package com.chatflow.ai.embedding;

import java.time.Instant;
import java.util.UUID;

/**
 * A full {@code message_embeddings} row to upsert: the vector plus the denormalized message
 * metadata ai-service keeps so it never has to read core's tables.
 */
public record MessageEmbeddingRow(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        String senderName,
        long sequenceNumber,
        String contentSnippet,
        float[] vector,
        String model,
        int dimensions,
        Instant messageCreatedAt,
        Instant embeddedAt) {
}
