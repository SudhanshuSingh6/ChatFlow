package com.chatflow.message.repository;

import com.chatflow.message.entity.Conversation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByParticipantOneIdAndParticipantTwoId(UUID p1, UUID p2);

    List<Conversation> findByParticipantOneIdOrParticipantTwoIdOrderByLastMessageAtDesc(UUID userId1, UUID userId2);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findByIdForUpdate(UUID id);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Conversation c " +
            "WHERE c.id = :conversationId " +
            "AND (c.participantOneId = :userId OR c.participantTwoId = :userId)")
    boolean existsParticipant(@Param("conversationId") UUID conversationId,
                              @Param("userId") UUID userId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END " +
            "FROM Conversation c " +
            "WHERE (c.participantOneId = :userA AND c.participantTwoId = :userB) " +
            "OR (c.participantOneId = :userB AND c.participantTwoId = :userA)")
    boolean existsConversationBetween(@Param("userA") UUID userA,
                                      @Param("userB") UUID userB);
}
