package com.chatflow.infra.outbox;

import com.chatflow.notification.event.NotificationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Writes outbox rows inside the caller's transaction. Business services call this
 * alongside their direct WebSocket push: the push is best-effort low latency, the
 * outbox row is the durable, at-least-once record the poller drains after commit.
 */
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void write(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        repository.save(OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(objectMapper.writeValueAsString(payload))
                .build());
    }

    /** Convenience for the common case: an event whose payload is a notification command. */
    public void writeNotification(String eventType, String aggregateType,
                                  UUID aggregateId, NotificationCommand command) {
        write(aggregateType, aggregateId, eventType, command);
    }
}
