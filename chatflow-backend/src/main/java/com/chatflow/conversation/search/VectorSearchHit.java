package com.chatflow.conversation.search;

import java.util.UUID;

/**
 * One vector-search hit: the message and its raw cosine {@code similarity} (0–1,
 * higher = closer). The hybrid search service hydrates the message and folds this
 * into a merged {@code rankScore}.
 */
public record VectorSearchHit(UUID messageId, double similarity) {
}
