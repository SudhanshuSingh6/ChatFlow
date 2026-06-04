package com.chatflow.ai.embedding;

/**
 * The subset of core's outbox wire envelope ai-service cares about. Core publishes every
 * outbox event to the shared topic as {@code {id, aggregateType, aggregateId, eventType,
 * payload}}; ai-service routes on {@code eventType} and re-parses the inner {@code payload}
 * JSON for the events it handles.
 */
public record OutboxEnvelope(java.util.UUID id, String eventType, String payload) {
}
