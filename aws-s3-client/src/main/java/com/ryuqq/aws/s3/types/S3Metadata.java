package com.ryuqq.aws.s3.types;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * S3 객체 메타데이터
 *
 * 한국어 설명:
 * S3 객체의 메타데이터 정보를 담는 타입입니다.
 * 시스템 메타데이터와 사용자 정의 메타데이터를 모두 포함합니다.
 */
public record S3Metadata(
    /**
     * 객체 크기 (bytes)
     */
    Long contentLength,
    
    /**
     * MIME 타입
     */
    String contentType,
    
    /**
     * 객체의 ETag (무결성 검증용)
     */
    String eTag,
    
    /**
     * 마지막 수정 시간
     */
    Instant lastModified,
    
    /**
     * 버전 ID (버전 관리 활성화된 경우)
     */
    String versionId,
    
    /**
     * 스토리지 클래스
     */
    String storageClass,
    
    /**
     * 서버사이드 암호화 상태
     */
    String serverSideEncryption,
    
    /**
     * 사용자 정의 메타데이터
     * HTTP 헤더로 전송되며 x-amz-meta- 접두사가 붙음
     */
    Map<String, String> userMetadata,
    
    /**
     * 캐시 제어 헤더
     */
    String cacheControl,
    
    /**
     * 콘텐츠 인코딩
     */
    String contentEncoding,
    
    /**
     * 콘텐츠 처리 방식 (inline/attachment)
     */
    String contentDisposition
) {

    /**
     * Builder 패턴을 위한 정적 메서드
     *
     * @return S3Metadata Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 기본 메타데이터로 S3Metadata 생성
     *
     * @param contentLength 콘텐츠 길이
     * @param contentType 콘텐츠 타입
     * @param eTag ETag
     * @return S3Metadata 객체
     */
    public static S3Metadata of(Long contentLength, String contentType, String eTag) {
        return new S3Metadata(
            contentLength, contentType, eTag, null, null, null, null,
            null, null, null, null
        );
    }

    /**
     * Builder 클래스
     */
    public static final class Builder {
        private Long contentLength;
        private String contentType;
        private String eTag;
        private Instant lastModified;
        private String versionId;
        private String storageClass;
        private String serverSideEncryption;
        private Map<String, String> userMetadata;
        private String cacheControl;
        private String contentEncoding;
        private String contentDisposition;

        private Builder() {}

        /**
         * 콘텐츠 길이 설정
         */
        public Builder contentLength(Long contentLength) {
            this.contentLength = contentLength;
            return this;
        }

        /**
         * 콘텐츠 타입 설정
         */
        public Builder contentType(String contentType) {
            this.contentType = contentType;
            return this;
        }

        /**
         * ETag 설정
         */
        public Builder eTag(String eTag) {
            this.eTag = eTag;
            return this;
        }

        /**
         * 마지막 수정 시간 설정
         */
        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        /**
         * 버전 ID 설정
         */
        public Builder versionId(String versionId) {
            this.versionId = versionId;
            return this;
        }

        /**
         * 스토리지 클래스 설정
         */
        public Builder storageClass(String storageClass) {
            this.storageClass = storageClass;
            return this;
        }

        /**
         * 서버사이드 암호화 설정
         */
        public Builder serverSideEncryption(String serverSideEncryption) {
            this.serverSideEncryption = serverSideEncryption;
            return this;
        }

        /**
         * 사용자 메타데이터 설정
         */
        public Builder userMetadata(Map<String, String> userMetadata) {
            this.userMetadata = userMetadata;
            return this;
        }

        /**
         * 캐시 제어 설정
         */
        public Builder cacheControl(String cacheControl) {
            this.cacheControl = cacheControl;
            return this;
        }

        /**
         * 콘텐츠 인코딩 설정
         */
        public Builder contentEncoding(String contentEncoding) {
            this.contentEncoding = contentEncoding;
            return this;
        }

        /**
         * 콘텐츠 처리 방식 설정
         */
        public Builder contentDisposition(String contentDisposition) {
            this.contentDisposition = contentDisposition;
            return this;
        }

        /**
         * S3Metadata 빌드
         */
        public S3Metadata build() {
            return new S3Metadata(
                contentLength, contentType, eTag, lastModified, versionId,
                storageClass, serverSideEncryption, userMetadata,
                cacheControl, contentEncoding, contentDisposition
            );
        }
    }
}