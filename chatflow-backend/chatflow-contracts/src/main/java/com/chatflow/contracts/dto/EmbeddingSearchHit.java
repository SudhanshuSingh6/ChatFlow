package com.chatflow.contracts.dto;

import java.util.UUID;

/**
 * One semantic-search hit returned by ai to core: just the message id and cosine similarity
 * (0–1, higher = closer). core hydrates the message from its own store and merges with its
 * keyword results.
 */
public record EmbeddingSearchHit(UUID messageId, double similarity) {
}
