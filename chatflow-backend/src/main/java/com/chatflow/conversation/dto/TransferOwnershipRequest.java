package com.chatflow.conversation.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TransferOwnershipRequest(
        @NotNull(message = "newOwnerId is required") UUID newOwnerId
) {
}
