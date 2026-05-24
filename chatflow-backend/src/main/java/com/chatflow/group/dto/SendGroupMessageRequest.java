package com.chatflow.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class SendGroupMessageRequest {

    @NotBlank
    @Size(max = 100)
    private String clientMessageId;

    @NotNull
    private UUID groupId;

    @NotBlank
    @Size(max = 4000)
    private String content;
}