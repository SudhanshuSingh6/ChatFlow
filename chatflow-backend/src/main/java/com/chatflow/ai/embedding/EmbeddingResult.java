package com.chatflow.ai.embedding;

/**
 * One embedding plus the metadata recorded alongside it in {@code message_embeddings},
 * so a future model swap / re-embedding can be reasoned about.
 *
 * @param vector     the embedding
 * @param model      the model that produced it
 * @param dimensions the vector length actually returned
 */
public record EmbeddingResult(float[] vector, String model, int dimensions) {
}
