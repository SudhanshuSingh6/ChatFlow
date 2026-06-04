package com.chatflow.infra.outbox;

/**
 * Domain-side handler for a drained outbox event. Implementations live in their owning
 * feature (e.g. notifications, message embeddings) and register as beans; {@link
 * OutboxDispatcher} routes each event to the first handler whose {@link #supports} matches.
 *
 * <p>This keeps {@code infra/outbox} free of any dependency on feature packages — the
 * dependency points inward (feature → infra), never the reverse.
 */
public interface OutboxEventHandler {

    /** Whether this handler processes the given {@link OutboxEventType} value. */
    boolean supports(String eventType);

    /** Apply the durable side effect for the event. Runs inside the per-event transaction. */
    void handle(OutboxEvent event);
}
