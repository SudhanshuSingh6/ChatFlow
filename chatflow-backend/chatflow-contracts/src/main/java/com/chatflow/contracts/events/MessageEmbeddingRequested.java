package com.chatflow.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by core-chat when a message should be embedded; consumed by ai-service.
 *
 * <p>This is <b>event-carried state transfer</b>: the payload carries everything ai-service
 * needs to embed the message and populate its denormalized snippet store, so ai-service never
 * reads core-chat's {@code messages} or {@code users} tables. That decoupling is the whole
 * point of the split — see {@code docs/microservices-migration.md} §4b.
 *
 * <p>Keep changes additive (append fields, never repurpose) so older consumers keep working.
 *
 * @param messageId      the message being embedded (vector primary key on the ai side)
 * @param conversationId scope for conversation-scoped vector search
 * @param senderId       author, or {@code null} for system messages
 * @param senderName     denormalized author name for RAG context (avoids a users-table read)
 * @param sequenceNumber per-conversation monotonic order, surfaced in RAG citations
 * @param content        the text to embed and snippet
 * @param messageType    e.g. {@code TEXT}; ai-service only embeds embeddable types
 * @param createdAt      when the message was created
 */
public record MessageEmbeddingRequested(
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        String senderName,
        long sequenceNumber,
        String content,
        String messageType,
        Instant createdAt) {

    /** Canonical routing key / Kafka topic discriminator both sides agree on. */
    public static final String TYPE = "message.embedding_requested";
}
