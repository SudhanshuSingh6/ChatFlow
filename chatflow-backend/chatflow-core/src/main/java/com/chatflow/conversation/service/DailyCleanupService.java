package com.chatflow.conversation.service;

import com.chatflow.contracts.events.ConversationDeleted;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.ConversationRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.infra.outbox.OutboxEventType;
import com.chatflow.infra.outbox.OutboxWriter;
import com.chatflow.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Daily retention cleanup. Soft-deleted rows are hidden immediately by the read
 * queries; this job physically removes them once they age past the retention window.
 *
 * <p>Three independent steps:
 * <ul>
 *   <li>{@link #purgeMessages()} — hard-deletes message tombstones.</li>
 *   <li>{@link #purgeNotifications()} — hard-deletes dismissed notifications.</li>
 *   <li>{@link #purgeGroups()} — for each soft-deleted GROUP, cascades the physical
 *       removal of its related notifications, messages, participants, and the row itself.</li>
 * </ul>
 *
 * <p>Each bulk purge and each per-group cascade runs in its own transaction (via
 * {@link TransactionTemplate}), so one failing group does not abort the rest.
 */
@Slf4j
@Service
public class DailyCleanupService {

    private final MessageRepository messageRepository;
    private final NotificationRepository notificationRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final OutboxWriter outboxWriter;
    private final TransactionTemplate tx;

    @Value("${app.cleanup.retention.messages-days:30}")
    private long messageRetentionDays;

    @Value("${app.cleanup.retention.notifications-days:30}")
    private long notificationRetentionDays;

    @Value("${app.cleanup.retention.groups-days:30}")
    private long groupRetentionDays;

    public DailyCleanupService(MessageRepository messageRepository,
                               NotificationRepository notificationRepository,
                               ConversationRepository conversationRepository,
                               ConversationParticipantRepository participantRepository,
                               OutboxWriter outboxWriter,
                               PlatformTransactionManager txManager) {
        this.messageRepository = messageRepository;
        this.notificationRepository = notificationRepository;
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.outboxWriter = outboxWriter;
        this.tx = new TransactionTemplate(txManager);
    }

    @Scheduled(fixedDelayString = "${app.cleanup.interval-ms:86400000}")
    public void dailyCleanup() {
        purgeMessages();
        purgeNotifications();
        purgeGroups();
    }

    void purgeMessages() {
        Instant cutoff = Instant.now().minus(messageRetentionDays, ChronoUnit.DAYS);
        // Embeddings now live in ai-service's own store (no FK from core); nothing to clear here.
        int purged = tx.execute(s -> messageRepository.purgeDeletedBefore(cutoff));
        if (purged > 0) {
            log.info("Purged {} soft-deleted messages older than {} days", purged, messageRetentionDays);
        }
    }

    void purgeNotifications() {
        Instant cutoff = Instant.now().minus(notificationRetentionDays, ChronoUnit.DAYS);
        int purged = tx.execute(s -> notificationRepository.purgeDeletedBefore(cutoff));
        if (purged > 0) {
            log.info("Purged {} soft-deleted notifications older than {} days",
                    purged, notificationRetentionDays);
        }
    }

    void purgeGroups() {
        Instant cutoff = Instant.now().minus(groupRetentionDays, ChronoUnit.DAYS);
        List<UUID> groupIds = conversationRepository.findGroupIdsToPurge(cutoff);
        if (groupIds.isEmpty()) {
            return;
        }
        int purged = 0;
        for (UUID conversationId : groupIds) {
            try {
                tx.executeWithoutResult(s -> cascadeDeleteGroup(conversationId));
                purged++;
            } catch (Exception ex) {
                // Skip this group and keep going; it will be retried on the next run.
                log.warn("Failed to purge soft-deleted group {} (will retry): {}",
                        conversationId, ex.getMessage());
            }
        }
        log.info("Purged {}/{} soft-deleted groups older than {} days",
                purged, groupIds.size(), groupRetentionDays);
    }

    /** Physical cascade for one soft-deleted group. Runs inside a transaction. */
    private void cascadeDeleteGroup(UUID conversationId) {
        notificationRepository.deleteByConversation(conversationId);
        messageRepository.deleteByConversationId(conversationId);
        participantRepository.deleteByConversationId(conversationId);
        conversationRepository.deleteById(conversationId);
        // Evict ai-service's embeddings for this conversation (same tx → drained to Kafka).
        outboxWriter.write("conversation", conversationId,
                OutboxEventType.CONVERSATION_DELETED, new ConversationDeleted(conversationId));
    }
}
