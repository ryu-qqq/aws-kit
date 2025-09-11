package com.ryuqq.aws.s3.service.impl;

import com.ryuqq.aws.s3.properties.S3Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultS3ServiceTest {

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
    void uploadFile_ShouldReturnETag() {
        // Given
        String bucket = "test-bucket";
        String key = "test-key";
        Path file = Paths.get("test-file.txt");
        String expectedETag = "test-etag";

        FileUpload fileUpload = mock(FileUpload.class);
        CompletedFileUpload completedUpload = mock(CompletedFileUpload.class);
        PutObjectResponse putObjectResponse = PutObjectResponse.builder().eTag(expectedETag).build();

        when(transferManager.uploadFile(any(UploadFileRequest.class))).thenReturn(fileUpload);
        when(fileUpload.completionFuture()).thenReturn(CompletableFuture.completedFuture(completedUpload));
        when(completedUpload.response()).thenReturn(putObjectResponse);

        // When
        CompletableFuture<String> result = s3Service.uploadFile(bucket, key, file);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);
        verify(transferManager).uploadFile(any(UploadFileRequest.class));
    }

    @Test
    void uploadBytes_ShouldReturnETag() {
        // Given
        String bucket = "test-bucket";
        String key = "test-key";
        byte[] bytes = "test content".getBytes();
        String contentType = "text/plain";
        String expectedETag = "test-etag";

        PutObjectResponse response = PutObjectResponse.builder().eTag(expectedETag).build();
        when(s3AsyncClient.putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = s3Service.uploadBytes(bucket, key, bytes, contentType);

        // Then
        assertThat(result.join()).isEqualTo(expectedETag);
        verify(s3AsyncClient).putObject(any(PutObjectRequest.class), any(AsyncRequestBody.class));
    }

    @Test
    void downloadFile_ShouldReturnByteArray() {
        // Given
        String bucket = "test-bucket";
        String key = "test-key";
        byte[] expectedContent = "test content".getBytes();

        ResponseBytes<GetObjectResponse> responseBytes = mock(ResponseBytes.class);
        when(responseBytes.asByteArray()).thenReturn(expectedContent);
        when(s3AsyncClient.getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class)))
                .thenReturn(CompletableFuture.completedFuture(responseBytes));

        // When
        CompletableFuture<byte[]> result = s3Service.downloadFile(bucket, key);

        // Then
        assertThat(result.join()).isEqualTo(expectedContent);
        verify(s3AsyncClient).getObject(any(GetObjectRequest.class), any(AsyncResponseTransformer.class));
    }

    @Test
    void deleteObject_ShouldCompleteSuccessfully() {
        // Given
        String bucket = "test-bucket";
        String key = "test-key";

        DeleteObjectResponse response = DeleteObjectResponse.builder().build();
        when(s3AsyncClient.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = s3Service.deleteObject(bucket, key);

        // Then
        assertThat(result.join()).isNull();
        verify(s3AsyncClient).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void listObjects_ShouldReturnObjectKeys() {
        // Given
        String bucket = "test-bucket";
        String prefix = "prefix/";
        List<String> expectedKeys = List.of("prefix/file1.txt", "prefix/file2.txt");

        List<S3Object> s3Objects = List.of(
                S3Object.builder().key("prefix/file1.txt").build(),
                S3Object.builder().key("prefix/file2.txt").build()
        );

        ListObjectsV2Response response = ListObjectsV2Response.builder()
                .contents(s3Objects)
                .build();

        when(s3AsyncClient.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = s3Service.listObjects(bucket, prefix);

        // Then
        assertThat(result.join()).containsExactlyElementsOf(expectedKeys);
        verify(s3AsyncClient).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    void generatePresignedUrl_ShouldReturnUrl() throws Exception {
        // Given
        String bucket = "test-bucket";
        String key = "test-key";
        Duration expiration = Duration.ofHours(1);
        String expectedUrl = "https://test-bucket.s3.amazonaws.com/test-key?X-Amz-Expires=3600";

        PresignedGetObjectRequest presignedRequest = mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL(expectedUrl));
        when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
                .thenReturn(presignedRequest);

        // When
        CompletableFuture<String> result = s3Service.generatePresignedUrl(bucket, key, expiration);

        // Then
        assertThat(result.join()).isEqualTo(expectedUrl);
        verify(s3Presigner).presignGetObject(any(GetObjectPresignRequest.class));
    }

}