package com.chatflow.ai.embedding;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Embedding provider settings, bound from {@code app.ai.embedding.*}.
 *
 * <p>Provider-agnostic: any OpenAI-compatible {@code /embeddings} endpoint works by
 * pointing {@code base-url} at it (OpenAI, Voyage, a local Ollama/vLLM server, …).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai.embedding")
public class EmbeddingProperties {

    /** Base URL of an OpenAI-compatible embeddings API. */
    private String baseUrl = "https://api.openai.com/v1";

    /** Bearer token; leave blank for keyless local servers. */
    private String apiKey;

    /** Model id sent to the provider. */
    private String model = "text-embedding-3-small";

    /** Expected vector dimension (must match the {@code message_embeddings.embedding} column). */
    private int dimensions = 1536;
}
