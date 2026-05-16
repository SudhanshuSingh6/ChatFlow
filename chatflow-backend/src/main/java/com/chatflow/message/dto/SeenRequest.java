package com.chatflow.message.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class SeenRequest {

    @NotNull
    private UUID conversationId;

    @NotNull
    @Positive
    private Long upToSequenceNumber;
}