package com.chatflow.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notif_recipient_created",
                        columnList = "recipient_id, created_at"),
                @Index(name = "idx_notif_recipient_read",
                        columnList = "recipient_id, read")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Who sees this notification. */
    @Column(name = "recipient_id", nullable = false, updatable = false)
    private UUID recipientId;

    /** Who caused it (nullable for system notifications). */
    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", updatable = false)
    private ReferenceType referenceType;

    /** ID of the friendship / conversation / message this points at. */
    @Column(name = "reference_id")
    private UUID referenceId;

    /** Denormalized snippet for the feed (e.g. "Alice: hey there"). */
    @Column(length = 280)
    private String preview;

    /** Coalescing counter: how many underlying events this row represents. */
    @Column(name = "event_count", nullable = false)
    @Builder.Default
    private int eventCount = 1;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    /**
     * Soft-delete marker. Set when the recipient dismisses the notification; the row
     * is hidden from the feed and unread count, and physically purged later by the
     * daily cleanup job.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markRead() {
        if (!read) {
            read = true;
            readAt = Instant.now();
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Soft-delete this notification; the row is retained until the cleanup job purges it. */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public void coalesce(String newPreview) {
        this.eventCount += 1;
        this.preview = newPreview;
        this.createdAt = Instant.now();
    }
}
