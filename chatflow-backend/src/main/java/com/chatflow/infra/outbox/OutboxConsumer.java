package com.chatflow.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes outbox events from the broker and drives the same {@link OutboxDispatcher} the
 * in-process transport uses. Today this listener lives in the monolith — Phase 0 proves the
 * full produce → broker → consume path within one app. In Phase 1+ a split-out service runs
 * its own copy of this listener against the same topic; nothing else about the producers
 * changes.
 *
 * <p>Only active when {@code app.outbox.transport=kafka}; otherwise no listener container is
 * created, so the app never tries to reach a broker when Kafka isn't running.
 *
 * <p>Handlers must be idempotent: Kafka delivery is at-least-once and a failed dispatch is
 * redelivered by the container's default error handling.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "kafka")
public class OutboxConsumer {

    private final OutboxDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.outbox.kafka.topic:chatflow.outbox.events}",
            groupId = "${app.outbox.kafka.consumer-group:chatflow-outbox}")
    public void consume(String json) {
        OutboxEventMessage message = objectMapper.readValue(json, OutboxEventMessage.class);
        log.debug("Consuming outbox event {} ({})", message.id(), message.eventType());
        dispatcher.dispatch(message.toOutboxEvent());
    }
}
