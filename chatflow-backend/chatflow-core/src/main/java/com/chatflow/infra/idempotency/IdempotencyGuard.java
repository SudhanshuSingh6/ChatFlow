package com.chatflow.infra.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side dedup over the {@code processed_events} table — the mechanism that makes Kafka's
 * at-least-once delivery effectively-once at the consumer. Keyed on {@code (consumer_group,
 * event_id)} where {@code event_id} is the stable outbox row id.
 *
 * <p>{@link #firstTime} is atomic (INSERT ... ON CONFLICT DO NOTHING): call it inside the same
 * transaction as the handler so the claim and the work commit (or roll back) together. For
 * handlers that are already idempotent, {@link #alreadyProcessed} + {@link #markProcessed}
 * (process-then-mark) avoids holding a transaction open across slow work.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private static final String INSERT =
            "INSERT INTO processed_events (consumer_group, event_id, processed_at) "
                    + "VALUES (?, ?, ?) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbcTemplate;

    /** Atomically claim the event. {@code true} = first delivery (proceed); {@code false} = duplicate (skip). */
    public boolean firstTime(String consumerGroup, UUID eventId) {
        return jdbcTemplate.update(INSERT, consumerGroup, eventId, Timestamp.from(Instant.now())) == 1;
    }

    public boolean alreadyProcessed(String consumerGroup, UUID eventId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_events WHERE consumer_group = ? AND event_id = ?",
                Integer.class, consumerGroup, eventId);
        return count != null && count > 0;
    }

    public void markProcessed(String consumerGroup, UUID eventId) {
        jdbcTemplate.update(INSERT, consumerGroup, eventId, Timestamp.from(Instant.now()));
    }
}
