package com.chatflow.media.service;

import com.chatflow.media.dto.MediaUrlResponse;
import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.media.storage.MediaStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MediaAccessServiceTest {

    private final MediaMessageRepository repo = mock(MediaMessageRepository.class);
    private final MediaAccessGuard guard = mock(MediaAccessGuard.class);
    private final MediaStorageService storage = mock(MediaStorageService.class);
    private final MediaAccessService service =
            new MediaAccessService(repo, guard, storage);

    {
        ReflectionTestUtils.setField(service, "ttlMinutes", 30L);
    }

    @Test
    void issuesPresignedUrlWithExpiryForParticipant() {
        UUID caller = UUID.randomUUID();
        MediaMessage m = MediaMessage.builder()
                .id(UUID.randomUUID()).storageKey("image/2026/05/x.jpg").build();
        when(repo.findByIdAndDeletedFalse(m.getId())).thenReturn(Optional.of(m));
        when(storage.presignedUrl(eq("image/2026/05/x.jpg"), any(Duration.class)))
                .thenReturn("http://host/x?sig=1");

        LocalDateTime before = LocalDateTime.now().plusMinutes(30);
        MediaUrlResponse resp = service.getSignedUrl(caller, m.getId());

        assertThat(resp.getUrl()).isEqualTo("http://host/x?sig=1");
        assertThat(resp.getExpiresAt()).isAfterOrEqualTo(before.minusSeconds(5));
        verify(guard).requireReadAccess(caller, m);
        verify(storage).presignedUrl("image/2026/05/x.jpg", Duration.ofMinutes(30));
    }

    @Test
    void deniedCallerGetsNoUrl() {
        UUID caller = UUID.randomUUID();
        MediaMessage m = MediaMessage.builder()
                .id(UUID.randomUUID()).storageKey("image/2026/05/x.jpg").build();
        when(repo.findByIdAndDeletedFalse(m.getId())).thenReturn(Optional.of(m));
        doThrow(new SecurityException("denied")).when(guard).requireReadAccess(caller, m);

        assertThatThrownBy(() -> service.getSignedUrl(caller, m.getId()))
                .isInstanceOf(SecurityException.class);
        verify(storage, never()).presignedUrl(any(), any());
    }

    @Test
    void missingMessageThrows() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndDeletedFalse(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSignedUrl(UUID.randomUUID(), id))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
