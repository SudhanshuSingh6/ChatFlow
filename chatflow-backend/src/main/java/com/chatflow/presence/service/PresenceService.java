package com.chatflow.presence.service;

import com.chatflow.message.entity.Conversation;
import com.chatflow.message.repository.ConversationRepository;
import com.chatflow.presence.dto.PresenceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    private final PresenceStore presenceStore;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void userConnected(UUID userId) {
        presenceStore.setOnline(userId);

        Instant onlineSince = presenceStore.getOnlineSince(userId)
                .orElse(Instant.now());

        PresenceEvent event = PresenceEvent.online(userId, onlineSince);
        broadcastToConversations(userId, event);
    }

    public void userDisconnected(UUID userId) {
        presenceStore.setOffline(userId);
        broadcastToConversations(userId, PresenceEvent.offline(userId));
    }

    public boolean isOnline(UUID userId) {
        return presenceStore.isOnline(userId);
    }

    public Optional<Instant> getOnlineSince(UUID userId) {
        return presenceStore.getOnlineSince(userId);
    }

    private void broadcastToConversations(UUID userId, PresenceEvent event) {
        List<Conversation> conversations = conversationRepository
                .findByParticipantOneIdOrParticipantTwoIdOrderByLastMessageAtDesc(userId, userId);

        conversations.stream()
                .filter(c -> c.getLastMessageAt() != null)
                .forEach(c -> {
                    String topic = "/topic/presence." + c.getId();
                    messagingTemplate.convertAndSend(topic, event);
                    log.debug("Broadcast {} presence to topic={}", event.getStatus(), topic);
                });

        log.debug("Presence {} broadcast to {} conversations for userId={}",
                event.getStatus(), conversations.size(), userId);
    }
}