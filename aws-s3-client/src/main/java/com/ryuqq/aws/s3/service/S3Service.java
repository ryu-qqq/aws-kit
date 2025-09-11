package com.ryuqq.aws.s3.service;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * S3 서비스 인터페이스 - 간단하고 명확한 메서드만 제공
 */
public interface S3Service {

    /**
     * 파일 업로드
     */
    CompletableFuture<String> uploadFile(String bucket, String key, Path file);

    /**
     * 바이트 배열 업로드
     */
    CompletableFuture<String> uploadBytes(String bucket, String key, byte[] bytes, String contentType);

    /**
     * 파일 다운로드 (바이트 배열 반환)
     */
    CompletableFuture<byte[]> downloadFile(String bucket, String key);

    /**
     * 파일을 지정된 경로로 다운로드
     */
    CompletableFuture<Void> downloadToFile(String bucket, String key, Path targetFile);

    /**
     * 객체 삭제
     */
    CompletableFuture<Void> deleteObject(String bucket, String key);

    /**
     * 객체 목록 조회 (prefix 기반)
     */
    CompletableFuture<List<String>> listObjects(String bucket, String prefix);

    /**
     * Presigned URL 생성 (다운로드용)
     */
    CompletableFuture<String> generatePresignedUrl(String bucket, String key, Duration expiration);
}