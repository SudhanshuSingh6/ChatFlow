package com.chatflow.presence.service;

import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
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
    private final ConversationParticipantRepository participantRepository;
    private final WebSocketGateway webSocketGateway;

    public void userConnected(UUID userId) {
        presenceStore.setOnline(userId);
        Instant onlineSince = presenceStore.getOnlineSince(userId).orElse(Instant.now());
        broadcastToContacts(userId, PresenceEvent.online(userId, onlineSince));
    }

    public void userDisconnected(UUID userId) {
        presenceStore.setOffline(userId);
        broadcastToContacts(userId, PresenceEvent.offline(userId));
    }

    public boolean isOnline(UUID userId) {
        return presenceStore.isOnline(userId);
    }

    public Optional<Instant> getOnlineSince(UUID userId) {
        return presenceStore.getOnlineSince(userId);
    }

    /** Notify everyone who shares a conversation (DIRECT or GROUP) with this user. */
    private void broadcastToContacts(UUID userId, PresenceEvent event) {
        List<UUID> contacts = participantRepository.findContactUserIds(userId);
        contacts.forEach(contactId -> webSocketGateway.sendToUser(contactId,
                OutboundMessage.of(OutboundMessage.Type.PRESENCE, event)));
        log.debug("Presence {} sent to {} contacts for userId={}",
                event.getStatus(), contacts.size(), userId);
    }
}
