package com.chatflow.media.storage;

/**
 * Single source of truth for deriving related storage keys from a media object's
 * primary {@code storageKey}. Used by thumbnail generation (Phase 6) and cleanup
 * (Phase 7) so both agree on where the thumbnail lives.
 */
public final class MediaKeys {

    private MediaKeys() {
    }

    /**
     * Thumbnail key for a given media storage key, e.g.
     * {@code image/2026/05/uuid.jpg} → {@code thumbnails/image/2026/05/uuid_thumb.jpg}.
     */
    public static String thumbnailKey(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        int dot = storageKey.lastIndexOf('.');
        String base = (dot > slash) ? storageKey.substring(0, dot) : storageKey;
        return "thumbnails/" + base + "_thumb.jpg";
    }
}
