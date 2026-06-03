package com.chatflow.notification.event;

import com.chatflow.infra.outbox.OutboxEvent;
import com.chatflow.infra.outbox.OutboxEventHandler;
import com.chatflow.infra.outbox.OutboxEventType;
import com.chatflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Outbox handler for every event whose payload is a {@link NotificationCommand}: persists
 * and pushes the notification. Lives in the notification feature so {@code infra/outbox}
 * never depends on it (dependency points feature → infra).
 */
@Component
@RequiredArgsConstructor
public class NotificationOutboxHandler implements OutboxEventHandler {

    private static final Set<String> TYPES = Set.of(
            OutboxEventType.MESSAGE_CREATED,
            OutboxEventType.FRIEND_REQUESTED,
            OutboxEventType.FRIEND_REQUEST_ACCEPTED,
            OutboxEventType.GROUP_MEMBER_ADDED,
            OutboxEventType.GROUP_MEMBER_REMOVED,
            OutboxEventType.GROUP_ROLE_CHANGED,
            OutboxEventType.GROUP_OWNERSHIP_TRANSFERRED);

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String eventType) {
        return TYPES.contains(eventType);
    }

    @Override
    public void handle(OutboxEvent event) {
        notificationService.createAndPush(
                objectMapper.readValue(event.getPayload(), NotificationCommand.class));
    }
}
