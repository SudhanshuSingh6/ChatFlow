package com.chatflow.realtime.ws;

import com.chatflow.realtime.client.CommandRejectedException;
import com.chatflow.realtime.client.CoreCommandClient;
import com.chatflow.realtime.metrics.RealtimeMetrics;
import com.chatflow.realtime.security.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

/**
 * The WebSocket edge. Terminates sockets, forwards inbound commands to core
 * ({@link CoreCommandClient}), and reports connection lifecycle. {@code PING} is answered locally;
 * outbound frames arrive via the relay subscriber, not here. Frames are handled generically as
 * {@code {type, requestId, payload}} — no shared DTOs with core.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private final RealtimeSessionRegistry sessionRegistry;
    private final CoreCommandClient core;
    private final RealtimeMetrics metrics;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = extractUserId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (sessionRegistry.register(userId, session)) {
            core.connect(userId); // first session → core marks online + replays
        }
        log.debug("WS connected userId={} sessionId={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = extractUserId(session);
        if (userId == null) return;
        if (sessionRegistry.remove(userId, session)) {
            core.disconnect(userId); // last session → core marks offline + clears typing
        }
        log.debug("WS disconnected userId={} sessionId={} status={}", userId, session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        log.warn("WS transport error userId={}: {}", extractUserId(session), ex.getMessage());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID userId = extractUserId(session);
        if (userId == null) {
            send(session, errorFrame(null, "Unauthenticated"));
            return;
        }
        metrics.frameReceived();

        JsonNode frame;
        try {
            frame = objectMapper.readTree(message.getPayload());
        } catch (Exception ex) {
            send(session, errorFrame(null, "Malformed message — expected {type, requestId, payload}"));
            return;
        }
        String type = frame.path("type").asString(null);
        String requestId = frame.path("requestId").asString(null);
        JsonNode payload = frame.get("payload");
        if (type == null) {
            send(session, errorFrame(requestId, "Missing type field"));
            return;
        }

        if ("PING".equals(type)) {
            send(session, pongFrame(requestId));
            return;
        }
        try {
            core.inbound(userId, type, payload, requestId);
        } catch (CommandRejectedException ex) {
            send(session, errorFrame(requestId, ex.getMessage()));
        } catch (Exception ex) {
            log.error("Unexpected error forwarding command userId={} type={}", userId, type, ex);
            send(session, errorFrame(requestId, "Internal server error"));
        }
    }

    private UUID extractUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        return attr instanceof UUID uuid ? uuid : null;
    }

    private String pongFrame(String requestId) {
        return frame("PONG", requestId, objectMapper.createObjectNode());
    }

    private String errorFrame(String requestId, String messageText) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("message", messageText);
        return frame("ERROR", requestId, payload);
    }

    private String frame(String type, String requestId, JsonNode payload) {
        ObjectNode f = objectMapper.createObjectNode();
        f.put("type", type);
        if (requestId != null) {
            f.put("requestId", requestId);
        }
        f.set("payload", payload);
        return objectMapper.writeValueAsString(f);
    }

    private void send(WebSocketSession session, String json) {
        try {
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ex) {
            log.warn("Failed to send frame: {}", ex.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ex) {
            log.warn("Failed to close session: {}", ex.getMessage());
        }
    }
}
