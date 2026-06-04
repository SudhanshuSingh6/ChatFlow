package com.chatflow.presence.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@Profile("!prod")
public class InMemoryPresenceStore implements PresenceStore {

    private final Map<UUID, Instant> onlineUsers = new ConcurrentHashMap<>();

    @Override
    public void setOnline(UUID userId) {
        onlineUsers.put(userId, Instant.now());
        log.debug("Presence ONLINE userId={}", userId);
    }

    @Override
    public void setOffline(UUID userId) {
        onlineUsers.remove(userId);
        log.debug("Presence OFFLINE userId={}", userId);
    }

    @Override
    public boolean isOnline(UUID userId) {
        return onlineUsers.containsKey(userId);
    }

    @Override
    public Optional<Instant> getOnlineSince(UUID userId) {
        return Optional.ofNullable(onlineUsers.get(userId));
    }
}