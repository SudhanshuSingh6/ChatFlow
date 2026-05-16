package com.chatflow.presence.service;

import com.chatflow.message.service.ReplayService;
import com.chatflow.typing.service.TypingStateManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEventListener {

    private final PresenceService presenceService;
    private final TypingStateManager typingStateManager;
    private final ReplayService replayService;

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        UUID userId = extractUserId(event.getUser());
        if (userId == null) {
            log.warn("SessionConnectedEvent received with no Principal — skipping");
            return;
        }

        presenceService.userConnected(userId);
        replayService.replayForUser(userId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        UUID userId = extractUserId(event.getUser());
        if (userId == null) {
            log.warn("SessionDisconnectEvent received with no Principal — skipping");
            return;
        }

        presenceService.userDisconnected(userId);

        typingStateManager.clearAllForUser(userId);
    }

    private UUID extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        try {
            return UUID.fromString(principal.getName());
        } catch (IllegalArgumentException ex) {
            log.warn("Principal name '{}' is not a valid UUID", principal.getName());
            return null;
        }
    }
}