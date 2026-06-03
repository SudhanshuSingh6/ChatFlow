package com.chatflow.infra.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Processes a single outbox event in its own transaction, so one poisoned event
 * never rolls back a whole batch and successfully published events commit
 * independently. The row is re-locked with SKIP LOCKED to stay safe under
 * concurrent pollers / multiple instances.
 *
 * <p>Publishing goes through {@link OutboxEventPublisher} — in-process dispatch or Kafka,
 * per {@code app.outbox.transport}. Either way the row is only marked {@code PUBLISHED} if
 * publish returns normally; a failure rolls the transaction back and leaves it
 * {@code PENDING} for the next sweep.
 */
@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxEventRepository repository;
    private final OutboxEventPublisher publisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID id) {
        repository.lockPending(id).ifPresent(event -> {
            publisher.publish(event);
            event.markPublished();
        });
    }
}
