package com.chatflow.contracts.events;

import java.util.UUID;

/**
 * Emitted by core when a conversation's messages are physically purged (group cascade); consumed
 * by ai-service to evict the now-orphaned embeddings for that conversation. Keeps ai's vector
 * store from accumulating rows whose source messages no longer exist.
 *
 * @param conversationId the purged conversation
 */
public record ConversationDeleted(UUID conversationId) {

    public static final String TYPE = "conversation.deleted";
}
