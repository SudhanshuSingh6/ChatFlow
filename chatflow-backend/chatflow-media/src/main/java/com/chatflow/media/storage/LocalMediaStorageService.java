package com.chatflow.media.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Local-disk storage for the worker. For cross-process sharing with core the {@code upload-dir}
 * must be the same directory (same host / shared volume). Active under the default ({@code !s3})
 * profile; use the {@code s3} profile + MinIO for a real shared store.
 */
@Slf4j
@Component
@Profile("!s3")
public class LocalMediaStorageService implements MediaStorageService {

    @Value("${app.media.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.media.base-url:http://localhost:8080/media}")
    private String baseUrl;

    @Override
    public byte[] read(String storageKey) {
        Path target = resolve(storageKey);
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new StorageException("Failed to read " + storageKey, ex);
        }
    }

    @Override
    public StoredMedia storeBytes(byte[] data, String storageKey, String contentType) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            log.debug("Stored bytes storageKey={} size={} bytes", storageKey, data.length);
        } catch (IOException ex) {
            throw new StorageException("Failed to store bytes at " + storageKey, ex);
        }
        return StoredMedia.builder().storageKey(storageKey).publicUrl(buildUrl(storageKey)).build();
    }

    @Override
    public String getUrl(String storageKey) {
        return buildUrl(storageKey);
    }

    @Override
    public String presignedUrl(String storageKey, Duration ttl) {
        return buildUrl(storageKey);
    }

    private Path resolve(String storageKey) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new StorageException("Illegal storage key — path traversal detected");
        }
        return target;
    }

    private String buildUrl(String storageKey) {
        return baseUrl + "/" + storageKey;
    }
}
