package com.chatflow.media.storage;

import java.time.Duration;

/**
 * URL side of the object store. Segregated so a consumer that only mints access URLs (e.g. the
 * signed-URL endpoint) depends on nothing more.
 */
public interface UrlStorage {

    /** Object URL for record-keeping. For a private S3 bucket this is not directly retrievable. */
    String getUrl(String storageKey);

    /**
     * Time-limited URL for private objects. For S3/MinIO this is a presigned GET URL; the local
     * profile returns its standard URL until local JWT signing is added.
     */
    String presignedUrl(String storageKey, Duration ttl);
}
