package com.chatflow.presence.service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PresenceStore {

    void setOnline(UUID userId);

    void setOffline(UUID userId);

    boolean isOnline(UUID userId);

    Optional<Instant> getOnlineSince(UUID userId);
}