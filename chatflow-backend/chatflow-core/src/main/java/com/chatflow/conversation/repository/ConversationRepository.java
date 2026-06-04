package com.chatflow.conversation.repository;

import com.chatflow.conversation.entity.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** Look up an existing DIRECT conversation by its canonical pair key. */
    Optional<Conversation> findByDmKey(String dmKey);

    boolean existsByDmKey(String dmKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") UUID id);

    /** Every conversation the user participates in, most-recent activity first. */
    @Query("SELECT c FROM Conversation c " +
            "WHERE c.id IN (SELECT p.conversationId FROM ConversationParticipant p " +
            "               WHERE p.userId = :userId) " +
            "AND c.deletedAt IS NULL " +
            "ORDER BY c.lastMessageAt DESC NULLS LAST")
    List<Conversation> findAllForUser(@Param("userId") UUID userId);

    /** Ids of GROUP conversations soft-deleted before the cutoff — fed to the cleanup cascade. */
    @Query("SELECT c.id FROM Conversation c " +
            "WHERE c.type = com.chatflow.conversation.entity.ConversationType.GROUP " +
            "AND c.deletedAt IS NOT NULL AND c.deletedAt < :cutoff")
    List<UUID> findGroupIdsToPurge(@Param("cutoff") Instant cutoff);
}
