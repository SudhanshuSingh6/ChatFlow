package com.chatflow.infra.websocket;

import com.chatflow.config.JwtHandshakeInterceptor;
import com.chatflow.conversation.dto.ConversationOpenRequest;
import com.chatflow.conversation.dto.DeliveryAckRequest;
import com.chatflow.conversation.dto.MarkReadRequest;
import com.chatflow.conversation.dto.SendMessageRequest;
import com.chatflow.conversation.service.ChatService;
import com.chatflow.conversation.service.DeliveryService;
import com.chatflow.conversation.service.ReplayService;
import com.chatflow.presence.service.PresenceService;
import com.chatflow.typing.dto.TypingEventRequest;
import com.chatflow.typing.service.TypingStateManager;
import tools.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

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

    // Unified chat — one path for DIRECT and GROUP conversations.
    private final ChatService chatService;
    private final DeliveryService deliveryService;
    private final ReplayService replayService;

    // Shared
    private final PresenceService presenceService;
    private final TypingStateManager typingStateManager;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

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

        // Replay every undelivered message across all of the user's conversations.
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
            dispatch(session, userId, inbound);
        } catch (IllegalArgumentException | SecurityException ex) {
            log.warn("Client error userId={} type={}: {}", userId, inbound.getType(), ex.getMessage());
            sendError(session, inbound.getRequestId(), ex.getMessage());
        } catch (Exception ex) {
            log.error("Server error userId={} type={}", userId, inbound.getType(), ex);
            sendError(session, inbound.getRequestId(), "Internal server error");
        }
    }

    private void dispatch(WebSocketSession session, UUID userId, InboundMessage inbound)
            throws Exception {
        switch (inbound.getType()) {
            case SEND_MESSAGE -> {
                SendMessageRequest req = parseAndValidate(inbound, SendMessageRequest.class);
                chatService.sendMessage(userId, req.getConversationId(),
                        req.getClientMessageId(), req.getContent(), inbound.getRequestId());
            }
            case MESSAGE_DELIVERED -> {
                DeliveryAckRequest req = parseAndValidate(inbound, DeliveryAckRequest.class);
                deliveryService.markDelivered(userId, req.getConversationId(),
                        req.getUpToSeq(), inbound.getRequestId());
            }
            case CONVERSATION_OPEN -> {
                ConversationOpenRequest req = parseAndValidate(inbound, ConversationOpenRequest.class);
                deliveryService.conversationOpen(userId, req.getConversationId());
            }
            case MARK_READ -> {
                MarkReadRequest req = parseAndValidate(inbound, MarkReadRequest.class);
                deliveryService.markRead(userId, req.getConversationId(), req.getUpToSeq());
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
            throw new IllegalArgumentException("Missing payload");
        }
        T value = objectMapper.treeToValue(inbound.getPayload(), type);
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .findFirst().orElse("Invalid payload");
            throw new IllegalArgumentException(msg);
        }
        return value;
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
