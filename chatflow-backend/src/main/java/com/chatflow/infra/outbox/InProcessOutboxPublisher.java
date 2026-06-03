package com.chatflow.infra.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default transport: hand the event straight to the in-JVM {@link OutboxDispatcher}.
 * Active unless {@code app.outbox.transport=kafka}, so existing behavior is unchanged
 * until the broker is deliberately switched on.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "in-process", matchIfMissing = true)
public class InProcessOutboxPublisher implements OutboxEventPublisher {

    private final OutboxDispatcher dispatcher;

    @Override
    public void publish(OutboxEvent event) {
        dispatcher.dispatch(event);
    }
}
