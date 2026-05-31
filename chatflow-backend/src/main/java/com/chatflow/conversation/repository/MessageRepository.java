package com.chatflow.conversation.repository;

import com.chatflow.conversation.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    // ---- idempotency ----

    boolean existsByConversationIdAndClientMessageId(UUID conversationId, String clientMessageId);

    Optional<Message> findByConversationIdAndClientMessageId(UUID conversationId, String clientMessageId);

    // ---- sequence allocation ----

    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) + 1 " +
            "FROM Message m WHERE m.conversationId = :conversationId")
    long nextSequenceNumber(@Param("conversationId") UUID conversationId);

    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) " +
            "FROM Message m WHERE m.conversationId = :conversationId")
    long maxSequenceNumber(@Param("conversationId") UUID conversationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM Message m WHERE m.conversationId = :conversationId")
    int deleteByConversationId(@Param("conversationId") UUID conversationId);

    // ---- history paging ----

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversationId = :conversationId AND m.sequenceNumber < :before " +
            "ORDER BY m.sequenceNumber DESC")
    List<Message> findPageBefore(@Param("conversationId") UUID conversationId,
                                 @Param("before") long before,
                                 Pageable pageable);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversationId = :conversationId AND m.sequenceNumber > :after " +
            "ORDER BY m.sequenceNumber ASC")
    List<Message> findPageAfter(@Param("conversationId") UUID conversationId,
                                @Param("after") long after,
                                Pageable pageable);

    // ---- replay (offline inbox) ----

    /**
     * Every message across all the user's conversations that hasn't yet been
     * delivered to them (seq beyond their per-conversation delivered watermark),
     * excluding their own messages. Ordered by conversation then sequence.
     */
    @Query("SELECT m FROM Message m " +
            "JOIN ConversationParticipant p ON p.conversationId = m.conversationId " +
            "WHERE p.userId = :userId " +
            "AND m.senderId <> :userId " +
            "AND m.deletedAt IS NULL " +
            "AND m.sequenceNumber > p.lastDeliveredSeq " +
            "ORDER BY m.conversationId ASC, m.sequenceNumber ASC")
    List<Message> findUndeliveredForUser(@Param("userId") UUID userId);

    // ---- unread count for a participant ----

    @Query("SELECT COUNT(m) FROM Message m " +
            "WHERE m.conversationId = :conversationId " +
            "AND m.sequenceNumber > :lastReadSeq " +
            "AND m.senderId <> :userId")
    long countUnread(@Param("conversationId") UUID conversationId,
                     @Param("lastReadSeq") long lastReadSeq,
                     @Param("userId") UUID userId);

    // ---- search (cursor: createdAt + id, null on first page) ----

    /** Case-insensitive substring search within one conversation, newest first. */
    @Query("SELECT m FROM Message m " +
            "WHERE m.conversationId = :conversationId " +
            "AND LOWER(m.content) LIKE :term ESCAPE '\\' " +
            "AND (:beforeTime IS NULL OR m.createdAt < :beforeTime " +
            "     OR (m.createdAt = :beforeTime AND m.id < :beforeId)) " +
            "ORDER BY m.createdAt DESC, m.id DESC")
    List<Message> searchInConversation(@Param("conversationId") UUID conversationId,
                                       @Param("term") String term,
                                       @Param("beforeTime") Instant beforeTime,
                                       @Param("beforeId") UUID beforeId,
                                       Pageable pageable);

    /** Case-insensitive search across every conversation the user is a member of. */
    @Query("SELECT m FROM Message m " +
            "WHERE m.conversationId IN (SELECT p.conversationId FROM ConversationParticipant p " +
            "                           WHERE p.userId = :userId) " +
            "AND LOWER(m.content) LIKE :term ESCAPE '\\' " +
            "AND (:beforeTime IS NULL OR m.createdAt < :beforeTime " +
            "     OR (m.createdAt = :beforeTime AND m.id < :beforeId)) " +
            "ORDER BY m.createdAt DESC, m.id DESC")
    List<Message> searchForUser(@Param("userId") UUID userId,
                                @Param("term") String term,
                                @Param("beforeTime") Instant beforeTime,
                                @Param("beforeId") UUID beforeId,
                                Pageable pageable);
}
