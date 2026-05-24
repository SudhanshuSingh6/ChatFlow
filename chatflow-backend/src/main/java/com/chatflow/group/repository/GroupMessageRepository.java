package com.chatflow.group.repository;

import com.chatflow.group.entity.GroupMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMessageRepository extends JpaRepository<GroupMessage, UUID> {

    boolean existsByGroupIdAndClientMessageId(UUID groupId, String clientMessageId);

    Optional<GroupMessage> findByGroupIdAndClientMessageId(UUID groupId, String clientMessageId);

    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) + 1 " +
            "FROM GroupMessage m WHERE m.groupId = :groupId")
    Long nextSequenceNumber(@Param("groupId") UUID groupId);

    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) " +
            "FROM GroupMessage m WHERE m.groupId = :groupId")
    Long maxSequenceNumber(@Param("groupId") UUID groupId);

    @Query("SELECT m FROM GroupMessage m " +
            "WHERE m.groupId = :groupId AND m.sequenceNumber < :before " +
            "ORDER BY m.sequenceNumber DESC")
    List<GroupMessage> findPageBefore(@Param("groupId") UUID groupId,
                                      @Param("before") long before,
                                      Pageable pageable);

    @Query("SELECT m FROM GroupMessage m " +
            "WHERE m.groupId = :groupId AND m.sequenceNumber > :after " +
            "ORDER BY m.sequenceNumber ASC")
    List<GroupMessage> findPageAfter(@Param("groupId") UUID groupId,
                                     @Param("after") long after,
                                     Pageable pageable);

    @Query("SELECT COUNT(m) FROM GroupMessage m " +
            "WHERE m.groupId = :groupId AND m.sequenceNumber > :lastReadSeq " +
            "AND m.senderId != :userId")
    long countUnread(@Param("groupId") UUID groupId,
                     @Param("lastReadSeq") long lastReadSeq,
                     @Param("userId") UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GroupMessage m WHERE m.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") UUID groupId);

    @Query("SELECT m FROM GroupMessage m " +
            "WHERE m.groupId = :groupId " +
            "AND m.sequenceNumber > :after " +
            "AND m.senderId <> :userId " +
            "ORDER BY m.sequenceNumber ASC")
    List<GroupMessage> findPageAfterExcludingSender(@Param("groupId") UUID groupId,
                                                    @Param("after") long after,
                                                    @Param("userId") UUID userId,
                                                    Pageable pageable);
}