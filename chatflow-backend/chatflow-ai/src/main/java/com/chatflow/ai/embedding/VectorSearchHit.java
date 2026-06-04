package com.chatflow.ai.embedding;

import java.util.UUID;

/**
 * One vector-search hit, fully self-contained from the denormalized store: the message id,
 * its sender/sequence/snippet, and the raw cosine {@code similarity} (0–1, higher = closer).
 * The RAG read-path (a later step) builds context and citations directly from these — no
 * call back to core required.
 */
public record VectorSearchHit(
        UUID messageId,
        UUID senderId,
        String senderName,
        long sequenceNumber,
        String contentSnippet,
        double similarity) {
}
