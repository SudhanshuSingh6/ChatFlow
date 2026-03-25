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

        if (messageRepository.existsByClientMessageId(request.getClientMessageId())) {
            log.debug("Duplicate clientMessageId={} — returning existing message",
                    request.getClientMessageId());
            return messageRepository.findByClientMessageId(request.getClientMessageId())
                    .map(MessageResponse::from)
                    .orElseThrow(); // can't happen: existsBy returned true
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + request.getConversationId()));

        if (!isParticipant(conversation, senderId)) {
            throw new SecurityException("User " + senderId + " is not a participant in conversation "
                    + request.getConversationId());
        }

        Long seq = messageRepository.nextSequenceNumber(request.getConversationId());

        Message message = Message.builder()
                .clientMessageId(request.getClientMessageId())
                .conversationId(request.getConversationId())
                .senderId(senderId)
                .receiverId(request.getReceiverId())
                .content(request.getContent())
                .status(MessageStatus.SENT)
                .sequenceNumber(seq)
                .build();

        Message saved = messageRepository.save(message);
        log.debug("Saved message id={} seq={} conversation={}",
                saved.getId(), seq, request.getConversationId());

        conversation.setLastMessage(request.getContent());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        MessageResponse response = MessageResponse.from(saved);
        messagingTemplate.convertAndSendToUser(
                request.getReceiverId().toString(),
                "/queue/messages",
                response
        );
        log.debug("Delivered message to receiver={}", request.getReceiverId());
        return response;
    }

    private boolean isParticipant(Conversation conversation, UUID userId) {
        return userId.equals(conversation.getParticipantOneId())
                || userId.equals(conversation.getParticipantTwoId());
    }
}