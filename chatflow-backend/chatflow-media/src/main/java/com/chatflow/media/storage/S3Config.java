package com.chatflow.media.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Wires the AWS SDK v2 {@link S3Client} / {@link S3Presigner} for the media worker. MinIO is
 * S3-compatible via an endpoint override with path-style access. Active under the {@code s3}
 * profile; the default profile uses {@link LocalMediaStorageService}.
 */
@Configuration
@Profile("s3")
@RequiredArgsConstructor
public class S3Config {

    private final S3Properties properties;

    @Bean(destroyMethod = "close")
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials())
                .applyMutation(b -> {
                    if (hasEndpoint()) {
                        b.endpointOverride(URI.create(properties.getEndpoint()));
                    }
                })
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(credentials())
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build());
        if (hasEndpoint()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
    }

    private boolean hasEndpoint() {
        return properties.getEndpoint() != null && !properties.getEndpoint().isBlank();
    }
}
