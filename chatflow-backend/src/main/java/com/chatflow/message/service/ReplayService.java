package com.chatflow.message.service;

import com.chatflow.message.dto.MessageResponse;
import com.chatflow.message.entity.Message;
import com.chatflow.message.entity.MessageStatus;
import com.chatflow.message.mapper.MessageMapper;
import com.chatflow.message.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public void replayForUser(UUID userId) {
        List<Message> missed = messageRepository
                .findByReceiverIdAndStatusOrderBySequenceNumberAsc(
                        userId, MessageStatus.SENT);

        if (missed.isEmpty()) {
            log.debug("No SENT messages to replay for userId={}", userId);
            return;
        }

        log.debug("Replaying {} SENT messages for userId={}", missed.size(), userId);

        missed.forEach(message -> {
            MessageResponse response = MessageMapper.toMessageResponse(message);
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/messages",
                    response
            );
            log.debug("Replayed messageId={} seq={} to userId={}",
                    message.getId(), message.getSequenceNumber(), userId);
        });
    }
}