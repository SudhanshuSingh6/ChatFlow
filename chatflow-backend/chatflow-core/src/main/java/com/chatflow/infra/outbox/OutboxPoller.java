package com.chatflow.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Drains pending {@code outbox_events} on a fixed interval. Each event is handled
 * in its own transaction by {@link OutboxProcessor}; failures are logged and left
 * PENDING for the next sweep (at-least-once).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final OutboxProcessor processor;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void poll() {
        List<UUID> ids = repository.findPendingIds(PageRequest.of(0, BATCH_SIZE));
        if (ids.isEmpty()) {
            return;
        }
        int published = 0;
        for (UUID id : ids) {
            try {
                processor.process(id);
                published++;
            } catch (Exception e) {
                log.error("Outbox dispatch failed for event {}; will retry", id, e);
            }
        }
        log.debug("Outbox poll processed {}/{} pending events", published, ids.size());
    }
}
