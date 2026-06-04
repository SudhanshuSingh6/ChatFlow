package com.chatflow.media.processing;

/**
 * The subset of core's outbox wire envelope the media worker cares about. Core publishes every
 * outbox event to the shared topic as {@code {id, aggregateType, aggregateId, eventType,
 * payload}}; the worker routes on {@code eventType} and re-parses the inner {@code payload}.
 */
public record OutboxEnvelope(String eventType, String payload) {
}
