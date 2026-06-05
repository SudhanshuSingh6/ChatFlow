package com.chatflow.media.storage;

/**
 * Combined object-store contract — the full capability set. Implementations provide all of it
 * (there is one bean per profile), but consumers should depend on the narrowest interface they
 * use ({@link ReadableStorage}, {@link WritableStorage}, {@link UrlStorage}); inject this façade
 * only when a consumer genuinely spans multiple capabilities (e.g. read + write).
 */
public interface MediaStorageService extends ReadableStorage, WritableStorage, UrlStorage {
}
