package com.ryuqq.aws.s3.types;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * S3 객체 메타데이터
 * 
 * 한국어 설명:
 * S3 객체의 메타데이터 정보를 담는 타입입니다.
 * 시스템 메타데이터와 사용자 정의 메타데이터를 모두 포함합니다.
 */
@Data
@Builder
public class S3Metadata {
    
    /**
     * 객체 크기 (bytes)
     */
    private Long contentLength;
    
    /**
     * MIME 타입
     */
    private String contentType;
    
    /**
     * 객체의 ETag (무결성 검증용)
     */
    private String eTag;
    
    /**
     * 마지막 수정 시간
     */
    private Instant lastModified;
    
    /**
     * 버전 ID (버전 관리 활성화된 경우)
     */
    private String versionId;
    
    /**
     * 스토리지 클래스
     */
    private String storageClass;
    
    /**
     * 서버사이드 암호화 상태
     */
    private String serverSideEncryption;
    
    /**
     * 사용자 정의 메타데이터
     * HTTP 헤더로 전송되며 x-amz-meta- 접두사가 붙음
     */
    private Map<String, String> userMetadata;
    
    /**
     * 캐시 제어 헤더
     */
    private String cacheControl;
    
    /**
     * 콘텐츠 인코딩
     */
    private String contentEncoding;
    
    /**
     * 콘텐츠 처리 방식 (inline/attachment)
     */
    private String contentDisposition;
}