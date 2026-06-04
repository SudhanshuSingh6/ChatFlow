package com.chatflow.realtime.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Forwards realtime events to core's {@code /internal/realtime/*} endpoints (shared internal token).
 * Bounded by RestClient timeouts + a circuit breaker. Inbound validation failures surface as
 * {@link CommandRejectedException} (→ ERROR frame) and are ignored by the breaker; backend
 * unavailability also surfaces as {@link CommandRejectedException} with a generic message.
 * Connection lifecycle is best-effort (failures logged, not propagated to the socket).
 */
@Slf4j
@Component
public class CoreCommandClient {

    private final RestClient restClient;
    private final String internalToken;
    private final ObjectMapper objectMapper;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public CoreCommandClient(@Value("${app.core.base-url:http://localhost:8080}") String coreBaseUrl,
                             @Value("${app.internal.token:dev-internal-token}") String internalToken,
                             ObjectMapper objectMapper,
                             CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(2));
        rf.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = RestClient.builder().baseUrl(coreBaseUrl).requestFactory(rf).build();
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public void connect(UUID userId) {
        lifecycle("/internal/realtime/connect", userId);
    }

    public void disconnect(UUID userId) {
        lifecycle("/internal/realtime/disconnect", userId);
    }

    private void lifecycle(String uri, UUID userId) {
        circuitBreakerFactory.create("core-lifecycle").run(
                () -> {
                    post(uri, Map.of("userId", userId));
                    return null;
                },
                throwable -> {
                    log.warn("core lifecycle {} for user {} failed: {}", uri, userId, throwable.getMessage());
                    return null;
                });
    }

    /** Forward an inbound command; throws {@link CommandRejectedException} the caller turns into ERROR. */
    public void inbound(UUID userId, String type, JsonNode payload, String requestId) {
        circuitBreakerFactory.create("core-inbound").run(
                () -> {
                    postInbound(Map.of(
                            "userId", userId, "type", type,
                            "payload", payload, "requestId", requestId == null ? "" : requestId));
                    return null;
                },
                throwable -> {
                    if (throwable instanceof CommandRejectedException rejected) {
                        throw rejected; // surface the client-facing validation message
                    }
                    throw new CommandRejectedException("Realtime backend temporarily unavailable");
                });
    }

    private void post(String uri, Object body) {
        restClient.post().uri(uri)
                .header("X-Internal-Token", internalToken)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private void postInbound(Object body) {
        restClient.post().uri("/internal/realtime/inbound")
                .header("X-Internal-Token", internalToken)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                    throw new CommandRejectedException(extractMessage(resp.getBody()));
                })
                .toBodilessEntity();
    }

    private String extractMessage(java.io.InputStream body) {
        try {
            JsonNode problem = objectMapper.readTree(body);
            String detail = problem.path("detail").asString(null);
            return (detail == null || detail.isBlank()) ? "Invalid request" : detail;
        } catch (Exception ex) {
            return "Invalid request";
        }
    }
}
