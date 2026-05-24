package com.chatflow.infra.websocket;

import com.chatflow.config.MetricsConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import tools.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionRegistry {

    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    private final Map<String, Instant> lastActivityAt = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetrics() {
        Gauge.builder(MetricsConfig.WS_CONNECTIONS, sessions, map ->
                        map.values().stream()
                                .mapToLong(s -> s.stream().filter(WebSocketSession::isOpen).count())
                                .sum())
                .description("Number of active WebSocket sessions")
                .register(meterRegistry);
    }

    public boolean register(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions =
                sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());
        userSessions.removeIf(s -> !s.isOpen());
        boolean firstSession = userSessions.isEmpty();
        userSessions.add(session);
        lastActivityAt.put(session.getId(), Instant.now());
        log.debug("Registered session userId={} sessionId={} total={}",
                userId, session.getId(), userSessions.size());
        return firstSession;
    }

    public boolean remove(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return false;

        userSessions.remove(session);
        userSessions.removeIf(s -> !s.isOpen());
        lastActivityAt.remove(session.getId());

        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
            log.debug("Removed final session userId={}", userId);
            return true;
        }
        log.debug("Removed session userId={} remaining={}", userId, userSessions.size());
        return false;
    }

    public boolean isConnected(UUID userId) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        return userSessions != null && userSessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    public void recordActivity(String sessionId) {
        lastActivityAt.put(sessionId, Instant.now());
    }

    public Map<String, Instant> getLastActivityMap() {
        return lastActivityAt;
    }

    public Map<UUID, Set<WebSocketSession>> getSessions() {
        return sessions;
    }

    public void sendToUser(UUID userId, OutboundMessage message) {

        Set<WebSocketSession> userSessions = sessions.get(userId);

        if (userSessions == null || userSessions.isEmpty()) {
            log.debug("sendToUser no-op — userId={} offline", userId);
            return;
        }
        final String json;

        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to serialize outbound websocket message",
                    ex
            );
        }
        userSessions.removeIf(session -> !send(session, json));

        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
        }
    }

    public Map<UUID, WebSocketSession> getStaleSessionsOlderThan(Duration threshold) {
        Instant cutoff = Instant.now().minus(threshold);
        Map<UUID, WebSocketSession> stale = new java.util.HashMap<>();

        sessions.forEach((userId, userSessions) ->
                userSessions.stream()
                        .filter(WebSocketSession::isOpen)
                        .filter(s -> {
                            Instant last = lastActivityAt.get(s.getId());
                            return last != null && last.isBefore(cutoff);
                        })
                        .forEach(s -> stale.put(userId, s))
        );
        return stale;
    }

    public void sendPingToAll(OutboundMessage pingFrame, ObjectMapper mapper) {

        final String json;

        try {
            json = mapper.writeValueAsString(pingFrame);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Failed to serialize websocket ping frame",
                    ex
            );
        }
        sessions.values().forEach(userSessions ->
                userSessions.forEach(session -> send(session, json)));

        log.debug("Sent server PING to {} session sets", sessions.size());
    }

    private boolean send(WebSocketSession session, String json) {
        if (!session.isOpen()) return false;
        try {
            synchronized (session) {
                if (!session.isOpen()) return false;
                session.sendMessage(new TextMessage(json));
            }
            return true;
        } catch (IOException ex) {
            log.warn("Failed to send frame sessionId={}: {}", session.getId(), ex.getMessage());
            return false;
        }
    }
}