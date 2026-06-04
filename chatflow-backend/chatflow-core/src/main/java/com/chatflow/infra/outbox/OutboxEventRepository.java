package com.chatflow.infra.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Oldest pending event ids, for the poller to process one-by-one. */
    @Query("SELECT e.id FROM OutboxEvent e WHERE e.status = com.chatflow.infra.outbox.OutboxStatus.PENDING " +
            "ORDER BY e.createdAt ASC")
    List<UUID> findPendingIds(Pageable pageable);

    /**
     * Lock a single still-pending row with {@code FOR UPDATE SKIP LOCKED} so
     * concurrent pollers (or instances) never process the same event twice.
     * The {@code -2} lock timeout is Hibernate's SKIP_LOCKED sentinel.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM OutboxEvent e " +
            "WHERE e.id = :id AND e.status = com.chatflow.infra.outbox.OutboxStatus.PENDING")
    Optional<OutboxEvent> lockPending(@Param("id") UUID id);
}
