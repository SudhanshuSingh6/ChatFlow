package com.chatflow.typing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TypingEventRequest {

    @NotNull
    private UUID conversationId;

    @NotNull
    private Boolean typing;
}