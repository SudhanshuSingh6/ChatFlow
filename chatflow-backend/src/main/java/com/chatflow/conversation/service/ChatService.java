package com.chatflow.conversation.service;

import com.chatflow.conversation.dto.MessageResponse;
import com.chatflow.conversation.entity.Conversation;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.entity.MessageType;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.ConversationRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.infra.outbox.OutboxEventType;
import com.chatflow.infra.outbox.OutboxWriter;
import com.chatflow.infra.tx.AfterCommit;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.notification.entity.NotificationType;
import com.chatflow.notification.entity.ReferenceType;
import com.chatflow.notification.event.NotificationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sending messages into any conversation — DIRECT or GROUP use the identical path
 * (a group is just a conversation with more participants). Replaces the old
 * ChatService + GroupChatService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final int PREVIEW_MAX = 250;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final WebSocketGateway webSocketGateway;
    private final OutboxWriter outboxWriter;

    @Transactional
    public MessageResponse sendMessage(UUID senderId, UUID conversationId,
                                       String clientMessageId, String content, String requestId) {
        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));

        if (!participantRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            throw new SecurityException(
                    "User " + senderId + " is not a participant in " + conversationId);
        }

        // Idempotency: a resend of the same clientMessageId returns the original.
        Optional<Message> existing = messageRepository
                .findByConversationIdAndClientMessageId(conversationId, clientMessageId);
        if (existing.isPresent()) {
            Message message = existing.get();
            if (!senderId.equals(message.getSenderId())) {
                throw new SecurityException("clientMessageId belongs to another sender");
            }
            MessageResponse response = MessageResponse.from(message);
            webSocketGateway.sendToUser(senderId,
                    OutboundMessage.responseTo(OutboundMessage.Type.MESSAGE_ACK, requestId, response));
            return response;
        }

        long seq = messageRepository.nextSequenceNumber(conversationId);
        Message saved = messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .clientMessageId(clientMessageId)
                .type(MessageType.TEXT)
                .content(content)
                .sequenceNumber(seq)
                .build());

        conversation.touchLastMessage(preview(content), saved.getCreatedAt(), seq);

        // The sender has implicitly read their own message.
        participantRepository.advanceReadCursor(conversationId, senderId, seq);

        MessageResponse response = MessageResponse.from(saved);
        List<UUID> recipients = participantRepository.findUserIdsByConversationId(conversationId).stream()
                .filter(id -> !id.equals(senderId))
                .toList();

        // Durable, coalesced notification via the transactional outbox (same tx).
        if (!recipients.isEmpty()) {
            outboxWriter.writeNotification(OutboxEventType.MESSAGE_CREATED,
                    "conversation", conversationId,
                    new NotificationCommand(recipients, senderId, NotificationType.NEW_MESSAGE,
                            ReferenceType.CONVERSATION, conversationId, preview(content), true));
        }

        // Deliver only after commit, so a rollback never leaks a phantom message.
        AfterCommit.run(() -> {
            webSocketGateway.sendToUser(senderId,
                    OutboundMessage.responseTo(OutboundMessage.Type.MESSAGE_ACK, requestId, response));
            webSocketGateway.sendToUsers(recipients,
                    OutboundMessage.of(OutboundMessage.Type.MESSAGE, response));
        });

        log.debug("Saved message id={} seq={} conversation={} recipients={}",
                saved.getId(), seq, conversationId, recipients.size());
        return response;
    }

    private static String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_MAX ? content : content.substring(0, PREVIEW_MAX);
    }
}
