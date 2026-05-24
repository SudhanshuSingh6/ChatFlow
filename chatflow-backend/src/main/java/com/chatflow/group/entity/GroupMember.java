package com.chatflow.group.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "group_members",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_member",
                        columnNames = {"group_id", "user_id"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_gm_group",
                        columnList = "group_id"
                ),
                @Index(
                        name = "idx_gm_user",
                        columnList = "user_id"
                )
        }
)

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupMemberRole role;

    @Builder.Default
    @Column(nullable = false)
    private long lastReadSequenceNumber = 0L;

    @Builder.Default
    @Column(nullable = false)
    private long lastDeliveredSequenceNumber = 0L;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        joinedAt = LocalDateTime.now();
    }
}