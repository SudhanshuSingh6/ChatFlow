package com.chatflow.conversation.search;

import java.util.UUID;

/**
 * Payload of a {@code MESSAGE_EMBEDDING_REQUESTED} outbox event. The worker re-reads
 * the message by id (single source of truth, handles later edits/deletes), so this is
 * mainly self-describing metadata for the row.
 */
public record EmbeddingRequested(UUID messageId) {
}
