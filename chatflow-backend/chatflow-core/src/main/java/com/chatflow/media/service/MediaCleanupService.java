package com.chatflow.media.service;

import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.repository.MediaMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Phase 7 — media deletion with a transactional-outbox flavour.
 *
 * <p>The DB row is marked deleted inside the transaction; the (non-transactional)
 * storage object is purged only <em>after</em> commit. If that post-commit purge
 * fails or never runs (e.g. crash), the row stays {@code PENDING_DELETION} and the
 * scheduled {@link #retryPendingDeletions()} job purges it later — no orphaned files.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaCleanupService {

    private final MediaMessageRepository mediaMessageRepository;
    private final MediaAccessGuard accessGuard;
    private final MediaStoragePurger purger;

    /** Grace period before the retry job touches a row, to avoid racing the post-commit purge. */
    @Value("${app.media.cleanup.retry-after-seconds:120}")
    private long retryAfterSeconds;

    @Transactional
    public void deleteMedia(UUID callerId, UUID messageId) {
        MediaMessage message = mediaMessageRepository.findByIdAndDeletedFalse(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media message not found: " + messageId));
        accessGuard.requireDeleteAccess(callerId, message);

        // Within the transaction: logical delete + flag storage for purge.
        message.setDeleted(true);
        message.setStatus(MediaStatus.PENDING_DELETION);
        mediaMessageRepository.save(message);

        final UUID id = message.getId();
        // Storage is not transactional — purge only after the DB delete commits.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            safePurge(id);
                        }
                    });
        } else {
            safePurge(id);
        }
    }

    /** Periodically retries storage purge for rows stuck in PENDING_DELETION. */
    @Scheduled(fixedDelayString = "${app.media.cleanup.interval-ms:300000}")
    public void retryPendingDeletions() {
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(retryAfterSeconds);
        List<MediaMessage> pending = mediaMessageRepository.findPendingDeletions(cutoff);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying storage purge for {} pending media deletions", pending.size());
        for (MediaMessage message : pending) {
            safePurge(message.getId());
        }
    }

    private void safePurge(UUID id) {
        try {
            purger.purge(id);
        } catch (Exception ex) {
            // Leave the row PENDING_DELETION; the scheduled job will retry.
            log.warn("Storage purge failed for media {} (will retry): {}", id, ex.getMessage());
        }
    }
}
