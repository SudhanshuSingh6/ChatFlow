package com.chatflow.media.storage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;

/**
 * Local-disk storage. For cross-process sharing between core (writes originals) and the media
 * worker (reads them) the {@code upload-dir} must be the same directory (same host / shared
 * volume). Active under the default ({@code !s3}) profile; use the {@code s3} profile + MinIO for
 * a real shared store.
 */
@Slf4j
@Component
@Profile("!s3")
public class LocalMediaStorageService implements MediaStorageService {

    @Value("${app.media.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.media.base-url:http://localhost:8080/media}")
    private String baseUrl;

    /**
     * Optional — present only where a {@link MediaUrlSigner} bean is defined (core). The
     * chatflow-media worker scans this class but never mints URLs, so the signer may be absent.
     */
    private final ObjectProvider<MediaUrlSigner> urlSignerProvider;

    public LocalMediaStorageService(ObjectProvider<MediaUrlSigner> urlSignerProvider) {
        this.urlSignerProvider = urlSignerProvider;
    }

    @PostConstruct
    public void init() throws IOException {
        Path root = Path.of(uploadDir);
        Files.createDirectories(root);
        log.info("LocalMediaStorageService initialised — uploadDir={}", root.toAbsolutePath());
    }

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
        Path target = resolve(storageKey);
        try {
            boolean deleted = Files.deleteIfExists(target);
            log.debug("Deleted storageKey={} existed={}", storageKey, deleted);
        } catch (IOException ex) {
            // A missing file is success (idempotent); a real I/O error must
            // surface so the cleanup job retries instead of orphaning.
            throw new StorageException("Failed to delete " + storageKey, ex);
        }
    }

    @Override
    public String getUrl(String storageKey) {
        return buildUrl(storageKey);
    }

    @Override
    public String presignedUrl(String storageKey, Duration ttl) {
        MediaUrlSigner signer = urlSignerProvider.getIfAvailable();
        if (signer == null) {
            // No signer in this context (e.g. the media worker, which never mints URLs).
            return buildUrl(storageKey);
        }
        long exp = Instant.now().plus(ttl).getEpochSecond();
        return buildUrl(storageKey) + "?exp=" + exp + "&t=" + signer.sign(storageKey, exp);
    }

    // --- helpers ---

    private Path resolveAndCreateParents(String storageKey) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException ex) {
            throw new StorageException("Failed to create directories for " + storageKey, ex);
        }
        return target;
    }

    /** Resolve a storage key against the upload root, rejecting path traversal. */
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
