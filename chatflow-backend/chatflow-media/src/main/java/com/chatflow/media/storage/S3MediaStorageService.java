package com.chatflow.media.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;

/**
 * S3 / MinIO storage for the worker — reads originals, writes thumbnails. Same bucket core
 * writes to (configured in {@link S3Config}). Active under the {@code s3} profile.
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
    public byte[] read(String storageKey) {
        try {
            return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(storageKey)
                    .build()).asByteArray();
        } catch (S3Exception ex) {
            throw new StorageException("Failed to read object " + storageKey, ex);
        }
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
            log.debug("Stored bytes bucket={} key={} size={}", properties.getBucket(), storageKey, data.length);
        } catch (S3Exception ex) {
            throw new StorageException("Failed to store bytes at " + storageKey, ex);
        }
        return stored(storageKey);
    }

    @Override
    public String getUrl(String storageKey) {
        return s3Client.utilities()
                .getUrl(b -> b.bucket(properties.getBucket()).key(storageKey))
                .toExternalForm();
    }

    @Override
    public String presignedUrl(String storageKey, Duration ttl) {
        Duration validity = (ttl != null && !ttl.isZero() && !ttl.isNegative())
                ? ttl
                : Duration.ofMinutes(properties.getPresignedUrlExpiryMinutes());
        GetObjectPresignRequest presign = GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.getBucket()).key(storageKey).build())
                .build();
        return s3Presigner.presignGetObject(presign).url().toExternalForm();
    }

    private StoredMedia stored(String storageKey) {
        return StoredMedia.builder().storageKey(storageKey).publicUrl(getUrl(storageKey)).build();
    }
}
