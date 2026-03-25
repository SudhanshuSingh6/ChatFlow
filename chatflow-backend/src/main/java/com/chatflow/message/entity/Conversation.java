package com.chatflow.message.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conv_participants",
                columnList = "participantOneId, participantTwoId",
                unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID participantOneId;

    @Column(nullable = false)
    private UUID participantTwoId;

    private String lastMessage;

    private LocalDateTime lastMessageAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static Conversation create(UUID user1, UUID user2) {
        UUID p1 = user1.compareTo(user2) < 0 ? user1 : user2;
        UUID p2 = user1.compareTo(user2) < 0 ? user2 : user1;

        return Conversation.builder()
                .participantOneId(p1)
                .participantTwoId(p2)
                .build();
    }
}