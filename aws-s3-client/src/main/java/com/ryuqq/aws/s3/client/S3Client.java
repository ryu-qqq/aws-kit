package com.ryuqq.aws.s3.client;

import java.util.concurrent.CompletableFuture;

/**
 * S3 클라이언트 인터페이스
 */
public interface S3Client {
    
    CompletableFuture<String> uploadObject(String bucketName, String key, byte[] content);
    
    CompletableFuture<byte[]> downloadObject(String bucketName, String key);
    
    CompletableFuture<Void> deleteObject(String bucketName, String key);
    
    CompletableFuture<Boolean> objectExists(String bucketName, String key);
    
    CompletableFuture<String> generatePresignedUrl(String bucketName, String key);
}