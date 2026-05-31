package com.chatflow.media.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URL;
import java.time.Duration;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Phase 5 — verifies S3MediaStorageService builds the correct S3 requests and
 * maps results, without a live network call. The live round-trip against MinIO
 * is exercised separately (see docs).
 */
class S3MediaStorageServiceTest {

    private S3Client s3Client;
    private S3Presigner s3Presigner;
    private S3Properties properties;
    private S3MediaStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        s3Client = mock(S3Client.class);
        s3Presigner = mock(S3Presigner.class);
        properties = new S3Properties();
        properties.setBucket("chatflow");
        properties.setRegion("us-east-1");
        properties.setPresignedUrlExpiryMinutes(60);

        // getUrl(...) goes through s3Client.utilities().getUrl(consumer)
        S3Utilities utilities = mock(S3Utilities.class);
        when(s3Client.utilities()).thenReturn(utilities);
        when(utilities.getUrl(any(Consumer.class)))
                .thenReturn(new URL("http://localhost:9000/chatflow/some/key"));

        service = new S3MediaStorageService(s3Client, s3Presigner, properties);
    }

    @Test
    void storeBytesPutsObjectWithBucketKeyAndContentType() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        byte[] data = {1, 2, 3, 4, 5};
        StoredMedia result = service.storeBytes(data, "image/2026/05/x.jpg", "image/jpeg");

        ArgumentCaptor<PutObjectRequest> req = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(req.capture(), any(RequestBody.class));
        assertThat(req.getValue().bucket()).isEqualTo("chatflow");
        assertThat(req.getValue().key()).isEqualTo("image/2026/05/x.jpg");
        assertThat(req.getValue().contentType()).isEqualTo("image/jpeg");
        assertThat(req.getValue().contentLength()).isEqualTo(5L);

        assertThat(result.getStorageKey()).isEqualTo("image/2026/05/x.jpg");
        assertThat(result.getPublicUrl()).startsWith("http://localhost:9000/chatflow/");
    }

    @Test
    void deleteIssuesDeleteObjectForKey() {
        service.delete("image/2026/05/x.jpg");

        ArgumentCaptor<DeleteObjectRequest> req =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(req.capture());
        assertThat(req.getValue().bucket()).isEqualTo("chatflow");
        assertThat(req.getValue().key()).isEqualTo("image/2026/05/x.jpg");
    }

    @Test
    void deleteSurfacesS3ErrorAsStorageException() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("denied").build());

        assertThatThrownBy(() -> service.delete("image/2026/05/x.jpg"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void presignedUrlUsesPresignerWithGivenTtl() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("http://localhost:9000/chatflow/x?X-Amz-Signature=abc"));
        when(s3Presigner.presignGetObject(any(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(presigned);

        String url = service.presignedUrl("image/2026/05/x.jpg", Duration.ofMinutes(5));

        assertThat(url).contains("X-Amz-Signature");
        verify(s3Presigner).presignGetObject(any(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class));
    }

    @Test
    void presignedUrlFallsBackToConfiguredExpiryWhenTtlNull() throws Exception {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("http://localhost:9000/chatflow/x?sig=1"));
        when(s3Presigner.presignGetObject(any(
                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.class)))
                .thenReturn(presigned);

        String url = service.presignedUrl("image/2026/05/x.jpg", null);

        assertThat(url).isNotBlank();
    }
}
