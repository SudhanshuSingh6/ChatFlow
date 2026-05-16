package com.chatflow.message.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class SeenResponse {

    private UUID conversationId;

    private Long lastSeenSequenceNumber;
}