package com.chatflow.conversation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/** Inbound WebSocket payload to send a text message into any conversation. */
@Data
public class SendMessageRequest {

    @NotNull(message = "conversationId is required")
    private UUID conversationId;

    @NotBlank(message = "clientMessageId is required")
    @Size(max = 100, message = "clientMessageId cannot exceed 100 characters")
    private String clientMessageId;

    @NotBlank(message = "content is required")
    @Size(max = 4000, message = "content cannot exceed 4000 characters")
    private String content;
}
