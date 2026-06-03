package com.chatflow.conversation.search;

import com.chatflow.infra.outbox.OutboxEvent;
import com.chatflow.infra.outbox.OutboxEventHandler;
import com.chatflow.infra.outbox.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox handler for {@code MESSAGE_EMBEDDING_REQUESTED}: embeds the message and stores its
 * vector. Lives with the embedding code in {@code conversation/search} so {@code infra/outbox}
 * has no dependency on it.
 */
@Component
@RequiredArgsConstructor
public class MessageEmbeddingOutboxHandler implements OutboxEventHandler {

    private final MessageEmbeddingWorker worker;

    @Override
    public boolean supports(String eventType) {
        return OutboxEventType.MESSAGE_EMBEDDING_REQUESTED.equals(eventType);
    }

    @Override
    public void handle(OutboxEvent event) {
        worker.embed(event.getAggregateId());
    }
}
