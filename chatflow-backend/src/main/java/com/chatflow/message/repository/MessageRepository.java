package com.chatflow.message.repository;

import com.chatflow.message.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    boolean existsByClientMessageId(String clientMessageId);

    List<Message> findByConversationIdOrderBySequenceNumberAsc(UUID conversationId);
}