package com.chatflow.media.service;

import com.chatflow.media.dto.MediaMessageResponse;
import com.chatflow.media.dto.MediaUploadRequest;
import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.entity.MessageType;
import com.chatflow.media.exception.MediaValidationException;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.media.validation.MediaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaMessageService {

    private final MediaMessageRepository mediaMessageRepository;
    private final MediaValidator mediaValidator;

    /**
     * Validates, persists metadata, and returns UPLOADING status.
     * Storage write (Phase 3) and WebSocket delivery (Phase 4) plug in here.
     *
     * Design: the method is intentionally left open for the storage service
     * to be injected in Phase 3. The DB record is created first so there is
     * always a row to update once the storage URL is known.
     */
    @Transactional
    public MediaMessageResponse upload(UUID senderId,
                                       MultipartFile file,
                                       MessageType messageType,
                                       MediaUploadRequest request) {
        validateTarget(request);

        // Phase 2: validate before any persistence
        String detectedMime = mediaValidator.detectAndVerifyMimeType(file, messageType);
        mediaValidator.validate(file, messageType);

        String safeOriginalName = sanitiseOriginalFilename(file.getOriginalFilename());
        String storageKey       = buildStorageKey(messageType, detectedMime);

        MediaMessage message = mediaMessageRepository.save(MediaMessage.builder()
                .senderId(senderId)
                .conversationId(request.getChatId())
                .groupId(request.getGroupId())
                .messageType(messageType)
                .status(MediaStatus.UPLOADING)
                .mimeType(detectedMime)
                .fileSize(file.getSize())
                .storageKey(storageKey)
                .originalFileName(safeOriginalName)
                .caption(request.getCaption())
                .build());

        log.debug("Persisted MediaMessage id={} storageKey={} status=UPLOADING",
                message.getId(), storageKey);

        // Phase 3: storage write happens here, then status → PROCESSING or READY
        // Phase 4: WebSocket delivery fires after storage confirms the URL

        return MediaMessageResponse.from(message);
    }

    @Transactional(readOnly = true)
    public MediaMessageResponse getById(UUID callerId, UUID messageId) {
        MediaMessage message = mediaMessageRepository.findByIdAndDeletedFalse(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media message not found: " + messageId));

        requireAccess(callerId, message);
        return MediaMessageResponse.from(message);
    }

    // --- helpers ---

    private void validateTarget(MediaUploadRequest request) {
        boolean hasChatId  = request.getChatId()  != null;
        boolean hasGroupId = request.getGroupId() != null;

        if (hasChatId == hasGroupId) {
            throw new MediaValidationException(
                    "Exactly one of chatId or groupId must be provided");
        }
    }

    /**
     * Generates a UUID-based storage key with the correct extension derived
     * from the detected MIME type — never from the original filename.
     * Format: {messageType}/{year}/{month}/{uuid}.{ext}
     */
    private String buildStorageKey(MessageType type, String mimeType) {
        String ext = mimeTypeToExtension(mimeType);
        java.time.LocalDate now = java.time.LocalDate.now();
        return String.format("%s/%d/%02d/%s%s",
                type.name().toLowerCase(),
                now.getYear(), now.getMonthValue(),
                UUID.randomUUID(), ext);
    }

    private String mimeTypeToExtension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg"       -> ".jpg";
            case "image/png"        -> ".png";
            case "image/gif"        -> ".gif";
            case "image/webp"       -> ".webp";
            case "video/mp4"        -> ".mp4";
            case "video/quicktime"  -> ".mov";
            case "video/webm"       -> ".webm";
            case "audio/mpeg"       -> ".mp3";
            case "audio/ogg"        -> ".ogg";
            case "audio/wav"        -> ".wav";
            case "audio/aac"        -> ".aac";
            case "application/pdf"  -> ".pdf";
            case "application/zip"  -> ".zip";
            default                 -> "";
        };
    }

    private String sanitiseOriginalFilename(String original) {
        if (original == null || original.isBlank()) return "unnamed";
        return Path.of(original).getFileName().toString()
                .replaceAll("[^a-zA-Z0-9._\\- ]", "_")
                .substring(0, Math.min(original.length(), 255));
    }

    private void requireAccess(UUID callerId, MediaMessage message) {
        if (!callerId.equals(message.getSenderId())) {
            // Phase 3+: also check conversation/group membership
            // For now sender-only access is enforced
            throw new SecurityException("Access denied to media message " + message.getId());
        }
    }
}