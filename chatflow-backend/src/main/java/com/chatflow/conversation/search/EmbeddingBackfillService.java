package com.chatflow.conversation.search;

import com.chatflow.infra.outbox.OutboxEventType;
import com.chatflow.infra.outbox.OutboxStatus;
import com.chatflow.infra.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Backfills embeddings for messages that pre-date the embedding pipeline (or whose
 * embedding was never produced). It does not embed directly — it enqueues
 * {@code MESSAGE_EMBEDDING_REQUESTED} outbox events in batches, so the same worker
 * path handles new and historical messages identically.
 *
 * <p>Idempotent and self-terminating: the candidate query excludes messages that
 * already have an embedding or a pending request, so once history is caught up each
 * run is a no-op (and the job can be disabled via config).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingBackfillService {

    private final MessageEmbeddingRepository embeddingRepository;
    private final OutboxWriter outboxWriter;

    @Value("${app.ai.embedding.backfill.enabled:true}")
    private boolean enabled;

    @Value("${app.ai.embedding.backfill.batch-size:200}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.ai.embedding.backfill.interval-ms:60000}")
    @Transactional
    public void backfill() {
        if (!enabled) {
            return;
        }
        List<UUID> ids = embeddingRepository.findMessagesNeedingEmbedding(
                OutboxEventType.MESSAGE_EMBEDDING_REQUESTED, OutboxStatus.PENDING.name(), batchSize);
        if (ids.isEmpty()) {
            return;
        }
        for (UUID id : ids) {
            outboxWriter.write("message", id,
                    OutboxEventType.MESSAGE_EMBEDDING_REQUESTED, new EmbeddingRequested(id));
        }
        log.info("Embedding backfill enqueued {} messages", ids.size());
    }
}
