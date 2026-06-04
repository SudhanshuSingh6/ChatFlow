package com.chatflow.infra.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional outbox row. Written in the same transaction as the state change it
 * describes, then drained asynchronously by {@link OutboxPoller} → {@link OutboxDispatcher}.
 * This gives at-least-once, crash-safe delivery of side effects (notifications,
 * cross-server fan-out) without a distributed transaction.
 *
 * <p>The {@code payload} holds the event body as JSON text (e.g. a serialized
 * {@code NotificationCommand}); the dispatcher re-materializes it per event type.
 */
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                // The poller scans pending rows oldest-first.
                @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Aggregate this event belongs to, e.g. "conversation", "friendship". */
    @Column(name = "aggregate_type", nullable = false, updatable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", updatable = false)
    private UUID aggregateId;

    /** Routing key for the dispatcher; see {@link OutboxEventType}. */
    @Column(name = "event_type", nullable = false, updatable = false, length = 64)
    private String eventType;

    /** Event body as JSON text. */
    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }
}
