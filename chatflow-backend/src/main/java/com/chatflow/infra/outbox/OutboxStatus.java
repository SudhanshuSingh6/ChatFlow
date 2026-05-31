package com.chatflow.infra.outbox;

/** Lifecycle of a transactional outbox row. */
public enum OutboxStatus {
    PENDING,
    PUBLISHED
}
