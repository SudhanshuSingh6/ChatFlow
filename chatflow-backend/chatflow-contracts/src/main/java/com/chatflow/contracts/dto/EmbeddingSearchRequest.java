package com.chatflow.contracts.dto;

import java.util.List;
import java.util.UUID;

/**
 * core → ai request for a semantic (vector) search. core supplies the conversations the
 * caller may see (it owns membership); ai embeds the query and searches its store scoped to
 * them. Keeps authorization in core and the vector store in ai.
 *
 * @param query           the search text to embed
 * @param conversationIds the caller's conversations (the search scope)
 * @param limit           max hits to return
 */
public record EmbeddingSearchRequest(
        String query,
        List<UUID> conversationIds,
        int limit) {
}
