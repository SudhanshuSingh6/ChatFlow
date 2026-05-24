package com.chatflow.infra.websocket;

import com.chatflow.infra.redis.CrossServerRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketGateway {

    private final WebSocketSessionRegistry sessionRegistry;
    private final CrossServerRelay crossServerRelay;
    public void sendToUser(UUID userId, OutboundMessage message) {
        if (sessionRegistry.isConnected(userId)) {
            sessionRegistry.sendToUser(userId, message);
        }
        crossServerRelay.publish(userId, message);
    }

    public void sendToUsers(Collection<UUID> userIds, OutboundMessage message) {
        userIds.forEach(userId -> sendToUser(userId, message));
    }

    public boolean isConnected(UUID userId) {
        return sessionRegistry.isConnected(userId);
    }
}