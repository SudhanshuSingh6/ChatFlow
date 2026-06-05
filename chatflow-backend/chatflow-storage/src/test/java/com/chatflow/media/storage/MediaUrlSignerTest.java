package com.chatflow.media.storage;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MediaUrlSignerTest {

    private final MediaUrlSigner signer = new MediaUrlSigner("test-media-signing-secret-at-least-32-chars");

    @Test
    void signThenVerifyRoundTrips() {
        String key = "image/2026/05/x.jpg";
        long exp = Instant.now().getEpochSecond() + 3600;

        String sig = signer.sign(key, exp);

        assertThat(signer.verify(key, exp, sig)).isTrue();
    }

    @Test
    void tamperedSignatureFails() {
        String key = "image/2026/05/x.jpg";
        long exp = Instant.now().getEpochSecond() + 3600;
        String sig = signer.sign(key, exp);

        assertThat(signer.verify(key, exp, sig + "00")).isFalse();
        assertThat(signer.verify("image/2026/05/other.jpg", exp, sig)).isFalse();
        assertThat(signer.verify(key, exp + 1, sig)).isFalse();
    }

    @Test
    void expiredTokenFails() {
        String key = "image/2026/05/x.jpg";
        long exp = Instant.now().getEpochSecond() - 10;
        String sig = signer.sign(key, exp);

        assertThat(signer.verify(key, exp, sig)).isFalse();
    }

    @Test
    void nullSignatureFails() {
        long exp = Instant.now().getEpochSecond() + 3600;

        assertThat(signer.verify("image/2026/05/x.jpg", exp, null)).isFalse();
    }
}
