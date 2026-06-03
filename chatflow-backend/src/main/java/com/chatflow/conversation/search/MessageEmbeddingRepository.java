package com.chatflow.conversation.search;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for {@code message_embeddings}. Uses {@link JdbcTemplate} + native SQL
 * because the pgvector {@code vector} type has no JPA mapping; the embedding is passed
 * as a {@code [v1,v2,...]} literal cast to {@code ::vector}. The search query
 * (subphase 5) is added here too.
 */
@Repository
@RequiredArgsConstructor
public class MessageEmbeddingRepository {

    private static final String UPSERT = """
            INSERT INTO message_embeddings (message_id, embedding, model, dimensions, embedded_at)
            VALUES (?, ?::vector, ?, ?, ?)
            ON CONFLICT (message_id) DO UPDATE
               SET embedding   = EXCLUDED.embedding,
                   model       = EXCLUDED.model,
                   dimensions  = EXCLUDED.dimensions,
                   embedded_at = EXCLUDED.embedded_at
            """;

    private static final String NEEDS_EMBEDDING = """
            SELECT m.id FROM messages m
            WHERE m.type = 'TEXT'
              AND m.deleted_at IS NULL
              AND m.content IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM message_embeddings e WHERE e.message_id = m.id)
              AND NOT EXISTS (SELECT 1 FROM outbox_events o
                              WHERE o.aggregate_id = m.id
                                AND o.event_type = ?
                                AND o.status = ?)
            ORDER BY m.created_at
            LIMIT ?
            """;

    private static final String SEARCH = """
            SELECT e.message_id, 1 - (e.embedding <=> ?::vector) AS similarity
            FROM message_embeddings e
            JOIN messages m ON m.id = e.message_id
            WHERE m.deleted_at IS NULL
              AND m.conversation_id IN (
                  SELECT p.conversation_id FROM conversation_participants p WHERE p.user_id = ?
              )
            ORDER BY e.embedding <=> ?::vector
            LIMIT ?
            """;

    private static final String SEARCH_IN_CONVERSATION = """
            SELECT e.message_id, 1 - (e.embedding <=> ?::vector) AS similarity
            FROM message_embeddings e
            JOIN messages m ON m.id = e.message_id
            WHERE m.deleted_at IS NULL
              AND m.conversation_id = ?
            ORDER BY e.embedding <=> ?::vector
            LIMIT ?
            """;

    private static final String DELETE_FOR_DELETED_MESSAGES = """
            DELETE FROM message_embeddings
            WHERE message_id IN (SELECT id FROM messages WHERE deleted_at < ?)
            """;

    private static final String DELETE_FOR_CONVERSATION = """
            DELETE FROM message_embeddings
            WHERE message_id IN (SELECT id FROM messages WHERE conversation_id = ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    /** Insert or replace the embedding for a message. */
    public void upsert(UUID messageId, float[] vector, String model, int dimensions, Instant embeddedAt) {
        jdbcTemplate.update(UPSERT,
                messageId, toVectorLiteral(vector), model, dimensions, Timestamp.from(embeddedAt));
    }

    /**
     * Ids of text messages that have no embedding yet and no embedding request already
     * pending in the outbox — the backfill candidates. Excluding pending events keeps the
     * backfill idempotent across runs (no duplicate enqueues).
     */
    public List<UUID> findMessagesNeedingEmbedding(String eventType, String pendingStatus, int limit) {
        return jdbcTemplate.queryForList(NEEDS_EMBEDDING, UUID.class, eventType, pendingStatus, limit);
    }

    /**
     * Top-k nearest messages to a query vector, scoped to the caller's conversations and
     * excluding deleted messages. Orders by the raw {@code <=>} cosine-distance operator
     * (not the computed alias) so the HNSW index is used; returns {@code similarity = 1 - distance}.
     */
    public List<VectorSearchHit> searchByVector(UUID userId, float[] queryVector, int limit) {
        String literal = toVectorLiteral(queryVector);
        return jdbcTemplate.query(SEARCH,
                (rs, rowNum) -> new VectorSearchHit(
                        rs.getObject("message_id", UUID.class), rs.getDouble("similarity")),
                literal, userId, literal, limit);
    }

    /** Top-k nearest messages within a single conversation (caller must already be authorized). */
    public List<VectorSearchHit> searchByVectorInConversation(UUID conversationId, float[] queryVector, int limit) {
        String literal = toVectorLiteral(queryVector);
        return jdbcTemplate.query(SEARCH_IN_CONVERSATION,
                (rs, rowNum) -> new VectorSearchHit(
                        rs.getObject("message_id", UUID.class), rs.getDouble("similarity")),
                literal, conversationId, literal, limit);
    }

    /** Deletes embeddings for message tombstones older than the cutoff — call before purging the messages (FK). */
    public int deleteForMessagesDeletedBefore(Instant cutoff) {
        return jdbcTemplate.update(DELETE_FOR_DELETED_MESSAGES, Timestamp.from(cutoff));
    }

    /** Deletes embeddings for all of a conversation's messages — call before the group cascade deletes them (FK). */
    public int deleteForConversation(UUID conversationId) {
        return jdbcTemplate.update(DELETE_FOR_CONVERSATION, conversationId);
    }

    /** Renders a float[] as the pgvector text literal {@code [v1,v2,...]}. */
    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
