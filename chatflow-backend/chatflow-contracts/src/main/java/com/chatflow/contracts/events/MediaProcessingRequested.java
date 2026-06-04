package com.chatflow.contracts.events;

import java.util.UUID;

/**
 * Emitted by core after a media upload is stored; consumed by media-service to generate a
 * thumbnail. Carries only the storage key (not the bytes) — the worker reads the original from
 * the shared object store, so large files never transit Kafka.
 *
 * @param mediaMessageId the media detail row (core-owned); echoed back on completion
 * @param storageKey     key of the stored original in the shared bucket
 * @param messageType    IMAGE | VIDEO | AUDIO | FILE (only IMAGE/VIDEO produce thumbnails)
 * @param mimeType       detected MIME, used to pick the video container extension for ffmpeg
 */
public record MediaProcessingRequested(
        UUID mediaMessageId,
        String storageKey,
        String messageType,
        String mimeType) {

    public static final String TYPE = "media.processing_requested";
}
