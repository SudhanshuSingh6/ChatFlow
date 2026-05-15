package com.chatflow.message.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AckRequest {

    @NotNull
    private UUID messageId;
}