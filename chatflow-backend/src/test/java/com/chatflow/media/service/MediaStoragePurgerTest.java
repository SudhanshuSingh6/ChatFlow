package com.chatflow.media.service;

import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.media.storage.MediaStorageService;
import com.chatflow.media.storage.StorageException;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MediaStoragePurgerTest {

    private final MediaMessageRepository repo = mock(MediaMessageRepository.class);
    private final MediaStorageService storage = mock(MediaStorageService.class);
    private final MediaStoragePurger purger = new MediaStoragePurger(repo, storage);

    private MediaMessage pending(String thumbnailUrl) {
        return MediaMessage.builder()
                .id(UUID.randomUUID())
                .storageKey("image/2026/05/abc.jpg")
                .thumbnailUrl(thumbnailUrl)
                .status(MediaStatus.PENDING_DELETION)
                .build();
    }

    @Test
    void deletesObjectAndThumbnailThenMarksDeleted() {
        MediaMessage m = pending("http://host/media/thumbnails/image/2026/05/abc_thumb.jpg");
        when(repo.findById(m.getId())).thenReturn(Optional.of(m));

        purger.purge(m.getId());

        verify(storage).delete("image/2026/05/abc.jpg");
        verify(storage).delete("thumbnails/image/2026/05/abc_thumb.jpg");
        verify(repo).save(argThat(saved -> saved.getStatus() == MediaStatus.DELETED));
    }

    @Test
    void skipsThumbnailDeleteWhenNoThumbnail() {
        MediaMessage m = pending(null);
        when(repo.findById(m.getId())).thenReturn(Optional.of(m));

        purger.purge(m.getId());

        verify(storage).delete("image/2026/05/abc.jpg");
        verify(storage, never()).delete(startsWith("thumbnails/"));
        verify(repo).save(argThat(saved -> saved.getStatus() == MediaStatus.DELETED));
    }

    @Test
    void alreadyDeletedIsNoOp() {
        MediaMessage m = pending(null);
        m.setStatus(MediaStatus.DELETED);
        when(repo.findById(m.getId())).thenReturn(Optional.of(m));

        purger.purge(m.getId());

        verifyNoInteractions(storage);
        verify(repo, never()).save(any());
    }

    @Test
    void storageFailurePropagatesAndDoesNotMarkDeleted() {
        MediaMessage m = pending(null);
        when(repo.findById(m.getId())).thenReturn(Optional.of(m));
        doThrow(new StorageException("boom", null)).when(storage).delete(anyString());

        assertThatThrownBy(() -> purger.purge(m.getId()))
                .isInstanceOf(StorageException.class);
        verify(repo, never()).save(any());
    }
}
