package com.chatflow.media.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5 — confirms the s3-profile beans build (path-style + endpoint override
 * for MinIO, and the plain AWS path with no endpoint). No network is touched;
 * the SDK clients connect lazily.
 */
class S3ConfigTest {

    private S3Properties props(String endpoint) {
        S3Properties p = new S3Properties();
        p.setAccessKey("minioadmin");
        p.setSecretKey("minioadmin");
        p.setBucket("chatflow");
        p.setRegion("us-east-1");
        p.setEndpoint(endpoint);
        return p;
    }

    @Test
    void buildsClientAndPresignerWithMinioEndpoint() {
        S3Config config = new S3Config(props("http://localhost:9000"));
        try (S3Client client = config.s3Client();
             S3Presigner presigner = config.s3Presigner()) {
            assertThat(client).isNotNull();
            assertThat(presigner).isNotNull();
        }
    }

    @Test
    void buildsClientAndPresignerWithoutEndpoint() {
        S3Config config = new S3Config(props(null));
        try (S3Client client = config.s3Client();
             S3Presigner presigner = config.s3Presigner()) {
            assertThat(client).isNotNull();
            assertThat(presigner).isNotNull();
        }
    }
}
