package com.chatflow.media.storage;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

public interface MediaStorageService {

    StoredMedia store(MultipartFile file, String storageKey);

    /**
     * Stores raw bytes (used for thumbnails and compressed variants).
     */
    StoredMedia storeBytes(byte[] data, String storageKey, String contentType);

    void delete(String storageKey);

    String getUrl(String storageKey);

    /**
     * Time-limited URL for private objects. For S3/MinIO this is a presigned
     * GET URL; the local profile returns its standard URL until Phase 8 adds
     * JWT-signed access. Added here (Phase 5) so the interface is ready for the
     * signed-URL endpoint in Phase 8 — no caller changes when the backend swaps.
     */
    String presignedUrl(String storageKey, Duration ttl);
}