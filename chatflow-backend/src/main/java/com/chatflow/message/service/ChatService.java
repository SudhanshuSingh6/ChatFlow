package com.chatflow.message.service;

import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.message.dto.MessageResponse;
import com.chatflow.message.dto.SendMessageRequest;
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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final WebSocketGateway webSocketGateway;

    @Transactional
    public MessageResponse sendMessage(UUID senderId, SendMessageRequest request, String requestId) {
        Optional<Message> existing = messageRepository.findByClientMessageId(request.getClientMessageId());

        if (existing.isPresent()) {
            Message message = existing.get();
            if (!message.getSenderId().equals(senderId)) {
                throw new SecurityException("User " + senderId
                        + " cannot access clientMessageId " + request.getClientMessageId());
            }

            MessageResponse response = MessageResponse.from(message);
            webSocketGateway.sendToUser(senderId,
                    OutboundMessage.responseTo(OutboundMessage.Type.MESSAGE_ACK, requestId, response));
            return response;
        }

        Conversation conversation = conversationRepository
                .findByIdForUpdate(request.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + request.getConversationId()));

        if (!isParticipant(conversation, senderId)) {
            throw new SecurityException("User " + senderId
                    + " is not a participant in conversation " + request.getConversationId());
        }

        UUID receiverId = resolveReceiver(conversation, senderId);
        if (!receiverId.equals(request.getReceiverId())) {
            throw new SecurityException("Receiver " + request.getReceiverId()
                    + " is not the other participant in conversation " + request.getConversationId());
        }

        Long seq = messageRepository.nextSequenceNumber(request.getConversationId());

        Message message = Message.builder()
                .clientMessageId(request.getClientMessageId())
                .conversationId(request.getConversationId())
                .senderId(senderId)
                .receiverId(receiverId)
                .content(request.getContent())
                .status(MessageStatus.SENT)
                .sequenceNumber(seq)
                .build();

        Message saved = messageRepository.save(message);

        conversation.setLastMessage(request.getContent());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.incrementUnreadFor(senderId);
        conversationRepository.save(conversation);

        MessageResponse response = MessageResponse.from(saved);

        webSocketGateway.sendToUser(senderId,
                OutboundMessage.responseTo(OutboundMessage.Type.MESSAGE_ACK, requestId, response));

        webSocketGateway.sendToUser(receiverId,
                OutboundMessage.of(OutboundMessage.Type.MESSAGE, response));

        log.debug("Saved message id={} seq={} conversation={} receiver={}",
                saved.getId(), seq, request.getConversationId(), receiverId);

        return response;
    }

    private boolean isParticipant(Conversation conversation, UUID userId) {
        return userId.equals(conversation.getParticipantOneId())
                || userId.equals(conversation.getParticipantTwoId());
    }

    private UUID resolveReceiver(Conversation conversation, UUID senderId) {
        return senderId.equals(conversation.getParticipantOneId())
                ? conversation.getParticipantTwoId()
                : conversation.getParticipantOneId();
    }
}