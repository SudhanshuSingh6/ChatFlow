package com.chatflow.conversation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Inbound WebSocket payload: recipient confirms delivery up to {@code upToSeq}. */
@Data
public class DeliveryAckRequest {

    @NotNull(message = "conversationId is required")
    private UUID conversationId;

    @Min(value = 1, message = "upToSeq must be positive")
    private long upToSeq;
}
