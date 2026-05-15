package com.chatflow.message.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class ConversationOpenRequest {

    @NotNull
    private UUID conversationId;

    @Positive
    private Long upToSequenceNumber;
}