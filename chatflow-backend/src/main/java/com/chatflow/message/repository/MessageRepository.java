package com.chatflow.message.repository;

import com.chatflow.message.entity.Message;
import com.chatflow.message.entity.MessageStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    boolean existsByClientMessageId(String clientMessageId);

    Optional<Message> findByClientMessageId(String clientMessageId);

    List<Message> findByConversationIdOrderBySequenceNumberDesc(
            UUID conversationId, Pageable pageable);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversationId = :conversationId " +
            "AND m.sequenceNumber < :before " +
            "ORDER BY m.sequenceNumber DESC")
    List<Message> findPageBefore(@Param("conversationId") UUID conversationId,
                                 @Param("before") long before,
                                 Pageable pageable);

    @Query("SELECT m FROM Message m " +
            "WHERE m.conversationId = :conversationId " +
            "AND m.sequenceNumber > :after " +
            "ORDER BY m.sequenceNumber ASC")
    List<Message> findPageAfter(@Param("conversationId") UUID conversationId,
                                @Param("after") long after,
                                Pageable pageable);

    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) + 1 " +
            "FROM Message m WHERE m.conversationId = :conversationId")
    Long nextSequenceNumber(UUID conversationId);

    List<Message> findByReceiverIdAndStatusOrderBySequenceNumberAsc(
            UUID receiverId, MessageStatus status);

    @Modifying
    @Query("UPDATE Message m SET m.status = :newStatus, m.updatedAt = :now " +
            "WHERE m.id = :id AND m.status = :currentStatus")
    int updateStatus(@Param("id") UUID id,
                     @Param("currentStatus") MessageStatus currentStatus,
                     @Param("newStatus") MessageStatus newStatus,
                     @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Message m SET m.status = :newStatus, m.updatedAt = :now " +
            "WHERE m.conversationId = :conversationId " +
            "AND m.receiverId = :receiverId " +
            "AND m.status = :currentStatus")
    int bulkUpdateStatus(@Param("conversationId") UUID conversationId,
                         @Param("receiverId") UUID receiverId,
                         @Param("currentStatus") MessageStatus currentStatus,
                         @Param("newStatus") MessageStatus newStatus,
                         @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Message m SET m.status = 'SEEN', m.updatedAt = :now " +
            "WHERE m.conversationId = :conversationId " +
            "AND m.receiverId = :receiverId " +
            "AND m.status = 'DELIVERED' " +
            "AND m.sequenceNumber <= :upToSequenceNumber")
    int bulkMarkSeen(@Param("conversationId") UUID conversationId,
                     @Param("receiverId") UUID receiverId,
                     @Param("upToSequenceNumber") long upToSequenceNumber,
                     @Param("now") LocalDateTime now);

    List<Message> findByConversationIdAndReceiverIdAndStatus(
            UUID conversationId, UUID receiverId, MessageStatus status);

    @Query("SELECT DISTINCT m.senderId FROM Message m " +
            "WHERE m.conversationId = :conversationId " +
            "AND m.receiverId = :receiverId " +
            "AND m.status = 'DELIVERED' " +
            "AND m.sequenceNumber <= :upToSequenceNumber")
    List<UUID> findSenderIdsByConversationAndReceiver(
            @Param("conversationId") UUID conversationId,
            @Param("receiverId") UUID receiverId,
            @Param("upToSequenceNumber") long upToSequenceNumber);
}
