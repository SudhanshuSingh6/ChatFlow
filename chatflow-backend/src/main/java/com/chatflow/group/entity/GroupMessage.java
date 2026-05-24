package com.chatflow.group.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "group_messages",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_group_client_message",
                        columnNames = {"group_id", "client_message_id"}
                ),
                @UniqueConstraint(
                        name = "uk_group_sequence",
                        columnNames = {"group_id", "sequence_number"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_group_messages_group_seq",
                        columnList = "group_id, sequence_number"
                ),
                @Index(
                        name = "idx_group_messages_sender",
                        columnList = "sender_id"
                ),
                @Index(
                        name = "idx_group_messages_created",
                        columnList = "created_at"
                )
        }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_message_id", nullable = false, length = 100)
    private String clientMessageId;

    @Column(name = "group_id", nullable = false, updatable = false)
    private UUID groupId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}