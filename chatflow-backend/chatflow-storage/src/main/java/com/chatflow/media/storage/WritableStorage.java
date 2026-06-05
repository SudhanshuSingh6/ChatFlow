package com.chatflow.media.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Write side of the object store: store an uploaded file or raw bytes, and delete. Segregated so
 * a consumer that only writes (upload) or only deletes (cleanup) depends on nothing more.
 */
public interface WritableStorage {

    /** Store an uploaded multipart file under {@code storageKey}. */
    StoredMedia store(MultipartFile file, String storageKey);

    /** Store raw bytes (e.g. a generated thumbnail or compressed variant). */
    StoredMedia storeBytes(byte[] data, String storageKey, String contentType);

    /** Delete the object at {@code storageKey}. Idempotent for a missing object. */
    void delete(String storageKey);
}
