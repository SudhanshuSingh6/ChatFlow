package com.chatflow.conversation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Unified conversation — both 1:1 (DIRECT) and group (GROUP) chats.
 *
 * <p>Replaces the old {@code message.entity.Conversation} (which hardcoded
 * participantOne/Two) and the {@code group.entity.Group}. Membership lives in
 * {@link ConversationParticipant}; messages in {@code Message} reference this by id.
 */
@Entity
@Table(
        name = "conversations",
        uniqueConstraints = {
                // Prevents duplicate direct conversations between the same pair.
                @UniqueConstraint(name = "uk_conversation_dm_key", columnNames = "dm_key")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ConversationType type;

    /** Group name. Null for DIRECT conversations. */
    @Column(name = "name")
    private String name;

    /** Group creator/owner. Null for DIRECT conversations. */
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /**
     * Canonical "minId:maxId" key for DIRECT conversations, used to enforce a
     * single conversation per pair via {@code uk_conversation_dm_key}. Null for
     * groups (multiple groups can share members, so no uniqueness applies).
     */
    @Column(name = "dm_key", updatable = false)
    private String dmKey;

    // ---- denormalized list-ordering fields ----

    @Column(name = "last_message_preview")
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_seq")
    private Long lastMessageSeq;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    // ---- factories ----

    /** Canonical DM key independent of argument order. */
    public static String dmKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    public static Conversation direct(UUID userA, UUID userB) {
        return Conversation.builder()
                .type(ConversationType.DIRECT)
                .dmKey(dmKey(userA, userB))
                .build();
    }

    public static Conversation group(String name, UUID createdBy) {
        return Conversation.builder()
                .type(ConversationType.GROUP)
                .name(name)
                .createdBy(createdBy)
                .build();
    }

    public boolean isDirect() {
        return type == ConversationType.DIRECT;
    }

    public boolean isGroup() {
        return type == ConversationType.GROUP;
    }

    /** Update the denormalized last-message fields after a new message lands. */
    public void touchLastMessage(String preview, Instant at, long seq) {
        this.lastMessagePreview = preview;
        this.lastMessageAt = at;
        this.lastMessageSeq = seq;
    }
}
