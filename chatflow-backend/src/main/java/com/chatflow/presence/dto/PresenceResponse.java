package com.chatflow.presence.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class PresenceResponse {

    private UUID userId;
    private boolean online;

    private Instant onlineSince;

    public static PresenceResponse of(UUID userId, boolean online, Instant onlineSince) {
        return PresenceResponse.builder()
                .userId(userId)
                .online(online)
                .onlineSince(onlineSince)
                .build();
    }
}