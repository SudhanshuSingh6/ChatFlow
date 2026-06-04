package com.chatflow.conversation.dto;

import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.entity.MessageType;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String clientMessageId,
        MessageType type,
        String content,
        long sequenceNumber,
        Instant createdAt,
        Instant editedAt,
        boolean deleted
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversationId(),
                m.getSenderId(),
                m.getClientMessageId(),
                m.getType(),
                m.getContent(),
                m.getSequenceNumber(),
                m.getCreatedAt(),
                m.getEditedAt(),
                m.isDeleted()
        );
    }
}
