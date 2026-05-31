package com.chatflow.media.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * S3 / MinIO connection settings, bound from {@code app.s3.*}.
 * Only created under the {@code s3} profile.
 */
@Data
@Component
@Profile("s3")
@ConfigurationProperties(prefix = "app.s3")
public class S3Properties {

    private String accessKey;
    private String secretKey;
    private String bucket;
    private String region = "us-east-1";

    /**
     * Custom endpoint for MinIO / S3-compatible stores. Leave null/blank to use
     * real AWS S3.
     */
    private String endpoint;

    /** Default TTL for presigned URLs (Phase 8). */
    private long presignedUrlExpiryMinutes = 60;
}
