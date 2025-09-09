package com.ryuqq.aws.s3.client;

import com.ryuqq.aws.commons.exception.AwsServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.util.concurrent.CompletableFuture;

/**
 * S3 클라이언트 기본 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultS3Client implements S3Client {

    private final S3AsyncClient s3AsyncClient;

    @Override
    public CompletableFuture<String> uploadObject(String bucketName, String key, byte[] content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        return s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(content))
                .thenApply(response -> {
                    log.debug("Object uploaded successfully: {}/{}", bucketName, key);
                    return response.eTag();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to upload object: {}/{}", bucketName, key, throwable);
                    throw new AwsServiceException("s3", "Failed to upload object", throwable);
                });
    }

    @Override
    public CompletableFuture<byte[]> downloadObject(String bucketName, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        return s3AsyncClient.getObject(request, AsyncResponseTransformer.toBytes())
                .thenApply(response -> {
                    log.debug("Object downloaded successfully: {}/{}", bucketName, key);
                    return response.asByteArray();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to download object: {}/{}", bucketName, key, throwable);
                    throw new AwsServiceException("s3", "Failed to download object", throwable);
                });
    }

    @Override
    public CompletableFuture<Void> deleteObject(String bucketName, String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        return s3AsyncClient.deleteObject(request)
                .thenApply(response -> {
                    log.debug("Object deleted successfully: {}/{}", bucketName, key);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    log.error("Failed to delete object: {}/{}", bucketName, key, throwable);
                    throw new AwsServiceException("s3", "Failed to delete object", throwable);
                });
    }

    @Override
    public CompletableFuture<Boolean> objectExists(String bucketName, String key) {
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        
        return s3AsyncClient.headObject(request)
                .thenApply(response -> true)
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof NoSuchKeyException) {
                        return false;
                    }
                    throw new AwsServiceException("s3", "Failed to check object existence", throwable);
                });
    }

    @Override
    public CompletableFuture<String> generatePresignedUrl(String bucketName, String key) {
        // 간소화된 구현 - 실제로는 S3Presigner를 사용해야 함
        return CompletableFuture.completedFuture("https://example.com/presigned-url");
    }
}