package com.chatflow.group.repository;

import com.chatflow.group.entity.GroupMember;
import com.chatflow.group.entity.GroupMemberRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, UUID> {

    List<GroupMember> findByGroupId(UUID groupId);

    List<GroupMember> findByUserId(UUID userId);

    Optional<GroupMember> findByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserId(UUID groupId, UUID userId);

    boolean existsByGroupIdAndUserIdAndRole(UUID groupId, UUID userId, GroupMemberRole role);

    void deleteByGroupIdAndUserId(UUID groupId, UUID userId);

    @Query("SELECT m.userId FROM GroupMember m WHERE m.groupId = :groupId")
    List<UUID> findUserIdsByGroupId(@Param("groupId") UUID groupId);

    @Modifying
    @Query("UPDATE GroupMember m SET m.lastReadSequenceNumber = :upTo " +
            "WHERE m.groupId = :groupId AND m.userId = :userId " +
            "AND m.lastReadSequenceNumber < :upTo")
    int advanceReadCursor(@Param("groupId") UUID groupId,
                          @Param("userId") UUID userId,
                          @Param("upTo") long upTo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM GroupMember m WHERE m.groupId = :groupId")
    List<GroupMember> findByGroupIdForUpdate(@Param("groupId") UUID groupId);

    @Modifying
    @Query("UPDATE GroupMember m SET m.lastDeliveredSequenceNumber = :upTo " +
            "WHERE m.groupId = :groupId AND m.userId = :userId " +
            "AND m.lastDeliveredSequenceNumber < :upTo")
    int advanceDeliveryCursor(@Param("groupId") UUID groupId,
                              @Param("userId") UUID userId,
                              @Param("upTo") long upTo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM GroupMember m WHERE m.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") UUID groupId);
}