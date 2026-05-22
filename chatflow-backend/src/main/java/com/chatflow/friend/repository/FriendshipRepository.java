package com.chatflow.friend.repository;

import com.chatflow.friend.entity.Friendship;
import com.chatflow.friend.entity.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    @Query("SELECT f FROM Friendship f " +
            "WHERE (f.userOneId = :a AND f.userTwoId = :b) " +
            "OR (f.userOneId = :b AND f.userTwoId = :a)")
    Optional<Friendship> findByUsers(@Param("a") UUID userA, @Param("b") UUID userB);

    @Query("SELECT f FROM Friendship f " +
            "WHERE (f.userOneId = :userId OR f.userTwoId = :userId) " +
            "AND f.status = :status")
    List<Friendship> findByUserAndStatus(@Param("userId") UUID userId,
                                         @Param("status") FriendshipStatus status);

    @Query("SELECT f FROM Friendship f " +
            "WHERE (f.userOneId = :userId OR f.userTwoId = :userId) " +
            "AND f.status = 'PENDING' " +
            "AND f.initiatorId != :userId")
    List<Friendship> findPendingReceived(@Param("userId") UUID userId);

    @Query("SELECT f FROM Friendship f " +
            "WHERE f.initiatorId = :userId AND f.status = 'PENDING'")
    List<Friendship> findPendingSent(@Param("userId") UUID userId);

    @Query("SELECT COUNT(f) > 0 FROM Friendship f " +
            "WHERE (f.userOneId = :a AND f.userTwoId = :b " +
            "OR f.userOneId = :b AND f.userTwoId = :a) " +
            "AND f.status = 'ACCEPTED'")
    boolean areFriends(@Param("a") UUID userA, @Param("b") UUID userB);
}