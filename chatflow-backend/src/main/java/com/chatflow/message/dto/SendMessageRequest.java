package com.chatflow.message.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {

    private String clientMessageId;

    private UUID conversationId;

    private UUID receiverId;

    private String content;
}