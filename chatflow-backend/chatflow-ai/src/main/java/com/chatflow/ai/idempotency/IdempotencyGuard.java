package com.chatflow.ai.idempotency;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Consumer-side dedup over {@code processed_events} (see core's IdempotencyGuard). ai's embedding
 * upsert is already idempotent on {@code message_id}, so the consumer uses process-then-mark
 * ({@link #alreadyProcessed} → work → {@link #markProcessed}) to avoid holding a transaction open
 * across the slow embed call.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private static final String INSERT =
            "INSERT INTO processed_events (consumer_group, event_id, processed_at) "
                    + "VALUES (?, ?, ?) ON CONFLICT DO NOTHING";

    private final JdbcTemplate jdbcTemplate;

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
