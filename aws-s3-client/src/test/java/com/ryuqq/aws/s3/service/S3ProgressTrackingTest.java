package com.ryuqq.aws.s3.service;

import com.ryuqq.aws.s3.service.impl.DefaultS3Service;
import com.ryuqq.aws.s3.types.S3ProgressListener;
import com.ryuqq.aws.s3.types.S3StorageClass;
import com.ryuqq.aws.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * S3 진행률 추적 테스트
 * 
 * 한국어 설명:
 * S3 업로드/다운로드 진행률 추적 기능을 테스트합니다.
 * S3ProgressListener를 통한 진행 상황 모니터링과 메타데이터 업로드를 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("S3 진행률 추적 테스트")
class S3ProgressTrackingTest {

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
    @DisplayName("uploadFileWithProgress - 업로드 진행률 추적 성공")
    void uploadFileWithProgress_ShouldTrackProgress_WhenProgressListenerProvided() {
        // Given
        String bucket = "test-bucket";
        String key = "large-file.zip";
        Path file = Paths.get("/tmp/large-file.zip");
        String expectedETag = "\"progress-etag-123\"";

        // 진행률 추적을 위한 변수들
        AtomicInteger progressCallCount = new AtomicInteger(0);
        AtomicLong lastBytesTransferred = new AtomicLong(0);
        
        S3ProgressListener progressListener = event -> {
            progressCallCount.incrementAndGet();
            lastBytesTransferred.set(event.getBytesTransferred());
            
            // 이벤트 데이터 검증
            assertThat(event.getBucket()).isEqualTo(bucket);
            assertThat(event.getKey()).isEqualTo(key);
            assertThat(event.getTransferType()).isEqualTo(S3ProgressListener.TransferType.UPLOAD);
            assertThat(event.getBytesTransferred()).isGreaterThanOrEqualTo(0);
            assertThat(event.getTotalBytes()).isGreaterThan(0);
            assertThat(event.getProgressPercentage()).isBetween(0.0, 100.0);
        };

        // FileUpload 모킹
        FileUpload fileUpload = mock(FileUpload.class);
        CompletedFileUpload completedUpload = mock(CompletedFileUpload.class);
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().eTag(expectedETag).build();

        when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(CompletableFuture.completedFuture(completedUpload));
        when(completedUpload.response()).thenReturn(putObjectResponse);

        // When
        CompletableFuture<String> result = s3Service.uploadFileWithProgress(bucket, key, file, progressListener);
        String actualETag = result.join();

        // Then
        assertThat(actualETag).isEqualTo(expectedETag);

        // UploadFileRequest 캡처 및 TransferListener 검증
        ArgumentCaptor<UploadFileRequest> requestCaptor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(transferManager).uploadFile(requestCaptor.capture());

        UploadFileRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.source()).isEqualTo(file);
        assertThat(capturedRequest.putObjectRequest().bucket()).isEqualTo(bucket);
        assertThat(capturedRequest.putObjectRequest().key()).isEqualTo(key);

        // TransferListener가 등록되었는지 확인
        assertThat(capturedRequest.transferListeners()).isNotEmpty();
    }

    @Test
    @DisplayName("uploadFileWithProgress - 진행률 리스너 없이 업로드")
    void uploadFileWithProgress_ShouldUploadSuccessfully_WhenNoProgressListener() {
        // Given
        String bucket = "test-bucket";
        String key = "simple-file.txt";
        Path file = Paths.get("/tmp/simple-file.txt");
        String expectedETag = "\"no-progress-etag\"";

        FileUpload fileUpload = mock(FileUpload.class);
        CompletedFileUpload completedUpload = mock(CompletedFileUpload.class);
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().eTag(expectedETag).build();

        when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(CompletableFuture.completedFuture(completedUpload));
        when(completedUpload.response()).thenReturn(putObjectResponse);

        // When
        CompletableFuture<String> result = s3Service.uploadFileWithProgress(bucket, key, file, null);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);

        ArgumentCaptor<UploadFileRequest> requestCaptor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(transferManager).uploadFile(requestCaptor.capture());

        UploadFileRequest capturedRequest = requestCaptor.getValue();
        // 진행률 리스너가 null일 때는 TransferListener가 추가되지 않음
        assertThat(capturedRequest.transferListeners()).isNullOrEmpty();
    }

    @Test
    @DisplayName("downloadToFileWithProgress - 다운로드 진행률 추적 성공")
    void downloadToFileWithProgress_ShouldTrackProgress_WhenProgressListenerProvided() {
        // Given
        String bucket = "download-bucket";
        String key = "movie.mp4";
        Path targetFile = Paths.get("/tmp/movie.mp4");

        AtomicInteger progressCallCount = new AtomicInteger(0);
        AtomicLong maxBytesTransferred = new AtomicLong(0);

        S3ProgressListener progressListener = event -> {
            progressCallCount.incrementAndGet();
            maxBytesTransferred.updateAndGet(current -> Math.max(current, event.getBytesTransferred()));
            
            // 다운로드 이벤트 검증
            assertThat(event.getBucket()).isEqualTo(bucket);
            assertThat(event.getKey()).isEqualTo(key);
            assertThat(event.getTransferType()).isEqualTo(S3ProgressListener.TransferType.DOWNLOAD);
            assertThat(event.getRemainingBytes()).isEqualTo(event.getTotalBytes() - event.getBytesTransferred());
        };

        // FileDownload 모킹
        FileDownload fileDownload = mock(FileDownload.class);
        CompletedFileDownload completedDownload = mock(CompletedFileDownload.class);

        when(transferManager.downloadFile(any(DownloadFileRequest.class))).thenReturn(fileDownload);
        when(fileDownload.completionFuture()).thenReturn(CompletableFuture.completedFuture(completedDownload));

        // When
        CompletableFuture<Void> result = s3Service.downloadToFileWithProgress(bucket, key, targetFile, progressListener);

        // Then
        assertThat(result.join()).isNull(); // Void 반환

        ArgumentCaptor<DownloadFileRequest> requestCaptor = ArgumentCaptor.forClass(DownloadFileRequest.class);
        verify(transferManager).downloadFile(requestCaptor.capture());

        DownloadFileRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.destination()).isEqualTo(targetFile);
        assertThat(capturedRequest.getObjectRequest().bucket()).isEqualTo(bucket);
        assertThat(capturedRequest.getObjectRequest().key()).isEqualTo(key);
        assertThat(capturedRequest.transferListeners()).isNotEmpty();
    }

    @Test
    @DisplayName("uploadFileWithMetadata - 메타데이터와 스토리지 클래스 설정하여 업로드")
    void uploadFileWithMetadata_ShouldUploadWithMetadata_WhenMetadataAndStorageClassProvided() {
        // Given
        String bucket = "metadata-bucket";
        String key = "document.pdf";
        Path file = Paths.get("/tmp/document.pdf");
        String expectedETag = "\"metadata-etag\"";
        
        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "John Doe");
        metadata.put("department", "Engineering");
        metadata.put("created", "2024-01-01");
        
        S3StorageClass storageClass = S3StorageClass.STANDARD_IA;

        FileUpload fileUpload = mock(FileUpload.class);
        CompletedFileUpload completedUpload = mock(CompletedFileUpload.class);
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().eTag(expectedETag).build();

        when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(CompletableFuture.completedFuture(completedUpload));
        when(completedUpload.response()).thenReturn(putObjectResponse);

        // When
        CompletableFuture<String> result = s3Service.uploadFileWithMetadata(bucket, key, file, metadata, storageClass);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);

        ArgumentCaptor<UploadFileRequest> requestCaptor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(transferManager).uploadFile(requestCaptor.capture());

        UploadFileRequest capturedRequest = requestCaptor.getValue();
        PutObjectRequest putRequest = capturedRequest.putObjectRequest();
        
        assertThat(putRequest.bucket()).isEqualTo(bucket);
        assertThat(putRequest.key()).isEqualTo(key);
        assertThat(putRequest.metadata()).containsEntry("author", "John Doe");
        assertThat(putRequest.metadata()).containsEntry("department", "Engineering");
        assertThat(putRequest.metadata()).containsEntry("created", "2024-01-01");
        assertThat(putRequest.storageClass()).isEqualTo(StorageClass.STANDARD_IA);
    }

    @Test
    @DisplayName("uploadFileWithMetadata - 빈 메타데이터 처리")
    void uploadFileWithMetadata_ShouldUploadWithoutMetadata_WhenEmptyMetadata() {
        // Given
        String bucket = "test-bucket";
        String key = "simple-file.txt";
        Path file = Paths.get("/tmp/simple-file.txt");
        String expectedETag = "\"simple-etag\"";
        
        Map<String, String> emptyMetadata = new HashMap<>(); // 빈 메타데이터
        S3StorageClass storageClass = S3StorageClass.STANDARD;

        FileUpload fileUpload = mock(FileUpload.class);
        CompletedFileUpload completedUpload = mock(CompletedFileUpload.class);
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().eTag(expectedETag).build();

        when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(CompletableFuture.completedFuture(completedUpload));
        when(completedUpload.response()).thenReturn(putObjectResponse);

        // When
        CompletableFuture<String> result = s3Service.uploadFileWithMetadata(bucket, key, file, emptyMetadata, storageClass);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);

        ArgumentCaptor<UploadFileRequest> requestCaptor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(transferManager).uploadFile(requestCaptor.capture());

        UploadFileRequest capturedRequest = requestCaptor.getValue();
        PutObjectRequest putRequest = capturedRequest.putObjectRequest();
        
        // 빈 메타데이터는 전송되지 않아야 함
        assertThat(putRequest.hasMetadata()).isFalse();
        assertThat(putRequest.storageClass()).isEqualTo(StorageClass.STANDARD);
    }

    @Test
    @DisplayName("uploadFileWithMetadata - 메타데이터만 설정 (스토리지 클래스 없음)")
    void uploadFileWithMetadata_ShouldUploadWithMetadataOnly_WhenOnlyMetadataProvided() {
        // Given
        String bucket = "test-bucket";
        String key = "metadata-only.txt";
        Path file = Paths.get("/tmp/metadata-only.txt");
        String expectedETag = "\"metadata-only-etag\"";
        
        Map<String, String> metadata = Map.of("purpose", "testing");

        FileUpload fileUpload = mock(FileUpload.class);
        CompletedFileUpload completedUpload = mock(CompletedFileUpload.class);
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().eTag(expectedETag).build();

        when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(CompletableFuture.completedFuture(completedUpload));
        when(completedUpload.response()).thenReturn(putObjectResponse);

        // When
        CompletableFuture<String> result = s3Service.uploadFileWithMetadata(bucket, key, file, metadata, null);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);

        ArgumentCaptor<UploadFileRequest> requestCaptor = ArgumentCaptor.forClass(UploadFileRequest.class);
        verify(transferManager).uploadFile(requestCaptor.capture());

        UploadFileRequest capturedRequest = requestCaptor.getValue();
        PutObjectRequest putRequest = capturedRequest.putObjectRequest();
        
        assertThat(putRequest.metadata()).containsEntry("purpose", "testing");

    }

    @Test
    @DisplayName("S3ProgressEvent - 진행률 계산 테스트")
    void s3ProgressEvent_ShouldCalculateProgressCorrectly_WhenBytesTransferred() {
        // Given
        long totalBytes = 1000L;
        long bytesTransferred = 250L;
        String bucket = "test-bucket";
        String key = "test-file.txt";
        
        // When
        S3ProgressListener.S3ProgressEvent event = new S3ProgressListener.S3ProgressEvent(
            bytesTransferred, totalBytes, S3ProgressListener.TransferType.UPLOAD, bucket, key);
        
        // Then
        assertThat(event.getBytesTransferred()).isEqualTo(250L);
        assertThat(event.getTotalBytes()).isEqualTo(1000L);
        assertThat(event.getProgressPercentage()).isEqualTo(25.0);
        assertThat(event.getRemainingBytes()).isEqualTo(750L);
        assertThat(event.isCompleted()).isFalse();
        assertThat(event.getBucket()).isEqualTo(bucket);
        assertThat(event.getKey()).isEqualTo(key);
        assertThat(event.getTransferType()).isEqualTo(S3ProgressListener.TransferType.UPLOAD);
    }

    @Test
    @DisplayName("S3ProgressEvent - 완료 상태 테스트")
    void s3ProgressEvent_ShouldIndicateCompletion_WhenFullyTransferred() {
        // Given
        long totalBytes = 1000L;
        long bytesTransferred = 1000L;
        
        // When
        S3ProgressListener.S3ProgressEvent event = new S3ProgressListener.S3ProgressEvent(
            bytesTransferred, totalBytes, S3ProgressListener.TransferType.DOWNLOAD, "bucket", "key");
        
        // Then
        assertThat(event.getProgressPercentage()).isEqualTo(100.0);
        assertThat(event.getRemainingBytes()).isEqualTo(0L);
        assertThat(event.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("S3ProgressEvent - 0바이트 파일 처리")
    void s3ProgressEvent_ShouldHandleZeroByteFile_WhenTotalBytesIsZero() {
        // Given
        long totalBytes = 0L;
        long bytesTransferred = 0L;
        
        // When
        S3ProgressListener.S3ProgressEvent event = new S3ProgressListener.S3ProgressEvent(
            bytesTransferred, totalBytes, S3ProgressListener.TransferType.UPLOAD, "bucket", "empty-file.txt");
        
        // Then
        assertThat(event.getProgressPercentage()).isEqualTo(0.0); // 0으로 나누기 처리
        assertThat(event.getRemainingBytes()).isEqualTo(0L);
        assertThat(event.isCompleted()).isTrue(); // 0바이트 파일은 완료 상태
    }

    @Test
    @DisplayName("loggingListener - 로깅 리스너 동작 테스트")
    void loggingListener_ShouldLogProgress_WhenProgressMade() {
        // Given
        S3ProgressListener loggingListener = S3ProgressListener.loggingListener();
        
        // When & Then - 로깅 리스너는 예외를 발생시키지 않고 정상 동작해야 함
        S3ProgressListener.S3ProgressEvent event = new S3ProgressListener.S3ProgressEvent(
            1024 * 1024 * 10, // 10MB 전송 (로깅 임계값)
            1024 * 1024 * 100, // 100MB 총 크기
            S3ProgressListener.TransferType.UPLOAD,
            "test-bucket", 
            "large-file.zip"
        );
        
        assertThatNoException().isThrownBy(() -> loggingListener.onProgress(event));
    }
}