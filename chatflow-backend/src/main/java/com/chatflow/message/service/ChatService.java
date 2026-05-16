package com.chatflow.message.service;

import com.chatflow.message.dto.MessageResponse;
import com.chatflow.message.dto.SendMessageRequest;
import com.chatflow.message.entity.Conversation;
import com.chatflow.message.entity.Message;
import com.chatflow.message.entity.MessageStatus;
import com.chatflow.message.repository.ConversationRepository;
import com.chatflow.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public MessageResponse sendMessage(UUID senderId, SendMessageRequest request) {

        Optional<Message> existing = messageRepository.findByClientMessageId(request.getClientMessageId());
        if (existing.isPresent()) {
            log.debug("Duplicate clientMessageId={} — returning existing message",
                    request.getClientMessageId());
            return existing
                    .filter(message -> message.getSenderId().equals(senderId))
                    .map(MessageResponse::from)
                    .orElseThrow(() -> new SecurityException(
                            "User " + senderId + " cannot access clientMessageId "
                                    + request.getClientMessageId()));
        }
        Conversation conversation = conversationRepository
                .findByIdForUpdate(request.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + request.getConversationId()));

        if (!isParticipant(conversation, senderId)) {
            throw new SecurityException("User " + senderId + " is not a participant in conversation "
                    + request.getConversationId());
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
        log.debug("Saved message id={} seq={} conversation={}",
                saved.getId(), seq, request.getConversationId());

        conversation.setLastMessage(request.getContent());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.incrementUnreadFor(senderId);
        conversationRepository.save(conversation);
        MessageResponse response = MessageResponse.from(saved);
        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/queue/messages",
                response
        );
        log.debug("Delivered message to receiver={}", receiverId);
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
