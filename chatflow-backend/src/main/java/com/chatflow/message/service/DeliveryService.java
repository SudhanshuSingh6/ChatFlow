package com.chatflow.message.service;

import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.message.dto.AckRequest;
import com.chatflow.message.dto.ConversationOpenRequest;
import com.chatflow.message.dto.SeenRequest;
import com.chatflow.message.dto.SeenResponse;
import com.chatflow.message.dto.StatusUpdateResponse;
import com.chatflow.message.entity.Conversation;
import com.chatflow.message.entity.Message;
import com.chatflow.message.entity.MessageStatus;
import com.chatflow.message.repository.ConversationRepository;
import com.chatflow.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final WebSocketGateway webSocketGateway;

    @Transactional
    public void ack(UUID receiverId, AckRequest request) {
        Message message = messageRepository.findById(request.getMessageId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Message not found: " + request.getMessageId()));

        if (!message.getReceiverId().equals(receiverId)) {
            throw new SecurityException("User " + receiverId
                    + " is not the receiver of message " + request.getMessageId());
        }

        int updated = messageRepository.updateStatus(
                message.getId(), MessageStatus.SENT, MessageStatus.DELIVERED, LocalDateTime.now());

        if (updated == 0) {
            log.debug("Message {} already past SENT; skipping DELIVERED push", message.getId());
            return;
        }

        pushStatusUpdate(message.getSenderId(), message, MessageStatus.DELIVERED);
    }

    @Transactional
    public void conversationOpen(UUID receiverId, ConversationOpenRequest request) {
        UUID conversationId = request.getConversationId();

        Conversation conversation = conversationRepository
                .findByIdForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        if (!isParticipant(conversation, receiverId)) {
            throw new SecurityException("User " + receiverId
                    + " is not a participant in conversation " + conversationId);
        }

        List<Message> sentMessages = messageRepository
                .findByConversationIdAndReceiverIdAndStatus(
                        conversationId, receiverId, MessageStatus.SENT);

        if (!sentMessages.isEmpty()) {
            int updated = messageRepository.bulkUpdateStatus(
                    conversationId, receiverId,
                    MessageStatus.SENT, MessageStatus.DELIVERED,
                    LocalDateTime.now());

            log.debug("Bulk-marked {} messages DELIVERED conversation={}", updated, conversationId);
            sentMessages.forEach(m -> pushStatusUpdate(m.getSenderId(), m, MessageStatus.DELIVERED));
        }

        conversation.clearUnreadFor(receiverId);
        conversationRepository.save(conversation);

        if (request.getUpToSequenceNumber() != null) {
            markSeenInternal(receiverId, conversationId, request.getUpToSequenceNumber());
        }
    }

    @Transactional
    public void markSeen(UUID receiverId, SeenRequest request) {
        UUID conversationId = request.getConversationId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        if (!isParticipant(conversation, receiverId)) {
            throw new SecurityException("User " + receiverId
                    + " is not a participant in conversation " + conversationId);
        }

        markSeenInternal(receiverId, conversationId, request.getUpToSequenceNumber());
    }

    private void markSeenInternal(UUID receiverId, UUID conversationId, long upTo) {
        List<UUID> senderIds = messageRepository.findSenderIdsByConversationAndReceiver(
                conversationId, receiverId, upTo);

        if (senderIds.isEmpty()) {
            log.debug("No DELIVERED messages up to seq={} conversation={}", upTo, conversationId);
            return;
        }

        int updated = messageRepository.bulkMarkSeen(
                conversationId, receiverId, upTo, LocalDateTime.now());

        if (updated == 0) {
            log.debug("No messages newly marked SEEN up to seq={} conversation={}", upTo, conversationId);
            return;
        }

        SeenResponse seenResponse = SeenResponse.builder()
                .conversationId(conversationId)
                .lastSeenSequenceNumber(upTo)
                .build();

        senderIds.forEach(senderId ->
                webSocketGateway.sendToUser(senderId,
                        OutboundMessage.of(OutboundMessage.Type.SEEN_UPDATE, seenResponse)));
    }

    void pushStatusUpdate(UUID recipientId, Message message, MessageStatus status) {
        StatusUpdateResponse update = StatusUpdateResponse.builder()
                .messageId(message.getId())
                .conversationId(message.getConversationId())
                .status(status)
                .sequenceNumber(message.getSequenceNumber())
                .build();

        webSocketGateway.sendToUser(recipientId,
                OutboundMessage.of(OutboundMessage.Type.STATUS_UPDATE, update));
    }

    private boolean isParticipant(Conversation conversation, UUID userId) {
        return userId.equals(conversation.getParticipantOneId())
                || userId.equals(conversation.getParticipantTwoId());
    }
}