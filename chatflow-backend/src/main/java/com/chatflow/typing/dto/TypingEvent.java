package com.chatflow.typing.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TypingEvent {

    private UUID conversationId;
    private UUID userId;
    private boolean typing;

    public static TypingEvent of(UUID conversationId, UUID userId, boolean typing) {
        return TypingEvent.builder()
                .conversationId(conversationId)
                .userId(userId)
                .typing(typing)
                .build();
    }
}