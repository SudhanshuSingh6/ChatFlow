package com.chatflow.media.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Multipart metadata for a media upload. A group is just a conversation now, so a
 * single {@code conversationId} replaces the old chatId/groupId XOR.
 */
public record MediaUploadRequest(
        @NotNull(message = "conversationId is required")
        UUID conversationId,

        @Size(max = 1000, message = "Caption cannot exceed 1000 characters")
        String caption
) {
    // Accessors used across the media service/tests.
    public UUID getConversationId() { return conversationId; }
    public String getCaption() { return caption; }
}
