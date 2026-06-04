package com.chatflow.contracts.events;

import java.util.UUID;

/**
 * Emitted by media-service when a thumbnail has been generated and stored; consumed by core to
 * persist the thumbnail URL on the media row and push {@code MEDIA_THUMBNAIL_READY} to the
 * conversation participants (core owns both the media metadata and the WebSocket layer).
 *
 * @param mediaMessageId the media detail row to update
 * @param thumbnailUrl   public URL of the stored thumbnail
 */
public record MediaThumbnailReady(
        UUID mediaMessageId,
        String thumbnailUrl) {

    public static final String TYPE = "media.thumbnail_ready";
}
