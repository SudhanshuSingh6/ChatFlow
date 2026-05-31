package com.chatflow.media.processing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.UUID;

/**
 * Published by {@link ThumbnailService} once a thumbnail has been generated and
 * stored. Consumed by {@code ThumbnailEventListener}, which persists the URL on
 * the {@code MediaMessage} and pushes a {@code MEDIA_THUMBNAIL_READY} frame to
 * participants.
 */
@Getter
@RequiredArgsConstructor
public class ThumbnailGeneratedEvent {

    private final UUID mediaMessageId;
    private final String thumbnailUrl;
}
