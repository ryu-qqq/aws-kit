package com.ryuqq.aws.s3.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * S3 클라이언트 설정 프로퍼티
 */
@Data
@Component
@ConfigurationProperties(prefix = "aws.s3")
public class S3Properties {

    /**
     * 기본 AWS 리전
     */
    private String region = "us-east-1";

    /**
     * 멀티파트 업로드 임계값 (bytes)
     */
    private long multipartThreshold = 5 * 1024 * 1024; // 5MB

    /**
     * Presigned URL 기본 만료 시간
     */
    private Duration presignedUrlExpiry = Duration.ofHours(1);
}
