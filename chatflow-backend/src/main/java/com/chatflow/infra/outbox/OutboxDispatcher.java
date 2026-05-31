package com.chatflow.infra.outbox;

import com.chatflow.notification.event.NotificationCommand;
import com.chatflow.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * The single consumer of drained outbox events. Routes each event to its durable
 * side effect — replacing the scattered {@code AfterCommit} fan-out the old stacks
 * used. For v1 every event type carries a {@link NotificationCommand} payload and
 * results in persisted + pushed notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public void dispatch(OutboxEvent event) {
        switch (event.getEventType()) {
            case OutboxEventType.MESSAGE_CREATED,
                 OutboxEventType.FRIEND_REQUESTED,
                 OutboxEventType.FRIEND_REQUEST_ACCEPTED,
                 OutboxEventType.GROUP_MEMBER_ADDED,
                 OutboxEventType.GROUP_MEMBER_REMOVED,
                 OutboxEventType.GROUP_ROLE_CHANGED,
                 OutboxEventType.GROUP_OWNERSHIP_TRANSFERRED ->
                    notificationService.createAndPush(
                            objectMapper.readValue(event.getPayload(), NotificationCommand.class));
            default -> log.warn("No outbox handler for event type '{}' (id={})",
                    event.getEventType(), event.getId());
        }
    }
}
