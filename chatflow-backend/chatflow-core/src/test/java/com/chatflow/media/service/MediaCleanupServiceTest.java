package com.chatflow.media.service;

import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.media.storage.StorageException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MediaCleanupServiceTest {

    private final MediaMessageRepository repo = mock(MediaMessageRepository.class);
    private final MediaAccessGuard guard = mock(MediaAccessGuard.class);
    private final MediaStoragePurger purger = mock(MediaStoragePurger.class);
    private final MediaCleanupService service =
            new MediaCleanupService(repo, guard, purger);

    {
        ReflectionTestUtils.setField(service, "retryAfterSeconds", 120L);
    }

    @Test
    void deleteMarksRowAndPurges() {
        UUID caller = UUID.randomUUID();
        MediaMessage m = MediaMessage.builder()
                .id(UUID.randomUUID()).senderId(caller)
                .status(MediaStatus.READY).deleted(false).build();
        when(repo.findByIdAndDeletedFalse(m.getId())).thenReturn(Optional.of(m));

        // No active transaction in a plain unit test → purge runs inline.
        service.deleteMedia(caller, m.getId());

        ArgumentCaptor<MediaMessage> saved = ArgumentCaptor.forClass(MediaMessage.class);
        verify(repo).save(saved.capture());
        org.assertj.core.api.Assertions.assertThat(saved.getValue().isDeleted()).isTrue();
        org.assertj.core.api.Assertions.assertThat(saved.getValue().getStatus())
                .isEqualTo(MediaStatus.PENDING_DELETION);
        verify(purger).purge(m.getId());
    }

    @Test
    void unauthorisedCallerCannotDelete() {
        UUID caller = UUID.randomUUID();
        MediaMessage m = MediaMessage.builder()
                .id(UUID.randomUUID()).senderId(UUID.randomUUID()).build();
        when(repo.findByIdAndDeletedFalse(m.getId())).thenReturn(Optional.of(m));
        doThrow(new SecurityException("denied")).when(guard).requireDeleteAccess(caller, m);

        assertThatThrownBy(() -> service.deleteMedia(caller, m.getId()))
                .isInstanceOf(SecurityException.class);
        verify(repo, never()).save(any());
        verify(purger, never()).purge(any());
    }

    @Test
    void missingMessageThrows() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMedia(UUID.randomUUID(), id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retryJobPurgesEachPendingRow() {
        MediaMessage a = MediaMessage.builder().id(UUID.randomUUID()).build();
        MediaMessage b = MediaMessage.builder().id(UUID.randomUUID()).build();
        when(repo.findPendingDeletions(any(LocalDateTime.class))).thenReturn(List.of(a, b));

        service.retryPendingDeletions();

        verify(purger).purge(a.getId());
        verify(purger).purge(b.getId());
    }

    @Test
    void purgeFailureDoesNotPropagateFromDelete() {
        UUID caller = UUID.randomUUID();
        MediaMessage m = MediaMessage.builder()
                .id(UUID.randomUUID()).senderId(caller).deleted(false).build();
        when(repo.findByIdAndDeletedFalse(m.getId())).thenReturn(Optional.of(m));
        doThrow(new StorageException("boom")).when(purger).purge(m.getId());

        // The row is already committed-deleted; purge failure is swallowed so the
        // HTTP delete still succeeds and the scheduled job retries later.
        service.deleteMedia(caller, m.getId());

        verify(repo).save(any());
    }
}
