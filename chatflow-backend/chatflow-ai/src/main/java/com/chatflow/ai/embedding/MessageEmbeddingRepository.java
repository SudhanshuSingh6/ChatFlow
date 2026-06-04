package com.chatflow.ai.embedding;

import com.chatflow.contracts.dto.EmbeddingSearchHit;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for ai-service's own {@code message_embeddings} table. Uses
 * {@link JdbcTemplate} + native SQL because the pgvector {@code vector} type has no JPA
 * mapping; the embedding is passed as a {@code [v1,v2,...]} literal cast to {@code ::vector}.
 *
 * <p>Unlike the old core-resident repository, this table is <b>denormalized</b>: it carries
 * the message snippet, sender, sequence, and conversation scope inline, so search needs no
 * cross-database JOIN to core's {@code messages}/{@code users} tables. That is the whole
 * point of the split (docs/microservices-migration.md §4b).
 */
@Repository
@RequiredArgsConstructor
public class MessageEmbeddingRepository {

    private static final String UPSERT = """
            INSERT INTO message_embeddings (message_id, conversation_id, sender_id, sender_name,
                                            sequence_number, content_snippet, embedding, model,
                                            dimensions, message_created_at, embedded_at)
            VALUES (?, ?, ?, ?, ?, ?, ?::vector, ?, ?, ?, ?)
            ON CONFLICT (message_id) DO UPDATE
               SET conversation_id   = EXCLUDED.conversation_id,
                   sender_id         = EXCLUDED.sender_id,
                   sender_name       = EXCLUDED.sender_name,
                   sequence_number   = EXCLUDED.sequence_number,
                   content_snippet   = EXCLUDED.content_snippet,
                   embedding         = EXCLUDED.embedding,
                   model             = EXCLUDED.model,
                   dimensions        = EXCLUDED.dimensions,
                   message_created_at = EXCLUDED.message_created_at,
                   embedded_at       = EXCLUDED.embedded_at
            """;

    // Self-contained: no JOIN to core tables. Orders by the raw <=> operator so the HNSW
    // index is used; returns similarity = 1 - cosine distance.
    private static final String SEARCH_IN_CONVERSATION = """
            SELECT message_id, sender_id, sender_name, sequence_number, content_snippet,
                   1 - (embedding <=> ?::vector) AS similarity
            FROM message_embeddings
            WHERE conversation_id = ?
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    /** Insert or replace a message's embedding row, including its denormalized snippet/metadata. */
    public void upsert(MessageEmbeddingRow row) {
        jdbcTemplate.update(UPSERT,
                row.messageId(), row.conversationId(), row.senderId(), row.senderName(),
                row.sequenceNumber(), row.contentSnippet(), toVectorLiteral(row.vector()),
                row.model(), row.dimensions(),
                Timestamp.from(row.messageCreatedAt()), Timestamp.from(row.embeddedAt()));
    }

    /** Top-k nearest messages within a single conversation (caller must already be authorized). */
    public List<VectorSearchHit> searchByVectorInConversation(UUID conversationId, float[] queryVector, int limit) {
        String literal = toVectorLiteral(queryVector);
        return jdbcTemplate.query(SEARCH_IN_CONVERSATION,
                (rs, rowNum) -> new VectorSearchHit(
                        rs.getObject("message_id", UUID.class),
                        rs.getObject("sender_id", UUID.class),
                        rs.getString("sender_name"),
                        rs.getLong("sequence_number"),
                        rs.getString("content_snippet"),
                        rs.getDouble("similarity")),
                literal, conversationId, literal, limit);
    }

    /**
     * Top-k nearest messages across a set of conversations — the scope core supplies for a
     * user's global semantic search. Returns lean (id, similarity) hits; core hydrates the
     * messages from its own store.
     */
    public List<EmbeddingSearchHit> searchByVectorInConversations(List<UUID> conversationIds,
                                                                  float[] queryVector, int limit) {
        if (conversationIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(conversationIds.size(), "?"));
        String sql = "SELECT message_id, 1 - (embedding <=> ?::vector) AS similarity "
                + "FROM message_embeddings WHERE conversation_id IN (" + placeholders + ") "
                + "ORDER BY embedding <=> ?::vector LIMIT ?";

        String literal = toVectorLiteral(queryVector);
        Object[] args = new Object[conversationIds.size() + 3];
        args[0] = literal;
        for (int i = 0; i < conversationIds.size(); i++) {
            args[i + 1] = conversationIds.get(i);
        }
        args[conversationIds.size() + 1] = literal;
        args[conversationIds.size() + 2] = limit;

        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new EmbeddingSearchHit(
                        rs.getObject("message_id", UUID.class), rs.getDouble("similarity")),
                args);
    }

    /** Evict all embeddings for a conversation (when core purges it). Idempotent. */
    public int deleteByConversationId(UUID conversationId) {
        return jdbcTemplate.update(
                "DELETE FROM message_embeddings WHERE conversation_id = ?", conversationId);
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
