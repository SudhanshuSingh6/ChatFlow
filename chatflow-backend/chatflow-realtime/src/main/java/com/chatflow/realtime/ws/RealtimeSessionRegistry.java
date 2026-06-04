package com.chatflow.realtime.ws;

import com.chatflow.realtime.metrics.RealtimeMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-session-per-user registry for the realtime gateway. Unlike core's copy, outbound delivery
 * is {@link #sendRaw} — the frame is already-serialized JSON pulled off the relay bus, so it's
 * written to sockets verbatim (the gateway never deserializes frames). Registers the
 * {@code realtime.active.sessions} / {@code realtime.connected.users} gauges.
 */
@Slf4j
@Component
public class RealtimeSessionRegistry {

    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final RealtimeMetrics metrics;

    public RealtimeSessionRegistry(MeterRegistry meterRegistry, RealtimeMetrics metrics) {
        this.metrics = metrics;
        Gauge.builder("realtime.active.sessions", sessions, map -> map.values().stream()
                        .mapToLong(s -> s.stream().filter(WebSocketSession::isOpen).count()).sum())
                .description("Open WebSocket sessions").register(meterRegistry);
        Gauge.builder("realtime.connected.users", sessions, map -> (double) map.size())
                .description("Distinct users with >=1 socket").register(meterRegistry);
    }

    /** @return true if this is the user's first session (caller notifies core to mark online). */
    public boolean register(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet());
        userSessions.removeIf(s -> !s.isOpen());
        boolean first = userSessions.isEmpty();
        userSessions.add(session);
        return first;
    }

    /** @return true if that was the user's last session (caller notifies core to mark offline). */
    public boolean remove(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return false;
        userSessions.remove(session);
        userSessions.removeIf(s -> !s.isOpen());
        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
            return true;
        }
        return false;
    }

    /** Write an already-serialized frame to all of a user's open sockets. */
    public void sendRaw(UUID userId, String frameJson) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        userSessions.removeIf(session -> !send(session, frameJson));
        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
        }
    }

    private boolean send(WebSocketSession session, String json) {
        if (!session.isOpen()) return false;
        try {
            synchronized (session) {
                if (!session.isOpen()) return false;
                session.sendMessage(new TextMessage(json));
            }
            metrics.frameSent();
            return true;
        } catch (IOException ex) {
            log.warn("Failed to send frame sessionId={}: {}", session.getId(), ex.getMessage());
            return false;
        }
    }
}
