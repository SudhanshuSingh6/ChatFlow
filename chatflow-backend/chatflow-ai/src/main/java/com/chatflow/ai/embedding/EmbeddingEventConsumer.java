package com.chatflow.ai.embedding;

import com.chatflow.ai.idempotency.IdempotencyGuard;
import com.chatflow.contracts.events.ConversationDeleted;
import com.chatflow.contracts.events.MessageEmbeddingRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Consumes core's shared outbox topic and ingests embedding requests. Uses its own consumer
 * group so it receives every event independently of core's own consumer, then filters to
 * {@link MessageEmbeddingRequested#TYPE} and re-parses the inner payload.
 *
 * <p>Delivery is at-least-once; {@link MessageEmbeddingRepository#upsert} is idempotent on
 * {@code message_id}, so redelivery just re-embeds the same row.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingEventConsumer {

    private final ObjectMapper objectMapper;
    private final EmbeddingIngestService ingestService;
    private final IdempotencyGuard idempotencyGuard;

    @Value("${app.outbox.consumer-group:chatflow-ai-embedding}")
    private String consumerGroup;

    @KafkaListener(
            topics = "${app.outbox.topic:chatflow.outbox.events}",
            groupId = "${app.outbox.consumer-group:chatflow-ai-embedding}")
    public void onOutboxEvent(String json) {
        OutboxEnvelope envelope = objectMapper.readValue(json, OutboxEnvelope.class);
        String type = envelope.eventType();

        if (MessageEmbeddingRequested.TYPE.equals(type)) {
            // Process-then-mark: the upsert is idempotent, so re-running on a crash between work and
            // mark is harmless — and we avoid holding a DB transaction across the slow embed call.
            if (idempotencyGuard.alreadyProcessed(consumerGroup, envelope.id())) {
                log.debug("Skipping already-processed embedding event {}", envelope.id());
                return;
            }
            MessageEmbeddingRequested event =
                    objectMapper.readValue(envelope.payload(), MessageEmbeddingRequested.class);
            ingestService.ingest(event);
            idempotencyGuard.markProcessed(consumerGroup, envelope.id());
        } else if (ConversationDeleted.TYPE.equals(type)) {
            // Orphan cleanup is idempotent (delete-by-conversation), so no dedup needed.
            ConversationDeleted event =
                    objectMapper.readValue(envelope.payload(), ConversationDeleted.class);
            ingestService.evictConversation(event.conversationId());
        }
        // else: not ours; another consumer/group handles it
    }
}
