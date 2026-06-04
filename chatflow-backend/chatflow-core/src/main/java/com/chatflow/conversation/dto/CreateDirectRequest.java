package com.chatflow.conversation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body for opening (or fetching) a 1:1 conversation with another user. */
public record CreateDirectRequest(
        @NotNull(message = "userId is required") UUID userId
) {
}
