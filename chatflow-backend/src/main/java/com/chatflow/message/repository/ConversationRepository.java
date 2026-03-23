package com.chatflow.message.repository;

import com.chatflow.message.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByParticipantOneIdAndParticipantTwoId(UUID p1, UUID p2);
}