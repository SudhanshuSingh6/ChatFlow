package com.chatflow.realtime;

import com.chatflow.auth.security.JwtHandshakeInterceptor;
import com.chatflow.infra.websocket.InboundMessage;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketSessionRegistry;
import com.chatflow.conversation.service.ReplayService;
import com.chatflow.presence.service.PresenceService;
import com.chatflow.typing.service.TypingStateManager;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.UUID;

/**
 * Embedded WebSocket handler — active only when core terminates WebSockets itself
 * ({@code app.realtime.mode=embedded}, the default). When the realtime gateway is external this
 * bean (and {@link com.chatflow.config.WebSocketConfig}) are not created; the same inbound logic
 * is reached via {@link RealtimeInboundService} from {@link InternalRealtimeController}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.realtime.mode", havingValue = "embedded", matchIfMissing = true)
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final RealtimeInboundService inboundService;

    private final ReplayService replayService;
    private final PresenceService presenceService;
    private final TypingStateManager typingStateManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = extractUserId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        boolean firstSession = sessionRegistry.register(userId, session);
        if (firstSession) {
            presenceService.userConnected(userId);
        }
        replayService.replayForUser(userId);
        log.debug("WebSocket connected userId={} sessionId={}", userId, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = extractUserId(session);
        if (userId == null) return;
        boolean finalSession = sessionRegistry.remove(userId, session);
        if (finalSession) {
            presenceService.userDisconnected(userId);
            typingStateManager.clearAllForUser(userId);
        }
        log.debug("WebSocket disconnected userId={} sessionId={} status={}", userId, session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable ex) {
        UUID userId = extractUserId(session);
        log.warn("WebSocket transport error userId={}: {}", userId, ex.getMessage());
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID userId = extractUserId(session);
        if (userId == null) {
            sendError(session, null, "Unauthenticated");
            return;
        }

        InboundMessage inbound;
        try {
            inbound = objectMapper.readValue(message.getPayload(), InboundMessage.class);
        } catch (Exception ex) {
            log.warn("Malformed message from userId={}: {}", userId, ex.getMessage());
            sendError(session, null, "Malformed message — expected {type, requestId, payload}");
            return;
        }
        if (inbound.getType() == null) {
            sendError(session, inbound.getRequestId(), "Missing type field");
            return;
        }

        try {
            if (inbound.getType() == InboundMessage.Type.PING) {
                sendDirect(session, OutboundMessage.responseTo(
                        OutboundMessage.Type.PONG, inbound.getRequestId(), Map.of()));
                return;
            }
            inboundService.dispatch(userId, inbound.getType(), inbound.getPayload(), inbound.getRequestId());
        } catch (IllegalArgumentException | SecurityException ex) {
            log.warn("Client error userId={} type={}: {}", userId, inbound.getType(), ex.getMessage());
            sendError(session, inbound.getRequestId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Server error userId={} type={}", userId, inbound.getType(), ex);
            sendError(session, inbound.getRequestId(), "Internal server error");
        }
    }

    private UUID extractUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        return attr instanceof UUID uuid ? uuid : null;
    }

    private void sendError(WebSocketSession session, String requestId, String message) {
        sendDirect(session, OutboundMessage.error(requestId, message));
    }

    private void sendDirect(WebSocketSession session, OutboundMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ex) {
            log.warn("Failed to send websocket frame: {}", ex.getMessage());
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
