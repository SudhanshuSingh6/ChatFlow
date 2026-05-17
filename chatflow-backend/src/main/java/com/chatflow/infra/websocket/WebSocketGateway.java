package com.chatflow.infra.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketGateway {

    private final WebSocketSessionRegistry sessionRegistry;

    public void sendToUser(UUID userId, OutboundMessage message) {
        sessionRegistry.sendToUser(userId, message);
    }

    public void sendToUsers(Collection<UUID> userIds, OutboundMessage message) {
        userIds.forEach(userId -> sendToUser(userId, message));
    }

    public boolean isConnected(UUID userId) {
        return sessionRegistry.isConnected(userId);
    }
}