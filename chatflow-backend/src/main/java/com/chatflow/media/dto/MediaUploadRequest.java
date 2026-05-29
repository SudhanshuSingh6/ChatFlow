package com.chatflow.media.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class MediaUploadRequest {

    private UUID chatId;
    private UUID groupId;

    @Size(max = 1000)
    private String caption;
}