package com.chatflow.message.dto;

import com.chatflow.message.entity.MessageStatus;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StatusUpdateResponse {

    private UUID messageId;
    private UUID conversationId;
    private MessageStatus status;
    private Long sequenceNumber;
}