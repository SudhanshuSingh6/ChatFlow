package com.chatflow.ai.embedding;

import com.chatflow.contracts.events.MessageEmbeddingRequested;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Turns a {@link MessageEmbeddingRequested} event into a stored embedding: embeds the carried
 * text and upserts the vector together with a denormalized snippet + metadata. Everything it
 * needs comes from the event payload — it never reads core-chat's database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingIngestService {

    /** Messages are <= 4000 chars; keep the whole text as the snippet for RAG context. */
    private static final int SNIPPET_MAX = 4000;
    private static final String TEXT = "TEXT";

    private final EmbeddingService embeddingService;
    private final MessageEmbeddingRepository repository;

    public void ingest(MessageEmbeddingRequested event) {
        if (!TEXT.equals(event.messageType())) {
            return; // only text is embeddable (media/system carry no embeddable content)
        }
        String content = event.content();
        if (content == null || content.isBlank()) {
            return;
        }

        EmbeddingResult result = embeddingService.embed(content);
        repository.upsert(new MessageEmbeddingRow(
                event.messageId(),
                event.conversationId(),
                event.senderId(),
                event.senderName(),
                event.sequenceNumber(),
                snippet(content),
                result.vector(),
                result.model(),
                result.dimensions(),
                event.createdAt(),
                Instant.now()));
        log.debug("Embedded message {} in conversation {} ({} dims, model {})",
                event.messageId(), event.conversationId(), result.dimensions(), result.model());
    }

    /** Evict all embeddings for a conversation core has purged (orphan cleanup). */
    public void evictConversation(UUID conversationId) {
        int deleted = repository.deleteByConversationId(conversationId);
        log.debug("Evicted {} embeddings for deleted conversation {}", deleted, conversationId);
    }

    private static String snippet(String content) {
        return content.length() <= SNIPPET_MAX ? content : content.substring(0, SNIPPET_MAX);
    }
}
