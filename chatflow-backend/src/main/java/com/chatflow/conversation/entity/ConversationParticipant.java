package com.chatflow.conversation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Membership of a user in a conversation, for both DIRECT and GROUP.
 *
 * <p>Replaces {@code group.entity.GroupMember} and the participantOne/Two columns
 * on the old conversation. Read/delivery state is tracked here as sequence-number
 * watermarks — the single mechanism behind both 1:1 ticks and group receipts.
 */
@Entity
@Table(
        name = "conversation_participants",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_conversation_participant",
                        columnNames = {"conversation_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_cp_conversation", columnList = "conversation_id"),
                @Index(name = "idx_cp_user", columnList = "user_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipantRole role;

    /** Highest message sequence this user has read. */
    @Builder.Default
    @Column(name = "last_read_seq", nullable = false)
    private long lastReadSeq = 0L;

    /** Highest message sequence delivered to this user. */
    @Builder.Default
    @Column(name = "last_delivered_seq", nullable = false)
    private long lastDeliveredSeq = 0L;

    @Builder.Default
    @Column(nullable = false)
    private boolean muted = false;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    void prePersist() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
    }

    public static ConversationParticipant of(UUID conversationId, UUID userId, ParticipantRole role) {
        return ConversationParticipant.builder()
                .conversationId(conversationId)
                .userId(userId)
                .role(role)
                .build();
    }

    /** Advance the delivered watermark; never moves backwards. */
    public boolean markDelivered(long seq) {
        if (seq > lastDeliveredSeq) {
            lastDeliveredSeq = seq;
            return true;
        }
        return false;
    }

    /** Advance the read watermark (and delivered, since read implies delivered). */
    public boolean markRead(long seq) {
        boolean changed = false;
        if (seq > lastDeliveredSeq) {
            lastDeliveredSeq = seq;
            changed = true;
        }
        if (seq > lastReadSeq) {
            lastReadSeq = seq;
            changed = true;
        }
        return changed;
    }
}
