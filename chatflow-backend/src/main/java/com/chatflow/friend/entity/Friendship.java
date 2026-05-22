package com.chatflow.friend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "friendships",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_friendship_pair",
                        columnNames = {"user_one_id", "user_two_id"}
                )
        },
        indexes = {
                @Index(name = "idx_friendship_user_one", columnList = "user_one_id"),
                @Index(name = "idx_friendship_user_two", columnList = "user_two_id"),
                @Index(name = "idx_friendship_status", columnList = "status")
        }
)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_one_id", nullable = false, updatable = false)
    private UUID userOneId;

    @Column(name = "user_two_id", nullable = false, updatable = false)
    private UUID userTwoId;

    @Column(name = "initiator_id", nullable = false)
    private UUID initiatorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FriendshipStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public static Friendship create(UUID fromUserId, UUID toUserId) {

        // Canonical ordering prevents duplicate friendship pairs
        UUID userOneId =
                fromUserId.compareTo(toUserId) < 0
                        ? fromUserId
                        : toUserId;

        UUID userTwoId =
                fromUserId.compareTo(toUserId) < 0
                        ? toUserId
                        : fromUserId;

        return Friendship.builder()
                .userOneId(userOneId)
                .userTwoId(userTwoId)
                .initiatorId(fromUserId)
                .status(FriendshipStatus.PENDING)
                .build();
    }

    public boolean involves(UUID userId) {
        return userOneId.equals(userId)
                || userTwoId.equals(userId);
    }

    public UUID otherUserId(UUID userId) {

        if (userOneId.equals(userId)) {
            return userTwoId;
        }

        if (userTwoId.equals(userId)) {
            return userOneId;
        }

        throw new IllegalArgumentException(
                "User is not part of this friendship"
        );
    }

    public void accept() {
        this.status = FriendshipStatus.ACCEPTED;
    }

    public void reject() {
        this.status = FriendshipStatus.DECLINED;
    }
}