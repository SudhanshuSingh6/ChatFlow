package com.chatflow.infra.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The single consumer of drained outbox events. Routes each event to the first
 * registered {@link OutboxEventHandler} that {@link OutboxEventHandler#supports supports}
 * its type. Handlers live in their owning feature, so this class — and all of
 * {@code infra/outbox} — stays free of feature dependencies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxDispatcher {

    private final List<OutboxEventHandler> handlers;

    public void dispatch(OutboxEvent event) {
        for (OutboxEventHandler handler : handlers) {
            if (handler.supports(event.getEventType())) {
                handler.handle(event);
                return;
            }
        }
        log.warn("No outbox handler for event type '{}' (id={})",
                event.getEventType(), event.getId());
    }
}
