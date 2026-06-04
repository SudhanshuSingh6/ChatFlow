package com.chatflow.ai.embedding;

import com.chatflow.ai.idempotency.IdempotencyGuard;
import com.chatflow.contracts.events.ConversationDeleted;
import com.chatflow.contracts.events.MessageEmbeddingRequested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class EmbeddingEventConsumerTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final EmbeddingIngestService ingestService = mock(EmbeddingIngestService.class);
    private final IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
    private final EmbeddingEventConsumer consumer =
            new EmbeddingEventConsumer(mapper, ingestService, idempotencyGuard);

    {
        ReflectionTestUtils.setField(consumer, "consumerGroup", "chatflow-ai-embedding");
    }

    private String envelope(UUID id, String eventType, String payloadJson) {
        return mapper.writeValueAsString(Map.of(
                "id", id.toString(),
                "aggregateType", "message",
                "aggregateId", UUID.randomUUID().toString(),
                "eventType", eventType,
                "payload", payloadJson));
    }

    @Test
    void ingestsAndMarksProcessed() {
        UUID eventId = UUID.randomUUID();
        var event = new MessageEmbeddingRequested(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "bob",
                3L, "hi there", "TEXT", Instant.parse("2026-06-04T10:00:00Z"));
        when(idempotencyGuard.alreadyProcessed("chatflow-ai-embedding", eventId)).thenReturn(false);

        consumer.onOutboxEvent(envelope(eventId, MessageEmbeddingRequested.TYPE, mapper.writeValueAsString(event)));

        ArgumentCaptor<MessageEmbeddingRequested> captor =
                ArgumentCaptor.forClass(MessageEmbeddingRequested.class);
        verify(ingestService).ingest(captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo(event.messageId());
        assertThat(captor.getValue().createdAt()).isEqualTo(event.createdAt());
        verify(idempotencyGuard).markProcessed("chatflow-ai-embedding", eventId);
    }

    @Test
    void skipsDuplicateDeliveries() {
        UUID eventId = UUID.randomUUID();
        var event = new MessageEmbeddingRequested(
                UUID.randomUUID(), UUID.randomUUID(), null, null,
                1L, "x", "TEXT", Instant.parse("2026-06-04T10:00:00Z"));
        when(idempotencyGuard.alreadyProcessed("chatflow-ai-embedding", eventId)).thenReturn(true);

        consumer.onOutboxEvent(envelope(eventId, MessageEmbeddingRequested.TYPE, mapper.writeValueAsString(event)));

        verify(ingestService, never()).ingest(any());
        verify(idempotencyGuard, never()).markProcessed(eq("chatflow-ai-embedding"), any());
    }

    @Test
    void evictsEmbeddingsOnConversationDeleted() {
        UUID conversationId = UUID.randomUUID();
        String payload = mapper.writeValueAsString(new ConversationDeleted(conversationId));

        consumer.onOutboxEvent(envelope(UUID.randomUUID(), ConversationDeleted.TYPE, payload));

        verify(ingestService).evictConversation(conversationId);
        verify(ingestService, never()).ingest(any());
    }

    @Test
    void ignoresEventsOfOtherTypes() {
        consumer.onOutboxEvent(envelope(UUID.randomUUID(), "friend.requested", "{}"));
        verifyNoInteractions(ingestService, idempotencyGuard);
    }
}
