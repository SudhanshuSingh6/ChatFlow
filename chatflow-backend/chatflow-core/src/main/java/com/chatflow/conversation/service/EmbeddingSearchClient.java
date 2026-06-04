package com.chatflow.conversation.service;

import com.chatflow.contracts.dto.EmbeddingSearchHit;
import com.chatflow.contracts.dto.EmbeddingSearchRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Calls ai-service for semantic (vector) search — ai owns the embedding store now. Wrapped in a
 * circuit breaker + time limiter: on timeout/failure or an open breaker it falls back to no vector
 * hits, so hybrid search degrades to keyword-only instead of hanging or erroring.
 */
@Slf4j
@Component
public class EmbeddingSearchClient {

    private final RestClient restClient;
    private final String internalToken;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public EmbeddingSearchClient(
            @Value("${app.ai.base-url:http://localhost:8081}") String aiBaseUrl,
            @Value("${app.internal.token:dev-internal-token}") String internalToken,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(aiBaseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public List<EmbeddingSearchHit> search(String query, List<UUID> conversationIds, int limit) {
        if (conversationIds.isEmpty()) {
            return List.of();
        }
        return circuitBreakerFactory.create("ai-search").run(
                () -> {
                    EmbeddingSearchHit[] hits = restClient.post()
                            .uri("/internal/embeddings/search")
                            .header("X-Internal-Token", internalToken)
                            .body(new EmbeddingSearchRequest(query, conversationIds, limit))
                            .retrieve()
                            .body(EmbeddingSearchHit[].class);
                    return hits == null ? List.<EmbeddingSearchHit>of() : Arrays.asList(hits);
                },
                throwable -> {
                    log.warn("Semantic search unavailable, falling back to keyword-only: {}",
                            throwable.getMessage());
                    return List.<EmbeddingSearchHit>of();
                });
    }
}
