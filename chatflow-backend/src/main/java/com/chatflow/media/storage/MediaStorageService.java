package com.chatflow.media.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface MediaStorageService {

    StoredMedia store(MultipartFile file, String storageKey);

    /**
     * Stores raw bytes (used for thumbnails and compressed variants).
     */
    StoredMedia storeBytes(byte[] data, String storageKey, String contentType);

    void delete(String storageKey);

    String getUrl(String storageKey);
}