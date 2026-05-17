package com.chatflow.infra.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketSessionRegistry {

    private final Map<UUID, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public boolean register(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions =
                sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet());

        userSessions.removeIf(s -> !s.isOpen());
        boolean firstSession = userSessions.isEmpty();

        userSessions.add(session);

        log.debug("Registered WebSocket session userId={} sessionId={} total={}",
                userId, session.getId(), userSessions.size());

        return firstSession;
    }

    public boolean remove(UUID userId, WebSocketSession session) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) {
            return false;
        }

        userSessions.remove(session);
        userSessions.removeIf(s -> !s.isOpen());

        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
            log.debug("Removed final WebSocket session userId={}", userId);
            return true;
        }

        log.debug("Removed WebSocket session userId={} remaining={}", userId, userSessions.size());
        return false;
    }

    public boolean isConnected(UUID userId) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        return userSessions != null && userSessions.stream().anyMatch(WebSocketSession::isOpen);
    }

    public void sendToUser(UUID userId, OutboundMessage message) {
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null || userSessions.isEmpty()) {
            log.debug("sendToUser no-op; userId={} offline", userId);
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(message);
        } catch (Exception ex) {
            log.warn("Failed to serialize outbound message type={}: {}",
                    message.getType(), ex.getMessage());
            return;
        }

        userSessions.forEach(session -> send(session, json));
        userSessions.removeIf(session -> !session.isOpen());

        if (userSessions.isEmpty()) {
            sessions.remove(userId, userSessions);
        }
    }

    private void send(WebSocketSession session, String json) {
        if (!session.isOpen()) {
            return;
        }

        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException ex) {
            log.warn("Failed to send WebSocket frame sessionId={}: {}",
                    session.getId(), ex.getMessage());
        }
    }
}