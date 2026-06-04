package com.chatflow.conversation.repository;

import com.chatflow.conversation.entity.ConversationParticipant;
import com.chatflow.conversation.entity.ParticipantRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationParticipantRepository
        extends JpaRepository<ConversationParticipant, UUID> {

    List<ConversationParticipant> findByConversationId(UUID conversationId);

    List<ConversationParticipant> findByUserId(UUID userId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserId(UUID conversationId, UUID userId);

    boolean existsByConversationIdAndUserIdAndRole(
            UUID conversationId, UUID userId, ParticipantRole role);

    void deleteByConversationIdAndUserId(UUID conversationId, UUID userId);

    long countByConversationId(UUID conversationId);

    @Query("SELECT p.userId FROM ConversationParticipant p WHERE p.conversationId = :conversationId")
    List<UUID> findUserIdsByConversationId(@Param("conversationId") UUID conversationId);

    /** The conversations a user belongs to — the scope for their cross-conversation search. */
    @Query("SELECT p.conversationId FROM ConversationParticipant p WHERE p.userId = :userId")
    List<UUID> findConversationIdsByUserId(@Param("userId") UUID userId);

    /** Distinct users who share at least one conversation with the given user (presence contacts). */
    @Query("SELECT DISTINCT other.userId FROM ConversationParticipant me, ConversationParticipant other " +
            "WHERE me.conversationId = other.conversationId " +
            "AND me.userId = :userId AND other.userId <> :userId")
    List<UUID> findContactUserIds(@Param("userId") UUID userId);

    /** Whether two users share any conversation (used to authorize presence lookups). */
    @Query("SELECT COUNT(a) > 0 FROM ConversationParticipant a, ConversationParticipant b " +
            "WHERE a.conversationId = b.conversationId AND a.userId = :userA AND b.userId = :userB")
    boolean existsSharedConversation(@Param("userA") UUID userA, @Param("userB") UUID userB);

    /** Lock all participant rows of a conversation — used for fan-out receipt updates. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ConversationParticipant p WHERE p.conversationId = :conversationId")
    List<ConversationParticipant> findByConversationIdForUpdate(
            @Param("conversationId") UUID conversationId);

    /**
     * Advance the read watermark (and delivered, since read implies delivered).
     * Monotonic — never moves a cursor backwards.
     */
    @Modifying
    @Query("UPDATE ConversationParticipant p " +
            "SET p.lastReadSeq = :upTo, " +
            "    p.lastDeliveredSeq = CASE WHEN p.lastDeliveredSeq < :upTo " +
            "                              THEN :upTo ELSE p.lastDeliveredSeq END " +
            "WHERE p.conversationId = :conversationId AND p.userId = :userId " +
            "AND p.lastReadSeq < :upTo")
    int advanceReadCursor(@Param("conversationId") UUID conversationId,
                          @Param("userId") UUID userId,
                          @Param("upTo") long upTo);

    /** Advance only the delivered watermark. Monotonic. */
    @Modifying
    @Query("UPDATE ConversationParticipant p SET p.lastDeliveredSeq = :upTo " +
            "WHERE p.conversationId = :conversationId AND p.userId = :userId " +
            "AND p.lastDeliveredSeq < :upTo")
    int advanceDeliveryCursor(@Param("conversationId") UUID conversationId,
                              @Param("userId") UUID userId,
                              @Param("upTo") long upTo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ConversationParticipant p WHERE p.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") UUID conversationId);
}
