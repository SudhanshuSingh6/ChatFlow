package com.chatflow.infra.websocket;

import com.chatflow.config.JwtHandshakeInterceptor;
import com.chatflow.message.dto.AckRequest;
import com.chatflow.message.dto.ConversationOpenRequest;
import com.chatflow.message.dto.SeenRequest;
import com.chatflow.message.dto.SendMessageRequest;
import com.chatflow.message.service.ChatService;
import com.chatflow.message.service.DeliveryService;
import com.chatflow.message.service.ReplayService;
import com.chatflow.presence.service.PresenceService;
import com.chatflow.typing.dto.TypingEventRequest;
import com.chatflow.typing.service.TypingStateManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ChatService chatService;
    private final DeliveryService deliveryService;
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
        if (userId == null) {
            return;
        }

        boolean finalSessionClosed = sessionRegistry.remove(userId, session);
        if (finalSessionClosed) {
            presenceService.userDisconnected(userId);
            typingStateManager.clearAllForUser(userId);
        }

        log.debug("WebSocket disconnected userId={} sessionId={} status={}",
                userId, session.getId(), status);
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
            sendError(session, null, "UNAUTHENTICATED", "Unauthenticated WebSocket session");
            return;
        }

        InboundMessage inbound;
        try {
            inbound = objectMapper.readValue(message.getPayload(), InboundMessage.class);
        } catch (Exception ex) {
            log.warn("Malformed WebSocket frame userId={}: {}", userId, ex.getMessage());
            sendError(session, null, "MALFORMED_JSON",
                    "Malformed message; expected {type, requestId, payload}");
            return;
        }

        if (inbound.getType() == null) {
            sendError(session, inbound.getRequestId(), "MISSING_TYPE", "Missing type field");
            return;
        }

        try {
            dispatch(session, userId, inbound);
        } catch (PayloadValidationException ex) {
            sendError(session, inbound.getRequestId(), "VALIDATION_ERROR",
                    "Invalid payload", ex.getDetails());
        } catch (SecurityException ex) {
            log.warn("Forbidden WebSocket event userId={} type={}: {}",
                    userId, inbound.getType(), ex.getMessage());
            sendError(session, inbound.getRequestId(), "FORBIDDEN", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("Bad WebSocket event userId={} type={}: {}",
                    userId, inbound.getType(), ex.getMessage());
            sendError(session, inbound.getRequestId(), "BAD_REQUEST", ex.getMessage());
        } catch (Exception ex) {
            log.error("Server error userId={} type={}", userId, inbound.getType(), ex);
            sendError(session, inbound.getRequestId(), "INTERNAL_ERROR", "Internal server error");
        }
    }

    private void dispatch(WebSocketSession session, UUID userId, InboundMessage inbound) throws Exception {
        switch (inbound.getType()) {
            case SEND_MESSAGE -> {
                SendMessageRequest req = parseAndValidate(inbound, SendMessageRequest.class);
                chatService.sendMessage(userId, req, inbound.getRequestId());
            }
            case MESSAGE_ACK -> {
                AckRequest req = parseAndValidate(inbound, AckRequest.class);
                deliveryService.ack(userId, req);
            }
            case CONVERSATION_OPEN -> {
                ConversationOpenRequest req = parseAndValidate(inbound, ConversationOpenRequest.class);
                deliveryService.conversationOpen(userId, req);
            }
            case CONVERSATION_SEEN -> {
                SeenRequest req = parseAndValidate(inbound, SeenRequest.class);
                deliveryService.markSeen(userId, req);
            }
            case TYPING -> {
                TypingEventRequest req = parseAndValidate(inbound, TypingEventRequest.class);
                typingStateManager.handleTyping(req.getConversationId(), userId, req.getTyping());
            }
            case PING -> sendDirect(session,
                    OutboundMessage.responseTo(OutboundMessage.Type.PONG,
                            inbound.getRequestId(), Map.of()));
        }
    }

    private <T> T parseAndValidate(InboundMessage inbound, Class<T> type) throws Exception {
        if (inbound.getPayload() == null || inbound.getPayload().isNull()) {
            throw new PayloadValidationException(List.of("payload must not be null"));
        }

        T value = objectMapper.treeToValue(inbound.getPayload(), type);
        Set<ConstraintViolation<T>> violations = validator.validate(value);

        if (!violations.isEmpty()) {
            List<String> details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .toList();
            throw new PayloadValidationException(details);
        }

        return value;
    }

    private UUID extractUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        return attr instanceof UUID uuid ? uuid : null;
    }

    private void sendError(WebSocketSession session, String requestId, String code, String message) {
        sendDirect(session, OutboundMessage.error(requestId, code, message));
    }

    private void sendError(WebSocketSession session, String requestId,
                           String code, String message, List<String> details) {
        sendDirect(session, OutboundMessage.error(requestId, code, message, details));
    }

    private void sendDirect(WebSocketSession session, OutboundMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to send WebSocket frame: {}", ex.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception ex) {
            log.warn("Failed to close WebSocket session: {}", ex.getMessage());
        }
    }

    private static class PayloadValidationException extends RuntimeException {
        private final List<String> details;

        PayloadValidationException(List<String> details) {
            super("Invalid payload");
            this.details = details;
        }

        List<String> getDetails() {
            return details;
        }
    }
}