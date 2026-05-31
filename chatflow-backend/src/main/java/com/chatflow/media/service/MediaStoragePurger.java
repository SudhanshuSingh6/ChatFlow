package com.chatflow.media.service;

import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.media.storage.MediaKeys;
import com.chatflow.media.storage.MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Phase 7 — purges the storage object (and thumbnail) for a media message that
 * has been marked {@code PENDING_DELETION}, then flips it to {@code DELETED}.
 *
 * <p>Separate bean so its {@code @Transactional} boundary applies when invoked
 * post-commit by {@link MediaCleanupService} and from the retry scheduler. If a
 * storage delete fails the {@link com.chatflow.media.storage.StorageException}
 * propagates, the transaction rolls back, and the row stays {@code PENDING_DELETION}
 * for the next retry — never marked DELETED while the object still exists.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaStoragePurger {

    private final MediaMessageRepository mediaMessageRepository;
    private final MediaStorageService mediaStorageService;

    @Transactional
    public void purge(UUID mediaMessageId) {
        MediaMessage message = mediaMessageRepository.findById(mediaMessageId).orElse(null);
        if (message == null || message.getStatus() == MediaStatus.DELETED) {
            return; // already purged or gone
        }

        mediaStorageService.delete(message.getStorageKey());
        if (message.getThumbnailUrl() != null) {
            mediaStorageService.delete(MediaKeys.thumbnailKey(message.getStorageKey()));
        }

        message.setStatus(MediaStatus.DELETED);
        mediaMessageRepository.save(message);
        log.debug("Purged storage for media {} key={}", mediaMessageId, message.getStorageKey());
    }
}
