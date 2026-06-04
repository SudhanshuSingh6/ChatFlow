package com.chatflow.conversation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddParticipantRequest(
        @NotNull(message = "userId is required") UUID userId
) {
}
