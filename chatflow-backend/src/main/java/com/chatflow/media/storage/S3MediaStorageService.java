package com.chatflow.media.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;

/**
 * Phase 5 — S3 / MinIO storage. Same code for both: MinIO is S3-compatible and
 * differs only by endpoint URL (configured in {@link S3Config}).
 *
 * <p>The bucket is private. {@link #getUrl} returns the object URL for storage
 * in the message record, but the object is only retrievable through a
 * {@link #presignedUrl} (Phase 8). Active under the {@code s3} profile.
 */
@Slf4j
@Component
@Profile("s3")
@RequiredArgsConstructor
public class S3MediaStorageService implements MediaStorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    @Override
    public StoredMedia store(MultipartFile file, String storageKey) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();
            s3Client.putObject(request,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.debug("Stored object bucket={} key={} size={}",
                    properties.getBucket(), storageKey, file.getSize());
        } catch (IOException | S3Exception ex) {
            throw new StorageException("Failed to store object at " + storageKey, ex);
        }
        return stored(storageKey);
    }

    @Override
    public StoredMedia storeBytes(byte[] data, String storageKey, String contentType) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .contentType(contentType)
                    .contentLength((long) data.length)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(data));
            log.debug("Stored bytes bucket={} key={} size={}",
                    properties.getBucket(), storageKey, data.length);
        } catch (S3Exception ex) {
            throw new StorageException("Failed to store bytes at " + storageKey, ex);
        }
        return stored(storageKey);
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build());
            log.debug("Deleted object bucket={} key={}", properties.getBucket(), storageKey);
        } catch (S3Exception ex) {
            // deleteObject is idempotent for missing keys; a genuine S3 error
            // must surface so the cleanup job (Phase 7) retries.
            throw new StorageException("Failed to delete object " + storageKey, ex);
        }
    }

    @Override
    public String getUrl(String storageKey) {
        // Object URL for record-keeping; bucket is private so this is not
        // directly retrievable — use presignedUrl for access.
        return s3Client.utilities()
                .getUrl(b -> b.bucket(properties.getBucket()).key(storageKey))
                .toExternalForm();
    }

    @Override
    public String presignedUrl(String storageKey, Duration ttl) {
        Duration validity = (ttl != null && !ttl.isZero() && !ttl.isNegative())
                ? ttl
                : Duration.ofMinutes(properties.getPresignedUrlExpiryMinutes());

        GetObjectRequest getObject = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(storageKey)
                .build();

        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(getObject)
                .build();

        return s3Presigner.presignGetObject(presign).url().toExternalForm();
    }

    private StoredMedia stored(String storageKey) {
        return StoredMedia.builder()
                .storageKey(storageKey)
                .publicUrl(getUrl(storageKey))
                .build();
    }
}
