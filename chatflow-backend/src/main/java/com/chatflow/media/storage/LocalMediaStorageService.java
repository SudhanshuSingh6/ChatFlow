package com.chatflow.media.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Component
@Profile("!s3")
public class LocalMediaStorageService implements MediaStorageService {

    @Value("${app.media.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.media.base-url:http://localhost:8080/media}")
    private String baseUrl;

    @PostConstruct
    public void init() throws IOException {
        Path root = Path.of(uploadDir);
        Files.createDirectories(root);
        log.info("LocalMediaStorageService initialised — uploadDir={}", root.toAbsolutePath());
    }

    @Override
    public StoredMedia store(MultipartFile file, String storageKey) {
        Path target = resolveAndCreateParents(storageKey);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Stored file storageKey={} size={} bytes", storageKey, file.getSize());
        } catch (IOException ex) {
            throw new StorageException("Failed to store file at " + storageKey, ex);
        }
        return StoredMedia.builder()
                .storageKey(storageKey)
                .publicUrl(buildUrl(storageKey))
                .build();
    }

    @Override
    public StoredMedia storeBytes(byte[] data, String storageKey, String contentType) {
        Path target = resolveAndCreateParents(storageKey);
        try {
            Files.write(target, data);
            log.debug("Stored bytes storageKey={} size={} bytes", storageKey, data.length);
        } catch (IOException ex) {
            throw new StorageException("Failed to store bytes at " + storageKey, ex);
        }
        return StoredMedia.builder()
                .storageKey(storageKey)
                .publicUrl(buildUrl(storageKey))
                .build();
    }

    @Override
    public void delete(String storageKey) {
        Path target = Path.of(uploadDir).resolve(storageKey).normalize();
        ensureWithinUploadDir(target);
        try {
            boolean deleted = Files.deleteIfExists(target);
            log.debug("Deleted storageKey={} existed={}", storageKey, deleted);
        } catch (IOException ex) {
            // Log and continue — caller decides whether to retry
            log.warn("Failed to delete storageKey={}: {}", storageKey, ex.getMessage());
        }
    }

    @Override
    public String getUrl(String storageKey) {
        return buildUrl(storageKey);
    }

    // --- helpers ---

    private Path resolveAndCreateParents(String storageKey) {
        Path target = Path.of(uploadDir).resolve(storageKey).normalize();
        ensureWithinUploadDir(target);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException ex) {
            throw new StorageException("Failed to create directories for " + storageKey, ex);
        }
        return target;
    }

    private void ensureWithinUploadDir(Path resolved) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        if (!resolved.toAbsolutePath().normalize().startsWith(root)) {
            throw new StorageException("Illegal storage key — path traversal detected");
        }
    }

    private String buildUrl(String storageKey) {
        return baseUrl + "/" + storageKey;
    }
}