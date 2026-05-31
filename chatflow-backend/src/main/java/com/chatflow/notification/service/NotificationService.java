package com.chatflow.notification.service;

import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.notification.dto.NotificationResponse;
import com.chatflow.notification.entity.Notification;
import com.chatflow.notification.event.NotificationCommand;
import com.chatflow.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists notifications and pushes them live. The write path is invoked by the
 * transactional outbox consumer ({@code infra.outbox.OutboxDispatcher}), which is
 * the single durable mechanism for turning domain events into notifications —
 * replacing the scattered {@code AfterCommit} fan-out the old stacks used.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final WebSocketGateway webSocketGateway;

    @Transactional
    public void createAndPush(NotificationCommand command) {
        for (UUID recipientId : command.recipientIds()) {
            if (recipientId.equals(command.actorId())) {
                continue; // never notify yourself
            }
            Notification saved = upsert(recipientId, command);
            push(recipientId, saved);
        }
    }

    private Notification upsert(UUID recipientId, NotificationCommand command) {
        if (command.coalesce() && command.referenceId() != null) {
            var existing = repository.findFirstByRecipientIdAndReferenceIdAndTypeAndReadFalse(
                    recipientId, command.referenceId(), command.type());
            if (existing.isPresent()) {
                Notification n = existing.get();
                n.coalesce(command.preview());
                return repository.save(n);
            }
        }

        Notification n = Notification.builder()
                .recipientId(recipientId)
                .actorId(command.actorId())
                .type(command.type())
                .referenceType(command.referenceType())
                .referenceId(command.referenceId())
                .preview(command.preview())
                .build();
        try {
            return repository.save(n);
        } catch (DataIntegrityViolationException e) {
            // A concurrent event coalesced first; retry the lookup once.
            return repository.findFirstByRecipientIdAndReferenceIdAndTypeAndReadFalse(
                            recipientId, command.referenceId(), command.type())
                    .map(found -> {
                        found.coalesce(command.preview());
                        return repository.save(found);
                    })
                    .orElseThrow(() -> e);
        }
    }

    private void push(UUID recipientId, Notification n) {
        webSocketGateway.sendToUser(recipientId,
                OutboundMessage.of(OutboundMessage.Type.NOTIFICATION,
                        NotificationResponse.from(n)));
    }

    // ---------- read path (called by the controller) ----------

    @Transactional(readOnly = true)
    public List<NotificationResponse> feed(UUID recipientId, Instant cursor, int limit) {
        return repository.findFeed(recipientId, cursor, PageRequest.of(0, limit))
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return repository.countByRecipientIdAndReadFalse(recipientId);
    }

    @Transactional
    public void markRead(UUID recipientId, UUID notificationId) {
        int updated = repository.markRead(notificationId, recipientId, Instant.now());
        if (updated > 0) {
            notifyReadState(recipientId);
        }
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        repository.markAllRead(recipientId, Instant.now());
        notifyReadState(recipientId);
    }

    @Transactional
    public void delete(UUID recipientId, UUID notificationId) {
        repository.deleteByIdAndRecipientId(notificationId, recipientId);
    }

    /** Push the new unread count so other devices update their badge live. */
    private void notifyReadState(UUID recipientId) {
        webSocketGateway.sendToUser(recipientId,
                OutboundMessage.of(OutboundMessage.Type.NOTIFICATION_READ,
                        unreadCount(recipientId)));
    }
}
