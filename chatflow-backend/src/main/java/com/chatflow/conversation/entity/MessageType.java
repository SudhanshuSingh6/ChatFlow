package com.chatflow.conversation.entity;

/**
 * Kind of message in the unified model.
 *
 * <p>Note: distinct from {@code com.chatflow.media.entity.MessageType}, which
 * classifies the media payload (IMAGE/VIDEO/AUDIO/FILE). This one classifies the
 * message itself: a plain text message, a media attachment, or a system event.
 */
public enum MessageType {
    /** Plain text content. */
    TEXT,
    /** Carries a media attachment (detail row in media_messages). */
    MEDIA,
    /** Server-generated event, e.g. "Alice added Bob", "Carol left". */
    SYSTEM
}
