package com.chatflow.ai.embedding;

/**
 * Provider-agnostic text embedding. Implementations talk to a concrete provider;
 * callers (embedding worker, semantic search query) depend only on this interface.
 */
public interface EmbeddingService {

    /** Embed a single piece of text into a vector + the metadata used to produce it. */
    EmbeddingResult embed(String text);
}
