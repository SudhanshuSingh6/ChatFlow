package com.chatflow.media.service;

import com.chatflow.group.repository.GroupMemberRepository;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.media.dto.MediaMessageResponse;
import com.chatflow.media.dto.MediaUploadRequest;
import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.entity.MessageType;
import com.chatflow.media.exception.MediaValidationException;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.media.storage.MediaStorageService;
import com.chatflow.media.storage.StoredMedia;
import com.chatflow.media.processing.MediaProcessingEvent;
import com.chatflow.media.validation.MediaValidator;
import org.springframework.context.ApplicationEventPublisher;
import com.chatflow.message.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaMessageService {

    private final MediaMessageRepository mediaMessageRepository;
    private final MediaValidator mediaValidator;
    private final MediaStorageService mediaStorageService;
    private final WebSocketGateway webSocketGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final ConversationRepository conversationRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Transactional
    public MediaMessageResponse upload(UUID senderId,
                                       MultipartFile file,
                                       MessageType messageType,
                                       MediaUploadRequest request) {
        validateTarget(request);
        requireSenderAccess(senderId, request);

        String detectedMime = mediaValidator.detectAndVerifyMimeType(file, messageType);
        mediaValidator.validate(file, messageType);

        String safeOriginalName = sanitiseOriginalFilename(file.getOriginalFilename());
        String storageKey       = buildStorageKey(messageType, detectedMime);

        // Persist with UPLOADING first — if storage fails the row is recoverable
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

        // Read bytes before storage — MultipartFile stream may not survive
        // the request thread boundary needed by the async thumbnail listener
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new com.chatflow.media.storage.StorageException("Could not read upload bytes", ex);
        }

        // Phase 3: write to storage
        StoredMedia stored = mediaStorageService.store(file, storageKey);

        // Update URL and mark READY (Phase 10 changes this to PROCESSING)
        message.setMediaUrl(stored.getPublicUrl());
        message.setStatus(MediaStatus.READY);
        message = mediaMessageRepository.save(message);

        log.debug("Media uploaded id={} storageKey={} url={}",
                message.getId(), storageKey, stored.getPublicUrl());

        MediaMessageResponse response = MediaMessageResponse.from(message);

        // Phase 4: WebSocket delivery
        deliverToParticipants(response, request);

        // Phase 6: trigger async thumbnail generation
        eventPublisher.publishEvent(new MediaProcessingEvent(
                this,
                message.getId(),
                storageKey,
                messageType,
                detectedMime,
                fileBytes));

        return response;
    }

    @Transactional(readOnly = true)
    public MediaMessageResponse getById(UUID callerId, UUID messageId) {
        MediaMessage message = mediaMessageRepository.findByIdAndDeletedFalse(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media message not found: " + messageId));
        requireReadAccess(callerId, message);
        return MediaMessageResponse.from(message);
    }

    private void deliverToParticipants(MediaMessageResponse response,
                                       MediaUploadRequest request) {
        OutboundMessage frame = OutboundMessage.of(
                OutboundMessage.Type.MEDIA_MESSAGE, response);

        if (request.getChatId() != null) {
            conversationRepository.findById(request.getChatId()).ifPresent(conv -> {
                // Push to the other participant; sender already has the HTTP response
                UUID otherId = conv.getParticipantOneId().equals(response.getSenderId())
                        ? conv.getParticipantTwoId()
                        : conv.getParticipantOneId();
                webSocketGateway.sendToUser(otherId, frame);
                log.debug("Delivered MEDIA_MESSAGE to receiverId={}", otherId);
            });
        } else if (request.getGroupId() != null) {
            List<UUID> memberIds = groupMemberRepository
                    .findUserIdsByGroupId(request.getGroupId())
                    .stream()
                    .filter(id -> !id.equals(response.getSenderId()))
                    .toList();
            webSocketGateway.sendToUsers(memberIds, frame);
            log.debug("Delivered MEDIA_MESSAGE to {} group members", memberIds.size());
        }
    }

    private void requireSenderAccess(UUID senderId, MediaUploadRequest request) {
        if (request.getChatId() != null) {
            conversationRepository.findById(request.getChatId())
                    .filter(c -> c.getParticipantOneId().equals(senderId)
                            || c.getParticipantTwoId().equals(senderId))
                    .orElseThrow(() -> new SecurityException(
                            "User " + senderId + " is not a participant in conversation "
                                    + request.getChatId()));
        } else {
            if (!groupMemberRepository.existsByGroupIdAndUserId(
                    request.getGroupId(), senderId)) {
                throw new SecurityException(
                        "User " + senderId + " is not a member of group "
                                + request.getGroupId());
            }
        }
    }

    private void requireReadAccess(UUID callerId, MediaMessage message) {
        if (message.getConversationId() != null) {
            conversationRepository.findById(message.getConversationId())
                    .filter(c -> c.getParticipantOneId().equals(callerId)
                            || c.getParticipantTwoId().equals(callerId))
                    .orElseThrow(() -> new SecurityException(
                            "Access denied to media message " + message.getId()));
        } else if (message.getGroupId() != null) {
            if (!groupMemberRepository.existsByGroupIdAndUserId(
                    message.getGroupId(), callerId)) {
                throw new SecurityException(
                        "Access denied to media message " + message.getId());
            }
        }
    }

    private void validateTarget(MediaUploadRequest request) {
        boolean hasChatId  = request.getChatId()  != null;
        boolean hasGroupId = request.getGroupId() != null;
        if (hasChatId == hasGroupId) {
            throw new MediaValidationException(
                    "Exactly one of chatId or groupId must be provided");
        }
    }

    private String buildStorageKey(MessageType type, String mimeType) {
        String ext  = mimeTypeToExtension(mimeType);
        LocalDate now = LocalDate.now();
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
}