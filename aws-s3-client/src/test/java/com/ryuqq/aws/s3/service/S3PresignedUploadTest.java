package com.ryuqq.aws.s3.service;

import com.ryuqq.aws.s3.properties.S3Properties;
import com.ryuqq.aws.s3.service.impl.DefaultS3Service;
import com.ryuqq.aws.s3.types.S3StorageClass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S3 Presigned URL 업로드 기능 테스트
 *
 * 한국어 설명:
 * 새로 추가된 Presigned URL 업로드 기능들을 테스트합니다.
 *
 * 테스트 범위:
 * - generatePresignedPutUrl() 메서드들
 * - URL 형식 및 파라미터 검증
 * - 만료 시간 설정 검증
 */
@ExtendWith(MockitoExtension.class)
class S3PresignedUploadTest {

    @Mock
    private S3AsyncClient s3AsyncClient;

    @Mock
    private S3TransferManager transferManager;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private S3Properties s3Properties;

    private S3Service s3Service;

    private static final String TEST_BUCKET = "test-presigned-upload-bucket";

    @BeforeEach
    void setUp() throws Exception {
        s3Service = new DefaultS3Service(s3AsyncClient, transferManager, s3Presigner, s3Properties);

        // Mock S3Properties default expiry
        lenient().when(s3Properties.presignedUrlExpiry()).thenReturn(Duration.ofMinutes(15));

        // Mock presigned URL generation
        URL mockUrl = new URL("https://test-bucket.s3.amazonaws.com/test-key?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=test&X-Amz-Date=20240101T000000Z&X-Amz-Expires=900&X-Amz-Signature=test&X-Amz-SignedHeaders=content-type%3Bhost&x-amz-storage-class=STANDARD");
        PresignedPutObjectRequest mockPresignedRequest = mock(PresignedPutObjectRequest.class);

        lenient().when(mockPresignedRequest.url()).thenReturn(mockUrl);
        lenient().when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
            .thenReturn(mockPresignedRequest);
    }

    @Test
    void generatePresignedPutUrl_기본_업로드() {
        // Given
        String key = "test-uploads/basic-file.txt";
        String contentType = "text/plain";
        Duration expiration = Duration.ofMinutes(15);

        // When
        CompletableFuture<String> urlFuture = s3Service.generatePresignedPutUrl(
            TEST_BUCKET, key, expiration, contentType
        );

        // Then
        String presignedUrl = urlFuture.join();
        assertThat(presignedUrl).isNotNull();
        assertThat(presignedUrl).startsWith("https://");
        assertThat(presignedUrl).contains("X-Amz-Signature");
        assertThat(presignedUrl).contains("X-Amz-Expires");
        assertThat(presignedUrl).contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
    }

    @Test
    void generatePresignedPutUrl_메타데이터_포함() {
        // Given
        String key = "test-uploads/metadata-file.jpg";
        String contentType = "image/jpeg";
        Duration expiration = Duration.ofMinutes(10);

        Map<String, String> metadata = Map.of(
            "user-id", "12345",
            "upload-source", "mobile-app"
        );

        // When
        CompletableFuture<String> urlFuture = s3Service.generatePresignedPutUrl(
            TEST_BUCKET, key, expiration, contentType, metadata, S3StorageClass.STANDARD_IA
        );

        // Then
        String presignedUrl = urlFuture.join();
        assertThat(presignedUrl).isNotNull();
        assertThat(presignedUrl).contains("x-amz-storage-class");
    }

    @Test
    void generatePresignedPutUrl_만료시간_기본값() {
        // Given
        String key = "test-uploads/default-expiry.txt";
        String contentType = "text/plain";

        // When - 만료 시간을 null로 설정하여 기본값 사용
        CompletableFuture<String> urlFuture = s3Service.generatePresignedPutUrl(
            TEST_BUCKET, key, null, contentType
        );

        // Then
        String presignedUrl = urlFuture.join();
        assertThat(presignedUrl).isNotNull();
        assertThat(presignedUrl).contains("X-Amz-Expires");
    }

    @Test
    void generatePresignedPutUrl_ContentType_없음() {
        // Given
        String key = "test-uploads/no-content-type.bin";
        Duration expiration = Duration.ofMinutes(5);

        // When - Content-Type을 null로 설정
        CompletableFuture<String> urlFuture = s3Service.generatePresignedPutUrl(
            TEST_BUCKET, key, expiration, null
        );

        // Then
        String presignedUrl = urlFuture.join();
        assertThat(presignedUrl).isNotNull();
        assertThat(presignedUrl).startsWith("https://");
    }
}