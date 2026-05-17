package com.chatflow.presence.service;

import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.message.entity.Conversation;
import com.chatflow.message.repository.ConversationRepository;
import com.chatflow.presence.dto.PresenceEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final WebSocketGateway webSocketGateway;

    public void userConnected(UUID userId) {
        presenceStore.setOnline(userId);
        Instant onlineSince = presenceStore.getOnlineSince(userId).orElse(Instant.now());
        broadcastToConversationPartners(userId, PresenceEvent.online(userId, onlineSince));
    }

    public void userDisconnected(UUID userId) {
        presenceStore.setOffline(userId);
        broadcastToConversationPartners(userId, PresenceEvent.offline(userId));
    }

    public boolean isOnline(UUID userId) {
        return presenceStore.isOnline(userId);
    }

    public Optional<Instant> getOnlineSince(UUID userId) {
        return presenceStore.getOnlineSince(userId);
    }

    private void broadcastToConversationPartners(UUID userId, PresenceEvent event) {
        List<Conversation> conversations = conversationRepository
                .findByParticipantOneIdOrParticipantTwoIdOrderByLastMessageAtDesc(userId, userId);

        conversations.forEach(conversation -> {
            UUID partnerId = conversation.getParticipantOneId().equals(userId)
                    ? conversation.getParticipantTwoId()
                    : conversation.getParticipantOneId();

            webSocketGateway.sendToUser(partnerId,
                    OutboundMessage.of(OutboundMessage.Type.PRESENCE, event));
        });

        log.debug("Presence {} sent to {} partners for userId={}",
                event.getStatus(), conversations.size(), userId);
    }
}