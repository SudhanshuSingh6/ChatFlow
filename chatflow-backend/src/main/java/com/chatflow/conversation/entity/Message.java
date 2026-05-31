package com.chatflow.conversation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Unified message for both DIRECT and GROUP conversations.
 *
 * <p>Replaces {@code message.entity.Message} and {@code group.entity.GroupMessage}.
 * Crucially there is no per-message {@code status}/{@code receiverId}: delivery and
 * read state are derived from each {@link ConversationParticipant}'s watermarks, so
 * the same logic serves 1:1 ticks and group receipts.
 */
@Entity
@Table(
        name = "messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_message_sequence",
                        columnNames = {"conversation_id", "sequence_number"}
                ),
                @UniqueConstraint(
                        name = "uk_message_client_id",
                        columnNames = {"conversation_id", "client_message_id"}
                )
        },
        indexes = {
                @Index(name = "idx_message_conversation_seq",
                        columnList = "conversation_id, sequence_number"),
                @Index(name = "idx_message_sender", columnList = "sender_id"),
                @Index(name = "idx_message_created", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    /** Author. Null only for SYSTEM messages. */
    @Column(name = "sender_id", updatable = false)
    private UUID senderId;

    /** Client-supplied idempotency key. Null for server-generated SYSTEM messages. */
    @Column(name = "client_message_id", length = 100, updatable = false)
    private String clientMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    /** Text body, or the system-event text. Null for media-only messages. */
    @Column(length = 4000)
    private String content;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private Long sequenceNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    /** Soft-delete marker; row is retained as a tombstone. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (type == null) {
            type = MessageType.TEXT;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.content = null;
    }

    public void edit(String newContent) {
        this.content = newContent;
        this.editedAt = Instant.now();
    }
}
