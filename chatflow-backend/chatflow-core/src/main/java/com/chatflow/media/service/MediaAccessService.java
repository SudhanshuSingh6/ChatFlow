package com.chatflow.media.service;

import com.chatflow.media.dto.MediaUrlResponse;
import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.media.storage.MediaStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Phase 8 — issues time-limited access URLs for media objects.
 *
 * <p>The caller must be a participant of the conversation/group before a URL is
 * minted. For the {@code s3} profile this is a presigned GET URL against a
 * private bucket; the {@code local} profile returns its standard URL (Phase 8
 * local JWT signing is a follow-up). Either way, access is gated by the
 * participant check here — objects are never handed out blindly.
 */
@Service
@RequiredArgsConstructor
public class MediaAccessService {

    private final MediaMessageRepository mediaMessageRepository;
    private final MediaAccessGuard accessGuard;
    private final MediaStorageService mediaStorageService;

    @Value("${app.media.signed-url-ttl-minutes:60}")
    private long ttlMinutes;

    @Transactional(readOnly = true)
    public MediaUrlResponse getSignedUrl(UUID callerId, UUID messageId) {
        MediaMessage message = mediaMessageRepository.findByIdAndDeletedFalse(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media message not found: " + messageId));
        accessGuard.requireReadAccess(callerId, message);

        Duration ttl = Duration.ofMinutes(ttlMinutes);
        String url = mediaStorageService.presignedUrl(message.getStorageKey(), ttl);

        return MediaUrlResponse.builder()
                .url(url)
                .expiresAt(LocalDateTime.now().plus(ttl))
                .build();
    }
}
