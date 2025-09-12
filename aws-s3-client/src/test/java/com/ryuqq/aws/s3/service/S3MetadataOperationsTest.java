package com.ryuqq.aws.s3.service;

import com.ryuqq.aws.s3.service.impl.DefaultS3Service;
import com.ryuqq.aws.s3.types.S3Metadata;
import com.ryuqq.aws.s3.types.S3StorageClass;
import com.ryuqq.aws.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S3 메타데이터 작업 테스트
 * 
 * 한국어 설명:
 * S3 객체의 메타데이터 조회 및 복사 기능을 테스트합니다.
 * headObject, copyObject, copyObjectWithMetadata 메서드의 동작을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3 메타데이터 작업 테스트")
class S3MetadataOperationsTest {

    @Mock
    private S3AsyncClient s3AsyncClient;
    
    @Mock
    private S3TransferManager transferManager;
    
    @Mock
    private S3Presigner s3Presigner;
    
    @Mock
    private S3Properties s3Properties;

    private DefaultS3Service s3Service;

    @BeforeEach
    void setUp() {
        s3Service = new DefaultS3Service(s3AsyncClient, transferManager, s3Presigner, s3Properties);
    }

    @Test
    @DisplayName("headObject - 객체 메타데이터 조회 성공")
    void headObject_ShouldReturnMetadata_WhenObjectExists() {
        // Given
        String bucket = "test-bucket";
        String key = "test-object.jpg";
        Instant lastModified = Instant.now();
        
        Map<String, String> userMetadata = new HashMap<>();
        userMetadata.put("author", "test-user");
        userMetadata.put("category", "photo");
        
        HeadObjectResponse response = HeadObjectResponse.builder()
                .contentLength(1024L)
                .contentType("image/jpeg")
                .eTag("\"d41d8cd98f00b204e9800998ecf8427e\"")
                .lastModified(lastModified)
                .versionId("version-123")
                .storageClass(StorageClass.STANDARD)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .metadata(userMetadata)
                .cacheControl("max-age=3600")
                .contentEncoding("gzip")
                .contentDisposition("attachment; filename=\"photo.jpg\"")
                .build();

        when(s3AsyncClient.headObject(any(HeadObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<S3Metadata> result = s3Service.headObject(bucket, key);
        S3Metadata metadata = result.join();

        // Then
        assertThat(metadata).isNotNull();
        assertThat(metadata.getContentLength()).isEqualTo(1024L);
        assertThat(metadata.getContentType()).isEqualTo("image/jpeg");
        assertThat(metadata.getETag()).isEqualTo("\"d41d8cd98f00b204e9800998ecf8427e\"");
        assertThat(metadata.getLastModified()).isEqualTo(lastModified);
        assertThat(metadata.getVersionId()).isEqualTo("version-123");
        assertThat(metadata.getStorageClass()).isEqualTo("STANDARD");
        assertThat(metadata.getServerSideEncryption()).isEqualTo("AES256");
        assertThat(metadata.getUserMetadata()).containsEntry("author", "test-user");
        assertThat(metadata.getUserMetadata()).containsEntry("category", "photo");
        assertThat(metadata.getCacheControl()).isEqualTo("max-age=3600");
        assertThat(metadata.getContentEncoding()).isEqualTo("gzip");
        assertThat(metadata.getContentDisposition()).isEqualTo("attachment; filename=\"photo.jpg\"");

        verify(s3AsyncClient).headObject(argThat(request -> 
            request.bucket().equals(bucket) && request.key().equals(key)));
    }

    @Test
    @DisplayName("headObject - 존재하지 않는 객체 조회 시 예외 발생")
    void headObject_ShouldThrowException_WhenObjectNotFound() {
        // Given
        String bucket = "test-bucket";
        String key = "non-existing-object.jpg";
        
        NoSuchKeyException exception = NoSuchKeyException.builder()
                .message("The specified key does not exist.")
                .build();

        when(s3AsyncClient.headObject(any(HeadObjectRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(exception));

        // When & Then
        CompletableFuture<S3Metadata> result = s3Service.headObject(bucket, key);
        
        assertThatThrownBy(result::join)
                .hasCauseInstanceOf(NoSuchKeyException.class)
                .hasMessageContaining("The specified key does not exist");
    }

    @Test
    @DisplayName("copyObject - 기본 객체 복사 성공")
    void copyObject_ShouldReturnETag_WhenCopySuccessful() {
        // Given
        String sourceBucket = "source-bucket";
        String sourceKey = "source/file.txt";
        String destBucket = "dest-bucket";
        String destKey = "dest/file.txt";
        String expectedETag = "\"098f6bcd4621d373cade4e832627b4f6\"";
        
        CopyObjectResult copyResult = CopyObjectResult.builder()
                .eTag(expectedETag)
                .build();
        
        CopyObjectResponse response = CopyObjectResponse.builder()
                .copyObjectResult(copyResult)
                .build();

        when(s3AsyncClient.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = s3Service.copyObject(sourceBucket, sourceKey, destBucket, destKey);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);
        
        verify(s3AsyncClient).copyObject(argThat(request -> 
            request.sourceBucket().equals(sourceBucket) &&
            request.sourceKey().equals(sourceKey) &&
            request.destinationBucket().equals(destBucket) &&
            request.destinationKey().equals(destKey) &&
            request.copySource().equals(sourceBucket + "/" + sourceKey)));
    }

    @Test
    @DisplayName("copyObjectWithMetadata - 메타데이터와 스토리지 클래스 변경하여 복사")
    void copyObjectWithMetadata_ShouldCopyWithNewMetadata_WhenMetadataProvided() {
        // Given
        String sourceBucket = "source-bucket";
        String sourceKey = "source/document.pdf";
        String destBucket = "dest-bucket";
        String destKey = "dest/document.pdf";
        String expectedETag = "\"new-etag-12345\"";
        
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("processed", "true");
        newMetadata.put("version", "2.0");
        
        S3StorageClass storageClass = S3StorageClass.STANDARD_IA;
        
        CopyObjectResult copyResult = CopyObjectResult.builder()
                .eTag(expectedETag)
                .build();
        
        CopyObjectResponse response = CopyObjectResponse.builder()
                .copyObjectResult(copyResult)
                .build();

        when(s3AsyncClient.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = s3Service.copyObjectWithMetadata(
            sourceBucket, sourceKey, destBucket, destKey, newMetadata, storageClass);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);
        
        verify(s3AsyncClient).copyObject(argThat(request -> 
            request.sourceBucket().equals(sourceBucket) &&
            request.sourceKey().equals(sourceKey) &&
            request.destinationBucket().equals(destBucket) &&
            request.destinationKey().equals(destKey) &&
            request.metadataDirective() == MetadataDirective.REPLACE &&
            request.metadata().get("processed").equals("true") &&
            request.metadata().get("version").equals("2.0") &&
            request.storageClass() == StorageClass.STANDARD_IA));
    }

    @Test
    @DisplayName("copyObjectWithMetadata - 메타데이터 없이 복사 (COPY 모드)")
    void copyObjectWithMetadata_ShouldUseOriginalMetadata_WhenMetadataIsNull() {
        // Given
        String sourceBucket = "source-bucket";
        String sourceKey = "source/file.txt";
        String destBucket = "dest-bucket";
        String destKey = "dest/file.txt";
        String expectedETag = "\"original-etag\"";
        
        CopyObjectResult copyResult = CopyObjectResult.builder()
                .eTag(expectedETag)
                .build();
        
        CopyObjectResponse response = CopyObjectResponse.builder()
                .copyObjectResult(copyResult)
                .build();

        when(s3AsyncClient.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = s3Service.copyObjectWithMetadata(
            sourceBucket, sourceKey, destBucket, destKey, null, null);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);
        
        verify(s3AsyncClient).copyObject(argThat(request -> 
            request.metadataDirective() == MetadataDirective.COPY &&
            !request.hasMetadata() &&
            !request.hasStorageClass()));
    }

    @Test
    @DisplayName("copyObjectWithMetadata - 스토리지 클래스만 변경")
    void copyObjectWithMetadata_ShouldOnlyChangeStorageClass_WhenOnlyStorageClassProvided() {
        // Given
        String sourceBucket = "source-bucket";
        String sourceKey = "old-file.log";
        String destBucket = "archive-bucket";
        String destKey = "archive/old-file.log";
        String expectedETag = "\"archive-etag\"";
        
        S3StorageClass storageClass = S3StorageClass.GLACIER;
        
        CopyObjectResult copyResult = CopyObjectResult.builder()
                .eTag(expectedETag)
                .build();
        
        CopyObjectResponse response = CopyObjectResponse.builder()
                .copyObjectResult(copyResult)
                .build();

        when(s3AsyncClient.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = s3Service.copyObjectWithMetadata(
            sourceBucket, sourceKey, destBucket, destKey, null, storageClass);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);
        
        verify(s3AsyncClient).copyObject(argThat(request -> 
            request.metadataDirective() == MetadataDirective.COPY &&
            request.storageClass() == StorageClass.GLACIER &&
            !request.hasMetadata()));
    }

    @Test
    @DisplayName("copyObject - 크로스 리전 복사")
    void copyObject_ShouldHandleCrossRegionCopy_WhenDifferentRegions() {
        // Given - 다른 리전의 버킷들 간 복사 시뮬레이션
        String sourceBucket = "us-east-1-bucket";
        String sourceKey = "data/report.xlsx";
        String destBucket = "ap-northeast-2-bucket";
        String destKey = "backup/report.xlsx";
        String expectedETag = "\"cross-region-etag\"";
        
        CopyObjectResult copyResult = CopyObjectResult.builder()
                .eTag(expectedETag)
                .build();
        
        CopyObjectResponse response = CopyObjectResponse.builder()
                .copyObjectResult(copyResult)
                .build();

        when(s3AsyncClient.copyObject(any(CopyObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = s3Service.copyObject(sourceBucket, sourceKey, destBucket, destKey);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);
        
        // 크로스 리전 복사에서도 정상적으로 copySource가 설정되는지 확인
        verify(s3AsyncClient).copyObject(argThat(request -> 
            request.copySource().equals(sourceBucket + "/" + sourceKey)));
    }

    @Test
    @DisplayName("headObject - 빈 메타데이터 처리")
    void headObject_ShouldHandleEmptyMetadata_WhenNoUserMetadata() {
        // Given
        String bucket = "test-bucket";
        String key = "simple-file.txt";
        
        HeadObjectResponse response = HeadObjectResponse.builder()
                .contentLength(512L)
                .contentType("text/plain")
                .eTag("\"simple-etag\"")
                .lastModified(Instant.now())
                .metadata(Map.of()) // 빈 메타데이터
                .build();

        when(s3AsyncClient.headObject(any(HeadObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<S3Metadata> result = s3Service.headObject(bucket, key);
        S3Metadata metadata = result.join();

        // Then
        assertThat(metadata).isNotNull();
        assertThat(metadata.getContentLength()).isEqualTo(512L);
        assertThat(metadata.getUserMetadata()).isEmpty();
        assertThat(metadata.getVersionId()).isNull();
        assertThat(metadata.getStorageClass()).isNull();
    }
}