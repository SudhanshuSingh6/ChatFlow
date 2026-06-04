package com.chatflow.ai.embedding;

import com.chatflow.contracts.events.MessageEmbeddingRequested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmbeddingIngestServiceTest {

    @Mock EmbeddingService embeddingService;
    @Mock MessageEmbeddingRepository repository;
    @InjectMocks EmbeddingIngestService service;

    private static MessageEmbeddingRequested event(String type, String content) {
        return new MessageEmbeddingRequested(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "alice",
                7L, content, type, Instant.parse("2026-06-04T00:00:00Z"));
    }

    @Test
    void embedsTextAndUpsertsDenormalizedRow() {
        var e = event("TEXT", "hello world");
        when(embeddingService.embed("hello world"))
                .thenReturn(new EmbeddingResult(new float[]{0.1f, 0.2f}, "model-x", 2));

        service.ingest(e);

        ArgumentCaptor<MessageEmbeddingRow> row = ArgumentCaptor.forClass(MessageEmbeddingRow.class);
        verify(repository).upsert(row.capture());
        MessageEmbeddingRow r = row.getValue();
        assertThat(r.messageId()).isEqualTo(e.messageId());
        assertThat(r.conversationId()).isEqualTo(e.conversationId());
        assertThat(r.senderName()).isEqualTo("alice");
        assertThat(r.sequenceNumber()).isEqualTo(7L);
        assertThat(r.contentSnippet()).isEqualTo("hello world");
        assertThat(r.model()).isEqualTo("model-x");
        assertThat(r.dimensions()).isEqualTo(2);
        assertThat(r.messageCreatedAt()).isEqualTo(e.createdAt());
    }

    @Test
    void skipsNonTextMessages() {
        service.ingest(event("IMAGE", "caption"));
        verifyNoInteractions(embeddingService, repository);
    }

    @Test
    void skipsBlankContent() {
        service.ingest(event("TEXT", "   "));
        verifyNoInteractions(embeddingService, repository);
    }
}
