package com.chatflow.media.processing;

import com.chatflow.media.entity.MessageType;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by {@code MediaMessageService} after an upload is persisted and
 * stored. Carries the raw bytes so downstream async processing (thumbnail
 * generation in Phase 6) does not depend on the request-scoped
 * {@code MultipartFile}, whose stream may not survive the thread boundary.
 *
 * <p>Consumed asynchronously by {@link ThumbnailService}; the upload response
 * is never blocked on it.
 */
@Getter
public class MediaProcessingEvent extends ApplicationEvent {

    private final UUID mediaMessageId;
    private final String storageKey;
    private final MessageType messageType;
    private final String mimeType;
    private final byte[] fileBytes;

    public MediaProcessingEvent(Object source,
                                UUID mediaMessageId,
                                String storageKey,
                                MessageType messageType,
                                String mimeType,
                                byte[] fileBytes) {
        super(source);
        this.mediaMessageId = mediaMessageId;
        this.storageKey = storageKey;
        this.messageType = messageType;
        this.mimeType = mimeType;
        this.fileBytes = fileBytes;
    }
}
