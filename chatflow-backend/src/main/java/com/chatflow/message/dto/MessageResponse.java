package com.chatflow.message.dto;

import com.chatflow.message.entity.Message;
import com.chatflow.message.entity.MessageStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MessageResponse {

    private UUID id;
    private String clientMessageId;
    private UUID conversationId;
    private UUID senderId;
    private UUID receiverId;
    private String content;
    private MessageStatus status;
    private Long sequenceNumber;
    private LocalDateTime createdAt;

    public static MessageResponse from(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .clientMessageId(message.getClientMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .receiverId(message.getReceiverId())
                .content(message.getContent())
                .status(message.getStatus())
                .sequenceNumber(message.getSequenceNumber())
                .createdAt(message.getCreatedAt())
                .build();
    }
}