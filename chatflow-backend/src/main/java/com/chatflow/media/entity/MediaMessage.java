package com.chatflow.media.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_messages", indexes = {
        @Index(name = "idx_media_sender",       columnList = "senderId"),
        @Index(name = "idx_media_conversation",  columnList = "conversationId"),
        @Index(name = "idx_media_group",         columnList = "groupId"),
        @Index(name = "idx_media_status",        columnList = "status"),
        @Index(name = "idx_media_created",       columnList = "createdAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private UUID senderId;

    /**
     * Exactly one of conversationId or groupId must be non-null.
     * A CHECK constraint is enforced at the service layer — JPA does not
     * support XOR constraints natively.
     */
    @Column(updatable = false)
    private UUID conversationId;

    @Column(updatable = false)
    private UUID groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MessageType messageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MediaStatus status = MediaStatus.UPLOADING;

    /** Public URL (or storage key) for the media object */
    @Column(length = 1024)
    private String mediaUrl;

    /** Thumbnail URL — populated asynchronously in Phase 6 */
    @Column(length = 1024)
    private String thumbnailUrl;

    /**
     * MIME type detected from the file's magic bytes — NOT trusted from
     * the client Content-Type header. Set after validation in Phase 2.
     */
    @Column(nullable = false, updatable = false, length = 100)
    private String mimeType;

    @Column(nullable = false, updatable = false)
    private Long fileSize;

    /**
     * UUID-based storage filename — never the original. Stored here so
     * the storage object can be deleted without scanning the URL.
     */
    @Column(nullable = false, updatable = false, length = 255)
    private String storageKey;

    /**
     * Original filename from the upload — stored for display only.
     * Never used as a file system path. Sanitised and truncated at the
     * service layer before persistence.
     */
    @Column(updatable = false, length = 255)
    private String originalFileName;

    @Column(length = 1000)
    private String caption;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
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