package com.chatflow.media.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Phase 8 — a time-limited URL for retrieving a media object, plus its expiry.
 */
@Data
@Builder
public class MediaUrlResponse {

    private String url;
    private LocalDateTime expiresAt;
}
