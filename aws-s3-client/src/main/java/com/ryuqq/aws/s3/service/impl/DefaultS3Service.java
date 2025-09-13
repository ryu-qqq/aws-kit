package com.ryuqq.aws.s3.service.impl;

import com.ryuqq.aws.s3.properties.S3Properties;
import com.ryuqq.aws.s3.service.S3Service;
import com.ryuqq.aws.s3.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.BytesWrapper;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.*;
import software.amazon.awssdk.transfer.s3.progress.TransferListener;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Simplified S3 service implementation - provides essential operations only
 */
@Service
public class DefaultS3Service implements S3Service {

    private static final Logger log = LoggerFactory.getLogger(DefaultS3Service.class);

    private final S3AsyncClient s3AsyncClient;
    private final S3TransferManager transferManager;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    public DefaultS3Service(
            S3AsyncClient s3AsyncClient,
            S3TransferManager transferManager,
            S3Presigner s3Presigner,
            S3Properties s3Properties) {
        this.s3AsyncClient = s3AsyncClient;
        this.transferManager = transferManager;
        this.s3Presigner = s3Presigner;
        this.s3Properties = s3Properties;
    }

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
                .thenApply(BytesWrapper::asByteArray);
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
    public CompletableFuture<S3Metadata> headObject(String bucket, String key) {
        /**
         * 한국어 설명:
         * HEAD 요청으로 객체 메타데이터만 조회합니다.
         * 실제 데이터를 다운로드하지 않아 빠르고 효율적입니다.
         */
        HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        
        return s3AsyncClient.headObject(request)
                .thenApply(response -> new S3Metadata(
                        response.contentLength(),
                        response.contentType(),
                        response.eTag(),
                        response.lastModified(),
                        response.versionId(),
                        response.storageClassAsString(),
                        response.serverSideEncryptionAsString(),
                        response.metadata(),
                        response.cacheControl(),
                        response.contentEncoding(),
                        response.contentDisposition()
                ));
    }
    
    @Override
    public CompletableFuture<String> copyObject(String sourceBucket, String sourceKey, 
                                                String destBucket, String destKey) {
        /**
         * 한국어 설명:
         * 서버사이드 복사로 데이터가 클라이언트를 거치지 않습니다.
         * 대용량 파일도 빠르게 복사 가능합니다.
         */
        String copySource = sourceBucket + "/" + sourceKey;
        CopyObjectRequest request = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceKey)
                .destinationBucket(destBucket)
                .destinationKey(destKey)
                .copySource(copySource)
                .build();
        
        return s3AsyncClient.copyObject(request)
                .thenApply(CopyObjectResponse::copyObjectResult)
                .thenApply(result -> result.eTag());
    }
    
    @Override
    public CompletableFuture<String> copyObjectWithMetadata(String sourceBucket, String sourceKey,
                                                            String destBucket, String destKey,
                                                            Map<String, String> newMetadata,
                                                            S3StorageClass storageClass) {
        /**
         * 한국어 설명:
         * 복사하면서 메타데이터와 스토리지 클래스를 변경할 수 있습니다.
         * 원본 객체는 변경되지 않습니다.
         */
        String copySource = sourceBucket + "/" + sourceKey;
        CopyObjectRequest.Builder requestBuilder = CopyObjectRequest.builder()
                .sourceBucket(sourceBucket)
                .sourceKey(sourceKey)
                .destinationBucket(destBucket)
                .destinationKey(destKey)
                .copySource(copySource)
                .metadataDirective(newMetadata != null ? MetadataDirective.REPLACE : MetadataDirective.COPY);
        
        if (newMetadata != null) {
            requestBuilder.metadata(newMetadata);
        }
        
        if (storageClass != null) {
            requestBuilder.storageClass(storageClass.toAwsStorageClass());
        }
        
        return s3AsyncClient.copyObject(requestBuilder.build())
                .thenApply(CopyObjectResponse::copyObjectResult)
                .thenApply(result -> result.eTag());
    }
    
    @Override
    public CompletableFuture<List<String>> deleteObjects(String bucket, List<String> keys) {
        /**
         * 한국어 설명:
         * 최대 1000개의 객체를 한 번에 삭제합니다.
         * 일부 객체 삭제 실패 시 실패한 키 목록을 반환합니다.
         */
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        
        List<ObjectIdentifier> objectIds = keys.stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .collect(Collectors.toList());
        
        Delete delete = Delete.builder()
                .objects(objectIds)
                .build();
        
        DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(delete)
                .build();
        
        return s3AsyncClient.deleteObjects(request)
                .thenApply(response -> response.errors().stream()
                        .map(S3Error::key)
                        .collect(Collectors.toList()));
    }
    
    @Override
    public CompletableFuture<String> uploadFileWithMetadata(String bucket, String key, Path file,
                                                            Map<String, String> metadata,
                                                            S3StorageClass storageClass) {
        /**
         * 한국어 설명:
         * 파일 업로드 시 메타데이터와 스토리지 클래스를 지정합니다.
         * TransferManager가 파일 크기에 따라 최적의 업로드 방식을 선택합니다.
         */
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key);
        
        if (metadata != null && !metadata.isEmpty()) {
            requestBuilder.metadata(metadata);
        }
        
        if (storageClass != null) {
            requestBuilder.storageClass(storageClass.toAwsStorageClass());
        }
        
        UploadFileRequest uploadRequest = UploadFileRequest.builder()
                .source(file)
                .putObjectRequest(requestBuilder.build())
                .build();
        
        return transferManager.uploadFile(uploadRequest)
                .completionFuture()
                .thenApply(upload -> upload.response().eTag());
    }
    
    @Override
    public CompletableFuture<String> uploadFileWithProgress(String bucket, String key, Path file,
                                                            S3ProgressListener progressListener) {
        /**
         * 한국어 설명:
         * 업로드 진행률을 추적합니다.
         * TransferListener를 통해 진행 상황을 모니터링합니다.
         */
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        
        UploadFileRequest.Builder uploadBuilder = UploadFileRequest.builder()
                .source(file)
                .putObjectRequest(putRequest);
        
        // 진행률 리스너 설정
        if (progressListener != null) {
            uploadBuilder.addTransferListener(new TransferListener() {
                @Override
                public void bytesTransferred(Context.BytesTransferred context) {
                    long transferred = context.progressSnapshot().transferredBytes();
                    long total = context.progressSnapshot().totalBytes().orElse(-1L);
                    
                    S3ProgressListener.S3ProgressEvent event = new S3ProgressListener.S3ProgressEvent(
                            transferred, total, 
                            S3ProgressListener.TransferType.UPLOAD,
                            bucket, key
                    );
                    progressListener.onProgress(event);
                }
            });
        }
        
        return transferManager.uploadFile(uploadBuilder.build())
                .completionFuture()
                .thenApply(upload -> upload.response().eTag());
    }
    
    @Override
    public CompletableFuture<Void> downloadToFileWithProgress(String bucket, String key, Path targetFile,
                                                              S3ProgressListener progressListener) {
        /**
         * 한국어 설명:
         * 다운로드 진행률을 추적합니다.
         * 대용량 파일 다운로드 시 진행 상황을 모니터링할 수 있습니다.
         */
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        
        DownloadFileRequest.Builder downloadBuilder = DownloadFileRequest.builder()
                .destination(targetFile)
                .getObjectRequest(getRequest);
        
        // 진행률 리스너 설정
        if (progressListener != null) {
            downloadBuilder.addTransferListener(new TransferListener() {
                @Override
                public void bytesTransferred(Context.BytesTransferred context) {
                    long transferred = context.progressSnapshot().transferredBytes();
                    long total = context.progressSnapshot().totalBytes().orElse(-1L);
                    
                    S3ProgressListener.S3ProgressEvent event = new S3ProgressListener.S3ProgressEvent(
                            transferred, total,
                            S3ProgressListener.TransferType.DOWNLOAD,
                            bucket, key
                    );
                    progressListener.onProgress(event);
                }
            });
        }
        
        return transferManager.downloadFile(downloadBuilder.build())
                .completionFuture()
                .thenApply(download -> null);
    }
    
    @Override
    public CompletableFuture<Void> putObjectTags(String bucket, String key, S3Tag tags) {
        /**
         * 한국어 설명:
         * 객체에 태그를 설정합니다.
         * 태그는 라이프사이클 정책, 비용 관리, 접근 제어에 활용됩니다.
         */
        Tagging tagging = Tagging.builder()
                .tagSet(tags.toAwsTags())
                .build();
        
        PutObjectTaggingRequest request = PutObjectTaggingRequest.builder()
                .bucket(bucket)
                .key(key)
                .tagging(tagging)
                .build();
        
        return s3AsyncClient.putObjectTagging(request)
                .thenApply(response -> null);
    }
    
    @Override
    public CompletableFuture<S3Tag> getObjectTags(String bucket, String key) {
        /**
         * 한국어 설명:
         * 객체의 태그를 조회합니다.
         */
        GetObjectTaggingRequest request = GetObjectTaggingRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        
        return s3AsyncClient.getObjectTagging(request)
                .thenApply(response -> S3Tag.fromAwsTags(response.tagSet()));
    }
    
    @Override
    public CompletableFuture<Void> deleteObjectTags(String bucket, String key) {
        /**
         * 한국어 설명:
         * 객체의 모든 태그를 삭제합니다.
         */
        DeleteObjectTaggingRequest request = DeleteObjectTaggingRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();
        
        return s3AsyncClient.deleteObjectTagging(request)
                .thenApply(response -> null);
    }
    
    @Override
    public CompletableFuture<String> generatePresignedUrl(String bucket, String key, Duration expiration) {
        /**
         * 한국어 설명:
         * Presigned URL 생성 시 기본 만료 시간을 사용할 수 있습니다.
         * null인 경우 설정된 기본값을 사용합니다.
         */
        Duration effectiveExpiration = expiration != null ? expiration : s3Properties.presignedUrlExpiry();
        
        return CompletableFuture.supplyAsync(() -> {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(effectiveExpiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            return s3Presigner.presignGetObject(presignRequest).url().toString();
        });
    }

    /*
     * 구현 노트: uploadLargeFile 메서드를 별도로 구현하지 않음
     * 
     * 한국어 설명:
     * TransferManager가 파일 크기를 자동으로 감지하여 최적의 업로드 방식을 선택합니다:
     * 
     * 자동 선택 기준:
     * - 5MB 미만: 단일 PUT 요청 (빠른 처리)
     * - 5MB 이상: 멀티파트 업로드 (안정성, 재시도 가능)
     * - 16MB 이상: 병렬 멀티파트 (최대 성능)
     * 
     * 멀티파트 업로드 장점:
     * - 네트워크 장애 시 실패한 부분만 재시도
     * - 병렬 전송으로 대역폭 최대 활용
     * - 메모리 효율적 (전체 파일을 메모리에 로드하지 않음)
     * - 진행률 추적 가능 (확장 시)
     * 
     * 따라서 uploadFile() 메서드 하나로 모든 크기의 파일을 최적으로 처리 가능
     */
    
    /*
     * 추가 구현 고려사항:
     * 
     * 1. 에러 처리 및 재시도:
     *    - 네트워크 타임아웃: 자동 재시도 정책
     *    - AWS 서비스 장애: 지수 백오프 재시도
     *    - 클라이언트 오류: 명확한 예외 메시지
     * 
     * 2. 모니터링 및 로깅:
     *    - 성공/실패 메트릭스 수집
     *    - 느린 요청 감지 및 알림
     *    - 비용 최적화를 위한 사용량 추적
     * 
     * 3. 성능 최적화:
     *    - 연결 풀 크기 조정
     *    - 압축 전송 옵션
     *    - CDN 연동 고려
     * 
     * 4. 보안 강화:
     *    - 서버사이드 암호화 설정
     *    - 클라이언트 암호화 옵션
     *    - IAM 역할 최소 권한 원칙
     * 
     * 5. 확장성 고려:
     *    - 멀티 리전 지원
     *    - 스토리지 클래스 자동 관리
     *    - 대용량 배치 작업 지원
     */
}
