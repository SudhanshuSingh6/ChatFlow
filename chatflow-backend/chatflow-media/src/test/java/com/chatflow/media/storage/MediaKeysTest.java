package com.chatflow.media.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MediaKeysTest {

    @Test
    void derivesThumbnailKeyFromStorageKey() {
        assertThat(MediaKeys.thumbnailKey("image/2026/05/abc.jpg"))
                .isEqualTo("thumbnails/image/2026/05/abc_thumb.jpg");
    }

    @Test
    void handlesKeyWithoutExtension() {
        assertThat(MediaKeys.thumbnailKey("video/2026/05/abc"))
                .isEqualTo("thumbnails/video/2026/05/abc_thumb.jpg");
    }
}
