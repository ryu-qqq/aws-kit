package com.ryuqq.aws.s3.service.impl;

import com.ryuqq.aws.s3.properties.S3Properties;
import com.ryuqq.aws.s3.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Simplified S3 service implementation - provides essential operations only
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultS3Service implements S3Service {

    private final S3AsyncClient s3AsyncClient;
    private final S3TransferManager transferManager;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public CompletableFuture<String> uploadFile(String bucket, String key, Path file) {
        UploadFileRequest request = UploadFileRequest.builder()
                .source(file)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .build();

        return transferManager.uploadFile(request)
                .completionFuture()
                .thenApply(upload -> upload.response().eTag());
    }

    @Override
    public CompletableFuture<String> uploadBytes(String bucket, String key, byte[] bytes, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength((long) bytes.length)
                .build();

        return s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(bytes))
                .thenApply(PutObjectResponse::eTag);
    }

    @Override
    public CompletableFuture<byte[]> downloadFile(String bucket, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3AsyncClient.getObject(request, AsyncResponseTransformer.toBytes())
                .thenApply(response -> response.asByteArray());
    }

    @Override
    public CompletableFuture<Void> downloadToFile(String bucket, String key, Path targetFile) {
        DownloadFileRequest request = DownloadFileRequest.builder()
                .destination(targetFile)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build())
                .build();

        return transferManager.downloadFile(request)
                .completionFuture()
                .thenApply(download -> null);
    }

    @Override
    public CompletableFuture<Void> deleteObject(String bucket, String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        return s3AsyncClient.deleteObject(request)
                .thenApply(response -> null);
    }

    @Override
    public CompletableFuture<List<String>> listObjects(String bucket, String prefix) {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .build();

        return s3AsyncClient.listObjectsV2(request)
                .thenApply(response -> response.contents().stream()
                        .map(S3Object::key)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<String> generatePresignedUrl(String bucket, String key, Duration expiration) {
        return CompletableFuture.supplyAsync(() -> {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        });
    }

    // Note: uploadLargeFile removed as it's identical to uploadFile
    // Transfer Manager automatically handles multipart upload for large files
}
