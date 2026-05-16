package com.chatflow.presence.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PresenceEvent {

    public enum Status { ONLINE, OFFLINE }

    private UUID userId;
    private Status status;
    private Instant onlineSince;

    public static PresenceEvent online(UUID userId, Instant onlineSince) {
        return PresenceEvent.builder()
                .userId(userId)
                .status(Status.ONLINE)
                .onlineSince(onlineSince)
                .build();
    }

    public static PresenceEvent offline(UUID userId) {
        return PresenceEvent.builder()
                .userId(userId)
                .status(Status.OFFLINE)
                .onlineSince(null)
                .build();
    }
}