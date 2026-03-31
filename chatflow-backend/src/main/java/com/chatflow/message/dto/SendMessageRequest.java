package com.chatflow.message.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {

    @NotBlank
    private String clientMessageId;

    @NotNull
    private UUID conversationId;

    @NotNull
    private UUID receiverId;

    @NotBlank
    private String content;
}