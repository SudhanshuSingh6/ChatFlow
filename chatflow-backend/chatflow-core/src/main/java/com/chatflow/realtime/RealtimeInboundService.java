package com.chatflow.realtime;

import com.chatflow.conversation.dto.ConversationOpenRequest;
import com.chatflow.conversation.dto.DeliveryAckRequest;
import com.chatflow.conversation.dto.MarkReadRequest;
import com.chatflow.conversation.dto.SendMessageRequest;
import com.chatflow.conversation.service.ChatService;
import com.chatflow.conversation.service.DeliveryService;
import com.chatflow.infra.websocket.InboundMessage;
import com.chatflow.typing.dto.TypingEventRequest;
import com.chatflow.typing.service.TypingStateManager;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

/**
 * Single source of truth for handling an inbound realtime command. Called by both the embedded
 * {@link ChatWebSocketHandler} (when core terminates WebSockets) and the {@link InternalRealtimeController}
 * (when the realtime gateway forwards commands over REST). {@code PING} is an edge concern handled
 * by the socket layer and never reaches here.
 *
 * <p>Throws {@link IllegalArgumentException} (→ 400) / {@link SecurityException} (→ 403); the caller
 * turns those into an {@code ERROR} frame for the client.
 */
@Service
@RequiredArgsConstructor
public class RealtimeInboundService {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ChatService chatService;
    private final DeliveryService deliveryService;
    private final TypingStateManager typingStateManager;

    public void dispatch(UUID userId, InboundMessage.Type type, JsonNode payload, String requestId) {
        switch (type) {
            case SEND_MESSAGE -> {
                SendMessageRequest req = parseAndValidate(payload, SendMessageRequest.class);
                chatService.sendMessage(userId, req.getConversationId(),
                        req.getClientMessageId(), req.getContent(), requestId);
            }
            case MESSAGE_DELIVERED -> {
                DeliveryAckRequest req = parseAndValidate(payload, DeliveryAckRequest.class);
                deliveryService.markDelivered(userId, req.getConversationId(), req.getUpToSeq(), requestId);
            }
            case CONVERSATION_OPEN -> {
                ConversationOpenRequest req = parseAndValidate(payload, ConversationOpenRequest.class);
                deliveryService.conversationOpen(userId, req.getConversationId());
            }
            case MARK_READ -> {
                MarkReadRequest req = parseAndValidate(payload, MarkReadRequest.class);
                deliveryService.markRead(userId, req.getConversationId(), req.getUpToSeq());
            }
            case TYPING -> {
                TypingEventRequest req = parseAndValidate(payload, TypingEventRequest.class);
                typingStateManager.handleTyping(req.getConversationId(), userId, req.getTyping());
            }
            case PING -> throw new IllegalArgumentException("PING is handled at the socket edge");
        }
    }

    private <T> T parseAndValidate(JsonNode payload, Class<T> type) {
        if (payload == null || payload.isNull()) {
            throw new IllegalArgumentException("Missing payload");
        }
        T value = objectMapper.treeToValue(payload, type);
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .findFirst().orElse("Invalid payload");
            throw new IllegalArgumentException(msg);
        }
        return value;
    }
}
