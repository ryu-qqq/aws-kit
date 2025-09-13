package com.ryuqq.aws.s3.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * S3 클라이언트 설정 프로퍼티
 * 
 * 한국어 설명:
 * S3 클라이언트의 다양한 설정을 관리합니다.
 * application.yml에서 aws.s3 prefix로 설정 가능합니다.
 */
@Component
@ConfigurationProperties(prefix = "aws.s3")
public record S3Properties(
    /**
     * 기본 AWS 리전
     * 
     * 한국어 설명:
     * S3 버킷이 위치한 AWS 리전을 지정합니다.
     * 이 값은 aws-sdk-commons의 공통 Region 빈으로 사용되거나,
     * S3 특정 리전 설정이 필요한 경우 사용될 수 있습니다.
     * 
     * 예: ap-northeast-2 (서울), us-east-1 (버지니아)
     */
    String region,

    /**
     * 멀티파트 업로드 임계값 (bytes)
     * 
     * 한국어 설명:
     * 이 크기를 초과하는 파일은 자동으로 멀티파트 업로드를 사용합니다.
     * 멀티파트 업로드는 대용량 파일을 여러 부분으로 나누어 병렬 전송하며,
     * 네트워크 오류 시 실패한 부분만 재전송할 수 있어 안정적입니다.
     * 
     * 기본값: 5MB (AWS S3 최소 멀티파트 크기)
     */
    long multipartThreshold,

    /**
     * Presigned URL 기본 만료 시간
     * 
     * 한국어 설명:
     * generatePresignedUrl 메서드에서 만료 시간을 지정하지 않은 경우 사용되는 기본값입니다.
     * 보안을 위해 가능한 짧게 설정하는 것이 좋습니다.
     * 
     * 최대값: 7일 (AWS S3 제한)
     */
    Duration presignedUrlExpiry,
    
    /**
     * 업로드/다운로드 타임아웃 설정
     * 
     * 한국어 설명:
     * S3 작업의 타임아웃을 설정합니다.
     * 대용량 파일이나 느린 네트워크에서는 이 값을 늘려야 할 수 있습니다.
     */
    Duration operationTimeout,
    
    /**
     * 재시도 최대 횟수
     * 
     * 한국어 설명:
     * 네트워크 오류나 일시적인 S3 서비스 오류 시 재시도 횟수입니다.
     */
    int maxRetries,
    
    /**
     * 기본 스토리지 클래스
     * 
     * 한국어 설명:
     * 명시적으로 지정하지 않은 경우 사용되는 기본 스토리지 클래스입니다.
     * STANDARD: 자주 접근하는 데이터
     * STANDARD_IA: 덜 자주 접근하는 데이터
     * GLACIER: 아카이브 데이터
     */
    String defaultStorageClass,
    
    /**
     * 서버사이드 암호화 활성화
     * 
     * 한국어 설명:
     * S3에 저장되는 모든 객체를 자동으로 암호화합니다.
     * AES256 또는 aws:kms를 사용할 수 있습니다.
     */
    boolean serverSideEncryption,
    
    /**
     * 암호화 알고리즘
     * 
     * 한국어 설명:
     * serverSideEncryption이 true일 때 사용되는 암호화 알고리즘입니다.
     */
    String encryptionAlgorithm
) {
    
    public S3Properties {
        region = region != null ? region : "ap-northeast-2";
        multipartThreshold = multipartThreshold == 0 ? 5 * 1024 * 1024 : multipartThreshold; // 5MB
        presignedUrlExpiry = presignedUrlExpiry != null ? presignedUrlExpiry : Duration.ofHours(1);
        operationTimeout = operationTimeout != null ? operationTimeout : Duration.ofMinutes(5);
        maxRetries = maxRetries == 0 ? 3 : maxRetries;
        defaultStorageClass = defaultStorageClass != null ? defaultStorageClass : "STANDARD";
        encryptionAlgorithm = encryptionAlgorithm != null ? encryptionAlgorithm : "AES256";
    }
}
