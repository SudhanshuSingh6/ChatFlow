package com.chatflow.media.dto;

import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.entity.MessageType;

import java.time.LocalDateTime;
import java.util.UUID;

public record MediaMessageResponse(
        UUID id,
        UUID messageId,
        UUID conversationId,
        UUID senderId,
        MessageType messageType,
        MediaStatus status,
        String mediaUrl,
        String thumbnailUrl,
        String mimeType,
        Long fileSize,
        String originalFileName,
        String caption,
        LocalDateTime createdAt
) {
    public static MediaMessageResponse from(MediaMessage m, UUID conversationId) {
        return new MediaMessageResponse(
                m.getId(),
                m.getMessageId(),
                conversationId,
                m.getSenderId(),
                m.getMessageType(),
                m.getStatus(),
                m.getMediaUrl(),
                m.getThumbnailUrl(),
                m.getMimeType(),
                m.getFileSize(),
                m.getOriginalFileName(),
                m.getCaption(),
                m.getCreatedAt()
        );
    }
}
