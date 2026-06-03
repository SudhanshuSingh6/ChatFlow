package com.chatflow.infra.outbox;

import java.util.UUID;

/**
 * Wire form of an {@link OutboxEvent} on the broker. Deliberately a flat, provider-neutral
 * record serialized as plain JSON (no Jackson type headers) so a future polyglot consumer
 * service can read it without sharing this class. Carries exactly what
 * {@link OutboxEventHandler}s need — none of them touch the persisted entity.
 */
public record OutboxEventMessage(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload) {

    public static OutboxEventMessage from(OutboxEvent event) {
        return new OutboxEventMessage(
                event.getId(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload());
    }

    /**
     * Rebuild a transient {@link OutboxEvent} for the dispatcher. Not persisted — handlers
     * act on the payload/ids only, so a detached instance is sufficient.
     */
    public OutboxEvent toOutboxEvent() {
        return OutboxEvent.builder()
                .id(id)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .build();
    }
}
