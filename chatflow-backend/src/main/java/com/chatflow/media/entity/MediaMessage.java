package com.chatflow.media.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Detail row for a media attachment. In the unified model a media message is a
 * {@code messages} row of {@code type=MEDIA} plus this detail row linked by
 * {@link #messageId}; it no longer carries its own conversation/group linkage
 * (that lives on the parent message).
 */
@Entity
@Table(name = "media_messages", indexes = {
        @Index(name = "idx_media_message",  columnList = "message_id"),
        @Index(name = "idx_media_sender",   columnList = "sender_id"),
        @Index(name = "idx_media_status",   columnList = "status"),
        @Index(name = "idx_media_created",  columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The parent {@code messages} row (type=MEDIA) this attachment belongs to. */
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, updatable = false)
    private MessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MediaStatus status = MediaStatus.UPLOADING;

    /** Public URL (or storage key) for the media object */
    @Column(name = "media_url", length = 1024)
    private String mediaUrl;

    /** Thumbnail URL — populated asynchronously by the processing pipeline */
    @Column(name = "thumbnail_url", length = 1024)
    private String thumbnailUrl;

    /**
     * MIME type detected from the file's magic bytes — NOT trusted from
     * the client Content-Type header.
     */
    @Column(name = "mime_type", nullable = false, updatable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private Long fileSize;

    /**
     * UUID-based storage filename — never the original. Stored here so
     * the storage object can be deleted without scanning the URL.
     */
    @Column(name = "storage_key", nullable = false, updatable = false, length = 255)
    private String storageKey;

    /** Original filename from the upload — stored for display only, sanitised. */
    @Column(name = "original_file_name", updatable = false, length = 255)
    private String originalFileName;

    @Column(length = 1000)
    private String caption;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
