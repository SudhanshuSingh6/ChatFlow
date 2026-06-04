package com.chatflow.infra.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;

/**
 * Kafka transport: publish the event as JSON, keyed by {@code aggregateId} so all events
 * for one conversation/aggregate land on the same partition and keep their order.
 *
 * <p>The send is <b>synchronous</b> on purpose: this runs inside {@link OutboxProcessor}'s
 * {@code REQUIRES_NEW} transaction, so a broker failure must throw <em>before</em> the row
 * is marked {@code PUBLISHED}. The row then stays {@code PENDING} and the poller retries —
 * the outbox's at-least-once guarantee on the produce side.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "kafka")
public class KafkaOutboxPublisher implements OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;
    private final long sendTimeoutSeconds;

    public KafkaOutboxPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper,
                                @Value("${app.outbox.kafka.topic:chatflow.outbox.events}") String topic,
                                @Value("${app.outbox.kafka.send-timeout-seconds:10}") long sendTimeoutSeconds) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.sendTimeoutSeconds = sendTimeoutSeconds;
    }

    @Override
    public void publish(OutboxEvent event) {
        String json = objectMapper.writeValueAsString(OutboxEventMessage.from(event));
        String key = event.getAggregateId() != null ? event.getAggregateId().toString() : null;
        try {
            kafkaTemplate.send(topic, key, json).get(sendTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted publishing outbox event " + event.getId(), e);
        } catch (Exception e) {
            // Propagate so the surrounding transaction rolls back and the row stays PENDING.
            throw new IllegalStateException("Failed to publish outbox event " + event.getId() + " to Kafka", e);
        }
    }
}
