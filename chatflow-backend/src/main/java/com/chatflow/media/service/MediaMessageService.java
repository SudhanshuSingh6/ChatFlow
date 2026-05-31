package com.chatflow.media.service;

import com.chatflow.conversation.entity.Conversation;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.ConversationRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.infra.outbox.OutboxEventType;
import com.chatflow.infra.outbox.OutboxWriter;
import com.chatflow.infra.tx.AfterCommit;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.media.dto.MediaMessageResponse;
import com.chatflow.media.dto.MediaUploadRequest;
import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.entity.MessageType;
import com.chatflow.media.processing.MediaProcessingEvent;
import com.chatflow.media.repository.MediaMessageRepository;
import com.chatflow.media.storage.MediaStorageService;
import com.chatflow.media.storage.StoredMedia;
import com.chatflow.media.validation.MediaValidator;
import com.chatflow.notification.entity.NotificationType;
import com.chatflow.notification.entity.ReferenceType;
import com.chatflow.notification.event.NotificationCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Uploads a media attachment as a unified message: a {@code messages} row of
 * {@code type=MEDIA} plus a {@link MediaMessage} detail row linked by message id.
 * Works identically for DIRECT and GROUP conversations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaMessageService {

    private static final int PREVIEW_MAX = 250;

    private final MediaMessageRepository mediaMessageRepository;
    private final MediaValidator mediaValidator;
    private final MediaStorageService mediaStorageService;
    private final WebSocketGateway webSocketGateway;
    private final ApplicationEventPublisher eventPublisher;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final OutboxWriter outboxWriter;

    @Transactional
    public MediaMessageResponse upload(UUID senderId,
                                       MultipartFile file,
                                       MessageType messageType,
                                       MediaUploadRequest request) {
        UUID conversationId = request.getConversationId();
        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, senderId)) {
            throw new SecurityException(
                    "User " + senderId + " is not a participant in " + conversationId);
        }

        String detectedMime = mediaValidator.detectAndVerifyMimeType(file, messageType);
        mediaValidator.validate(file, messageType);

        String safeOriginalName = sanitiseOriginalFilename(file.getOriginalFilename());
        String storageKey       = buildStorageKey(messageType, detectedMime);

        // Parent message row (type=MEDIA), allocating the conversation sequence.
        long seq = messageRepository.nextSequenceNumber(conversationId);
        Message parent = messageRepository.save(Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .type(com.chatflow.conversation.entity.MessageType.MEDIA)
                .content(request.getCaption())
                .sequenceNumber(seq)
                .build());

        // Detail row — persist with UPLOADING first so storage failure is recoverable.
        MediaMessage media = mediaMessageRepository.save(MediaMessage.builder()
                .messageId(parent.getId())
                .senderId(senderId)
                .messageType(messageType)
                .status(MediaStatus.UPLOADING)
                .mimeType(detectedMime)
                .fileSize(file.getSize())
                .storageKey(storageKey)
                .originalFileName(safeOriginalName)
                .caption(request.getCaption())
                .build());

        // Read bytes before storage — the MultipartFile stream may not survive the
        // request thread boundary the async thumbnail pipeline needs.
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (java.io.IOException ex) {
            throw new com.chatflow.media.storage.StorageException("Could not read upload bytes", ex);
        }

        StoredMedia stored = mediaStorageService.store(file, storageKey);
        media.setMediaUrl(stored.getPublicUrl());
        media.setStatus(MediaStatus.READY);
        media = mediaMessageRepository.save(media);

        conversation.touchLastMessage(preview(request.getCaption(), messageType),
                parent.getCreatedAt(), seq);
        participantRepository.advanceReadCursor(conversationId, senderId, seq);

        log.debug("Media uploaded id={} message={} storageKey={}",
                media.getId(), parent.getId(), storageKey);

        MediaMessageResponse response = MediaMessageResponse.from(media, conversationId);
        List<UUID> recipients = participantRepository.findUserIdsByConversationId(conversationId)
                .stream().filter(id -> !id.equals(senderId)).toList();

        // Durable, coalesced notification via the outbox.
        if (!recipients.isEmpty()) {
            outboxWriter.writeNotification(OutboxEventType.MESSAGE_CREATED,
                    "conversation", conversationId,
                    new NotificationCommand(recipients, senderId, NotificationType.NEW_MESSAGE,
                            ReferenceType.CONVERSATION, conversationId,
                            preview(request.getCaption(), messageType), true));
        }

        // Best-effort live delivery after commit.
        final MediaMessageResponse frame = response;
        AfterCommit.run(() -> webSocketGateway.sendToUsers(recipients,
                OutboundMessage.of(OutboundMessage.Type.MEDIA_MESSAGE, frame)));

        // Async thumbnail generation (unchanged pipeline).
        eventPublisher.publishEvent(new MediaProcessingEvent(
                this, media.getId(), storageKey, messageType, detectedMime, fileBytes));

        return response;
    }

    @Transactional(readOnly = true)
    public MediaMessageResponse getById(UUID callerId, UUID mediaId) {
        MediaMessage media = mediaMessageRepository.findByIdAndDeletedFalse(mediaId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Media message not found: " + mediaId));
        Message parent = messageRepository.findById(media.getMessageId())
                .orElseThrow(() -> new SecurityException("Access denied to media message " + mediaId));
        if (!participantRepository.existsByConversationIdAndUserId(
                parent.getConversationId(), callerId)) {
            throw new SecurityException("Access denied to media message " + mediaId);
        }
        return MediaMessageResponse.from(media, parent.getConversationId());
    }

    private String preview(String caption, MessageType messageType) {
        if (caption != null && !caption.isBlank()) {
            return caption.length() <= PREVIEW_MAX ? caption : caption.substring(0, PREVIEW_MAX);
        }
        return "[" + messageType.name().toLowerCase() + "]";
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
