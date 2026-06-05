package com.chatflow.media.storage;

/**
 * Read side of the object store. Segregated so a consumer that only fetches bytes (e.g. the
 * media worker reading an original to thumbnail it) depends on nothing more.
 */
public interface ReadableStorage {

    /** Read a stored object's bytes. */
    byte[] read(String storageKey);
}
