package com.chatflow.conversation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Inbound WebSocket payload: recipient opened a conversation (mark all delivered). */
@Data
public class ConversationOpenRequest {

    @NotNull(message = "conversationId is required")
    private UUID conversationId;
}
