package com.chatflow.notification.repository;

import com.chatflow.notification.entity.Notification;
import com.chatflow.notification.entity.NotificationType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {


    @Query("""
            select n from Notification n
            where n.recipientId = :recipientId
              and n.deletedAt is null
              and (:cursor is null or n.createdAt < :cursor)
            order by n.createdAt desc
            """)
    List<Notification> findFeed(@Param("recipientId") UUID recipientId,
                                @Param("cursor") Instant cursor,
                                Pageable pageable);


    @Query("""
            select count(n) from Notification n
            where n.recipientId = :recipientId and n.read = false and n.deletedAt is null
            """)
    long countByRecipientIdAndReadFalse(@Param("recipientId") UUID recipientId);


    Optional<Notification> findFirstByRecipientIdAndReferenceIdAndTypeAndReadFalseAndDeletedAtIsNull(
            UUID recipientId, UUID referenceId, NotificationType type);


    @Modifying
    @Query("""
            update Notification n
               set n.read = true, n.readAt = :now
             where n.id = :id and n.recipientId = :recipientId and n.read = false
            """)
    int markRead(@Param("id") UUID id,
                 @Param("recipientId") UUID recipientId,
                 @Param("now") Instant now);

    @Modifying
    @Query("""
            update Notification n
               set n.read = true, n.readAt = :now
             where n.recipientId = :recipientId and n.read = false
            """)
    int markAllRead(@Param("recipientId") UUID recipientId,
                    @Param("now") Instant now);

    @Modifying
    @Query("""
            update Notification n
               set n.deletedAt = :now
             where n.id = :id and n.recipientId = :recipientId and n.deletedAt is null
            """)
    int softDelete(@Param("id") UUID id,
                   @Param("recipientId") UUID recipientId,
                   @Param("now") Instant now);

    // ---- retention purge ----

    /** Hard-deletes notifications soft-deleted before the cutoff. */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.deletedAt < :cutoff")
    int purgeDeletedBefore(@Param("cutoff") Instant cutoff);

    /**
     * Hard-deletes every notification scoped to a conversation — used by the group
     * cleanup cascade. All conversation-scoped notifications (NEW_MESSAGE and the
     * GROUP_* types) carry {@code referenceType = CONVERSATION} and
     * {@code referenceId = conversationId}.
     */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n " +
            "WHERE n.referenceType = com.chatflow.notification.entity.ReferenceType.CONVERSATION " +
            "AND n.referenceId = :conversationId")
    int deleteByConversation(@Param("conversationId") UUID conversationId);
}
