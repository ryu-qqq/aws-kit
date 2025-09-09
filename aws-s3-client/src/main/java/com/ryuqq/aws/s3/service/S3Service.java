package com.ryuqq.aws.s3.service;

import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.StorageClass;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * S3 서비스 인터페이스
 */
public interface S3Service {

    /**
     * 파일 업로드
     */
    CompletableFuture<String> uploadFile(String bucketName, String key, Path filePath);

    /**
     * 바이트 배열 업로드
     */
    CompletableFuture<String> uploadBytes(String bucketName, String key, byte[] data);

    /**
     * 스트림 업로드
     */
    CompletableFuture<String> uploadStream(String bucketName, String key, InputStream stream, long contentLength);

    /**
     * 메타데이터와 함께 업로드
     */
    CompletableFuture<String> uploadWithMetadata(String bucketName, String key, byte[] data,
                                                Map<String, String> metadata);

    /**
     * ACL과 함께 업로드
     */
    CompletableFuture<String> uploadWithAcl(String bucketName, String key, byte[] data,
                                           ObjectCannedACL acl);

    /**
     * 멀티파트 업로드 (대용량 파일)
     */
    CompletableFuture<String> multipartUpload(String bucketName, String key, Path filePath);

    /**
     * 파일 다운로드
     */
    CompletableFuture<byte[]> downloadFile(String bucketName, String key);

    /**
     * 파일을 경로로 다운로드
     */
    CompletableFuture<Void> downloadToFile(String bucketName, String key, Path targetPath);

    /**
     * 스트림으로 다운로드
     */
    CompletableFuture<InputStream> downloadAsStream(String bucketName, String key);

    /**
     * 범위 다운로드 (부분 다운로드)
     */
    CompletableFuture<byte[]> downloadRange(String bucketName, String key, long start, long end);

    /**
     * 객체 삭제
     */
    CompletableFuture<Void> deleteObject(String bucketName, String key);

    /**
     * 배치 삭제
     */
    CompletableFuture<Void> deleteObjects(String bucketName, List<String> keys);

    /**
     * 폴더 삭제 (prefix 기반)
     */
    CompletableFuture<Void> deleteFolder(String bucketName, String prefix);

    /**
     * 객체 복사
     */
    CompletableFuture<String> copyObject(String sourceBucket, String sourceKey,
                                        String destBucket, String destKey);

    /**
     * 객체 이동 (복사 후 삭제)
     */
    CompletableFuture<String> moveObject(String sourceBucket, String sourceKey,
                                        String destBucket, String destKey);

    /**
     * 객체 존재 여부 확인
     */
    CompletableFuture<Boolean> objectExists(String bucketName, String key);

    /**
     * 객체 메타데이터 조회
     */
    CompletableFuture<Map<String, String>> getObjectMetadata(String bucketName, String key);

    /**
     * 객체 목록 조회
     */
    CompletableFuture<List<S3ObjectInfo>> listObjects(String bucketName, String prefix);

    /**
     * 페이징된 객체 목록 조회
     */
    CompletableFuture<S3ListResult> listObjectsPaginated(String bucketName, String prefix, 
                                                        String continuationToken, int maxKeys);

    /**
     * Pre-signed URL 생성 (업로드용)
     */
    CompletableFuture<String> generatePresignedUploadUrl(String bucketName, String key, 
                                                        Duration expiration);

    /**
     * Pre-signed URL 생성 (다운로드용)
     */
    CompletableFuture<String> generatePresignedDownloadUrl(String bucketName, String key, 
                                                          Duration expiration);

    /**
     * 버킷 생성
     */
    CompletableFuture<Void> createBucket(String bucketName);

    /**
     * 버킷 삭제
     */
    CompletableFuture<Void> deleteBucket(String bucketName);

    /**
     * 버킷 존재 여부 확인
     */
    CompletableFuture<Boolean> bucketExists(String bucketName);

    /**
     * 버킷 버저닝 활성화
     */
    CompletableFuture<Void> enableBucketVersioning(String bucketName);

    /**
     * 라이프사이클 규칙 설정
     */
    CompletableFuture<Void> setLifecycleRule(String bucketName, String ruleId, 
                                            String prefix, int expirationDays);

    /**
     * 스토리지 클래스 변경
     */
    CompletableFuture<Void> changeStorageClass(String bucketName, String key, 
                                              StorageClass storageClass);

    /**
     * S3 객체 정보
     */
    record S3ObjectInfo(
            String key,
            long size,
            String etag,
            java.time.Instant lastModified,
            StorageClass storageClass
    ) {}

    /**
     * S3 목록 조회 결과
     */
    record S3ListResult(
            List<S3ObjectInfo> objects,
            boolean isTruncated,
            String nextContinuationToken
    ) {}
}