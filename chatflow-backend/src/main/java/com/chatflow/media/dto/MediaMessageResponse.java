package com.chatflow.media.dto;

import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.entity.MessageType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class MediaMessageResponse {

    private UUID id;
    private UUID senderId;
    private UUID conversationId;
    private UUID groupId;
    private MessageType messageType;
    private MediaStatus status;
    private String mediaUrl;
    private String thumbnailUrl;
    private String mimeType;
    private Long fileSize;
    private String originalFileName;
    private String caption;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MediaMessageResponse from(MediaMessage m) {
        return MediaMessageResponse.builder()
                .id(m.getId())
                .senderId(m.getSenderId())
                .conversationId(m.getConversationId())
                .groupId(m.getGroupId())
                .messageType(m.getMessageType())
                .status(m.getStatus())
                .mediaUrl(m.getMediaUrl())
                .thumbnailUrl(m.getThumbnailUrl())
                .mimeType(m.getMimeType())
                .fileSize(m.getFileSize())
                .originalFileName(m.getOriginalFileName())
                .caption(m.getCaption())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}