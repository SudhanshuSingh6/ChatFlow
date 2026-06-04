package com.chatflow.conversation.service;

import com.chatflow.conversation.dto.MessageResponse;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Offline inbox: on (re)connect, re-push every message across all of a user's
 * conversations that is beyond their delivered watermark. One path for DM and
 * group, replacing the old ReplayService + GroupChatService.replayForUser.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

    private final MessageRepository messageRepository;
    private final WebSocketGateway webSocketGateway;

    @Transactional(readOnly = true)
    public void replayForUser(UUID userId) {
        List<MessageResponse> missed = messageRepository.findUndeliveredForUser(userId).stream()
                .map(MessageResponse::from)
                .toList();

        if (missed.isEmpty()) {
            log.debug("No undelivered messages to replay for user={}", userId);
            return;
        }

        missed.forEach(message -> webSocketGateway.sendToUser(userId,
                OutboundMessage.of(OutboundMessage.Type.MESSAGE, message)));
        log.debug("Replayed {} messages to user={}", missed.size(), userId);
    }
}
