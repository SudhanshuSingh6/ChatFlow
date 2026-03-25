package com.chatflow.message.repository;

import com.chatflow.message.entity.Message;
import com.chatflow.message.entity.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    boolean existsByClientMessageId(String clientMessageId);

    Optional<Message> findByClientMessageId(String clientMessageId);

    List<Message> findByConversationIdOrderBySequenceNumberDesc(
            UUID conversationId, Pageable pageable);

    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) + 1 " +
            "FROM Message m WHERE m.conversationId = :conversationId")
    Long nextSequenceNumber(UUID conversationId);

    List<Message> findByReceiverIdAndStatus(UUID receiverId, MessageStatus status);
}