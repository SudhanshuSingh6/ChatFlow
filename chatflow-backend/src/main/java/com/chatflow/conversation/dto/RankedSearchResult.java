package com.chatflow.conversation.dto;

/**
 * A hybrid-search hit. {@code rankScore} is the merged keyword+vector ranking (Reciprocal
 * Rank Fusion) and is the field clients sort/threshold on. {@code similarity} is the raw
 * cosine similarity when the message was a vector hit, else null (keyword-only match).
 */
public record RankedSearchResult(
        MessageSearchResult message,
        double rankScore,
        Double similarity
) {
}
