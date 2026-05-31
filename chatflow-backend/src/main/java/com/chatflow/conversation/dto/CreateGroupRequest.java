package com.chatflow.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateGroupRequest(
        @NotBlank(message = "Group name is required")
        @Size(max = 100, message = "Group name cannot exceed 100 characters")
        String name,

        @NotNull(message = "memberIds is required (may be empty)")
        List<UUID> memberIds
) {
}
