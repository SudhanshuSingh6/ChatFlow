package com.chatflow.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

/**
 * Synchronous membership check against core. Participant data is core-owned and must be
 * <em>correct</em> (not eventually-consistent), so this is a sync call (docs/microservices-migration.md
 * §2/§4b). Wrapped in a circuit breaker + time limiter and <b>fails closed</b>: any
 * timeout/failure/open-breaker is treated as "not authorized".
 */
@Slf4j
@Component
public class ConversationAccessClient {

    private final RestClient restClient;
    private final String internalToken;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public ConversationAccessClient(
            @Value("${app.core.base-url:http://localhost:8080}") String coreBaseUrl,
            @Value("${app.internal.token:dev-internal-token}") String internalToken,
            CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(coreBaseUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public boolean isParticipant(UUID conversationId, UUID userId) {
        return circuitBreakerFactory.create("core-access").run(
                () -> {
                    Boolean member = restClient.get()
                            .uri("/internal/conversations/{cid}/participants/{uid}", conversationId, userId)
                            .header("X-Internal-Token", internalToken)
                            .retrieve()
                            .body(Boolean.class);
                    return Boolean.TRUE.equals(member);
                },
                throwable -> {
                    log.warn("Membership check failed for conversation {} user {}: {}",
                            conversationId, userId, throwable.getMessage());
                    return false; // fail closed
                });
    }
}
