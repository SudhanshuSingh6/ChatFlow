package com.chatflow.infra.outbox;

/**
 * Where a drained outbox event goes. Two implementations, selected by
 * {@code app.outbox.transport}:
 * <ul>
 *   <li>{@code in-process} (default) — {@link InProcessOutboxPublisher} dispatches to the
 *       in-JVM {@link OutboxDispatcher}, the pre-Kafka behavior.</li>
 *   <li>{@code kafka} — {@link KafkaOutboxPublisher} publishes to the broker; an
 *       {@link OutboxConsumer} elsewhere (today still this app) drives the same dispatcher.</li>
 * </ul>
 * This is the seam the microservices split pivots on: flip the flag and the same events
 * cross a network boundary instead of a method call.
 */
public interface OutboxEventPublisher {

    void publish(OutboxEvent event);
}
