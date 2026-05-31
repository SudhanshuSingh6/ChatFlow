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
 */
@Component
@RequiredArgsConstructor
public class OutboxProcessor {

    private final OutboxEventRepository repository;
    private final OutboxDispatcher dispatcher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID id) {
        repository.lockPending(id).ifPresent(event -> {
            dispatcher.dispatch(event);
            event.markPublished();
        });
    }
}
