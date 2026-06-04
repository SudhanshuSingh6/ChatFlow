package com.chatflow.media.storage;

import java.time.Duration;

/**
 * Object-store abstraction for the media worker. Unlike core's copy this is byte-oriented:
 * the worker reads the stored original and writes the generated thumbnail; it never handles
 * a multipart upload.
 */
public interface MediaStorageService {

    /** Read a stored object's bytes (the original, to thumbnail it). */
    byte[] read(String storageKey);

    /** Store raw bytes (the generated thumbnail). */
    StoredMedia storeBytes(byte[] data, String storageKey, String contentType);

    String getUrl(String storageKey);

    String presignedUrl(String storageKey, Duration ttl);
}
