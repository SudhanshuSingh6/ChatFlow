package com.chatflow.conversation.dto;

import com.chatflow.conversation.entity.ParticipantRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(
        @NotNull(message = "role is required") ParticipantRole role
) {
}
