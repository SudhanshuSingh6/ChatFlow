package com.chatflow.ai.summary;

import com.chatflow.contracts.dto.ConversationTranscript;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

/**
 * Fetches a caller's unread transcript from core's internal endpoint. Synchronous because the
 * backlog is core-owned, large, and changes constantly (docs/microservices-migration.md §2).
 * Wrapped in a circuit breaker + time limiter; on failure the fallback throws so the summary
 * request surfaces a clear 5xx rather than a misleading "all caught up".
 */
@Slf4j
@Component
public class TranscriptClient {

    private final RestClient restClient;
    private final String internalToken;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public TranscriptClient(
            @Value("${app.core.base-url:http://localhost:8080}") String coreBaseUrl,
            @Value("${app.internal.token:dev-internal-token}") String internalToken,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(4));
        this.restClient = RestClient.builder().baseUrl(coreBaseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public ConversationTranscript fetchUnread(UUID conversationId, UUID userId) {
        return circuitBreakerFactory.create("core-transcript").run(
                () -> restClient.get()
                        .uri("/internal/conversations/{cid}/transcript/unread?userId={uid}", conversationId, userId)
                        .header("X-Internal-Token", internalToken)
                        .retrieve()
                        .body(ConversationTranscript.class),
                throwable -> {
                    log.warn("Transcript fetch failed for conversation {}: {}",
                            conversationId, throwable.getMessage());
                    throw new IllegalStateException("Summary temporarily unavailable", throwable);
                });
    }
}
