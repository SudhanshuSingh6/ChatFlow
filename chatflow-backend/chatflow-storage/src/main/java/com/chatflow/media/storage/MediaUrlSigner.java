package com.chatflow.media.storage;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signer for local media URLs. Bakes the storage key + expiry into a token so a
 * {@code /media/**} URL is self-authorizing (no bearer header) and time-limited, mirroring the
 * S3 presigned-URL model.
 *
 * <p>Deliberately a plain class (not a {@code @Component}) wired by an {@code @Bean} in core only,
 * so the chatflow-media worker — which scans this package but never mints URLs — does not require
 * the signing secret. Uses a media-specific secret, kept separate from the JWT signing secret.
 */
public class MediaUrlSigner {

    private final byte[] secret;

    public MediaUrlSigner(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Hex HMAC-SHA256 over {@code storageKey + ":" + expEpochSeconds}. */
    public String sign(String storageKey, long expEpochSeconds) {
        return HexFormat.of().formatHex(hmac(storageKey + ":" + expEpochSeconds));
    }

    /** True only if the signature matches and the expiry is still in the future. */
    public boolean verify(String storageKey, long expEpochSeconds, String sig) {
        if (sig == null || expEpochSeconds < Instant.now().getEpochSecond()) {
            return false;
        }
        byte[] expected = sign(storageKey, expEpochSeconds).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, sig.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to compute media URL signature", ex);
        }
    }
}
