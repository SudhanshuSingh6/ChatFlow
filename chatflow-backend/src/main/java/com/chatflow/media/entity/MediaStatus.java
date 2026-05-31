package com.chatflow.media.entity;

public enum MediaStatus {
    UPLOADING,
    PROCESSING,
    READY,
    PROCESSING_FAILED,
    /** Phase 7 — logically deleted; storage object still awaiting purge. */
    PENDING_DELETION,
    /** Phase 7 — storage object purged; row retained as a tombstone. */
    DELETED
}