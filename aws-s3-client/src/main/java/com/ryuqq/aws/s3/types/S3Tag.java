package com.ryuqq.aws.s3.types;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S3 객체 태그
 *
 * 한국어 설명:
 * S3 객체에 대한 태그를 관리하는 타입입니다.
 * 태그는 객체 분류, 라이프사이클 관리, 비용 추적 등에 사용됩니다.
 *
 * 제한사항:
 * - 객체당 최대 10개의 태그
 * - 키: 최대 128자 유니코드 문자
 * - 값: 최대 256자 유니코드 문자
 */
public record S3Tag(
    /**
     * 태그 키-값 맵
     */
    Map<String, String> tags
) {
    
    /**
     * 기본 생성자 (빈 태그 맵으로 초기화)
     */
    public S3Tag() {
        this(new HashMap<>());
    }
    
    /**
     * 정규 생성자 (null 체크 및 기본값 설정)
     */
    public S3Tag(Map<String, String> tags) {
        this.tags = tags != null ? Map.copyOf(tags) : Map.of();
    }
    
    /**
     * 태그 추가
     * 
     * @param key 태그 키
     * @param value 태그 값
     * @return 새로운 S3Tag 인스턴스 (immutable)
     */
    public S3Tag addTag(String key, String value) {
        validateTag(key, value);
        Map<String, String> newTags = new HashMap<>(this.tags);
        newTags.put(key, value);
        return new S3Tag(newTags);
    }
    
    /**
     * 여러 태그 추가
     * 
     * @param newTags 추가할 태그들
     * @return 새로운 S3Tag 인스턴스 (immutable)
     */
    public S3Tag addTags(Map<String, String> newTags) {
        if (newTags == null || newTags.isEmpty()) {
            return this;
        }
        
        Map<String, String> combinedTags = new HashMap<>(this.tags);
        for (Map.Entry<String, String> entry : newTags.entrySet()) {
            validateTag(entry.getKey(), entry.getValue());
            combinedTags.put(entry.getKey(), entry.getValue());
        }
        return new S3Tag(combinedTags);
    }
    
    /**
     * 태그 제거
     * 
     * @param key 제거할 태그 키
     * @return 새로운 S3Tag 인스턴스 (immutable)
     */
    public S3Tag removeTag(String key) {
        if (!tags.containsKey(key)) {
            return this;
        }
        Map<String, String> newTags = new HashMap<>(this.tags);
        newTags.remove(key);
        return new S3Tag(newTags);
    }
    
    /**
     * 태그 개수 확인
     * 
     * @return 태그 개수
     */
    public int getTagCount() {
        return tags.size();
    }
    
    /**
     * 태그 키 집합 반환
     * 
     * @return 태그 키 집합
     */
    public Set<String> getTagKeys() {
        return tags.keySet();
    }
    
    /**
     * 특정 키의 태그 값 조회
     * 
     * @param key 태그 키
     * @return 태그 값 (없으면 null)
     */
    public String getTagValue(String key) {
        return tags.get(key);
    }
    
    /**
     * 태그 유효성 검증
     * 
     * @param key 태그 키
     * @param value 태그 값
     */
    private void validateTag(String key, String value) {
        if (tags.size() >= 10 && !tags.containsKey(key)) {
            throw new IllegalStateException("S3 객체는 최대 10개의 태그만 가질 수 있습니다");
        }
        if (key == null || key.isEmpty() || key.length() > 128) {
            throw new IllegalArgumentException("태그 키는 1-128자여야 합니다");
        }
        if (value != null && value.length() > 256) {
            throw new IllegalArgumentException("태그 값은 최대 256자까지 가능합니다");
        }
    }
    
    /**
     * 환경 태그 추가 (편의 메서드)
     * 
     * @param environment 환경 (dev, staging, prod)
     * @return 새로운 S3Tag 인스턴스 (immutable)
     */
    public S3Tag withEnvironment(String environment) {
        return addTag("Environment", environment);
    }
    
    /**
     * 프로젝트 태그 추가 (편의 메서드)
     * 
     * @param project 프로젝트명
     * @return 새로운 S3Tag 인스턴스 (immutable)
     */
    public S3Tag withProject(String project) {
        return addTag("Project", project);
    }
    
    /**
     * 소유자 태그 추가 (편의 메서드)
     * 
     * @param owner 소유자
     * @return 새로운 S3Tag 인스턴스 (immutable)
     */
    public S3Tag withOwner(String owner) {
        return addTag("Owner", owner);
    }
    
    /**
     * 비용 센터 태그 추가 (편의 메서드)
     * 
     * @param costCenter 비용 센터
     * @return 새로운 S3Tag 인스턴스 (immutable)
     */
    public S3Tag withCostCenter(String costCenter) {
        return addTag("CostCenter", costCenter);
    }
    
    /**
     * AWS SDK Tag 리스트로 변환
     * 
     * @return AWS SDK Tag 리스트
     */
    public Set<software.amazon.awssdk.services.s3.model.Tag> toAwsTags() {
        return tags.entrySet().stream()
                .map(entry -> software.amazon.awssdk.services.s3.model.Tag.builder()
                        .key(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .collect(Collectors.toSet());
    }
    
    /**
     * AWS SDK Tag 리스트에서 변환
     *
     * @param awsTags AWS SDK Tag 리스트
     * @return S3Tag 객체
     */
    public static S3Tag fromAwsTags(Set<software.amazon.awssdk.services.s3.model.Tag> awsTags) {
        Map<String, String> tagMap = awsTags.stream()
                .collect(Collectors.toMap(
                        software.amazon.awssdk.services.s3.model.Tag::key,
                        software.amazon.awssdk.services.s3.model.Tag::value
                ));
        return new S3Tag(tagMap);
    }

    /**
     * AWS SDK Tag 리스트에서 변환 (List 버전)
     *
     * @param awsTags AWS SDK Tag 리스트
     * @return S3Tag 객체
     */
    public static S3Tag fromAwsTags(List<software.amazon.awssdk.services.s3.model.Tag> awsTags) {
        Map<String, String> tagMap = awsTags.stream()
                .collect(Collectors.toMap(
                        software.amazon.awssdk.services.s3.model.Tag::key,
                        software.amazon.awssdk.services.s3.model.Tag::value
                ));
        return new S3Tag(tagMap);
    }

    /**
     * Builder 패턴을 위한 정적 메서드
     *
     * @return S3Tag Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 단일 태그로 S3Tag 생성
     *
     * @param key 태그 키
     * @param value 태그 값
     * @return S3Tag 객체
     */
    public static S3Tag of(String key, String value) {
        return new S3Tag(Map.of(key, value));
    }

    /**
     * 빈 S3Tag 생성
     *
     * @return 빈 S3Tag 객체
     */
    public static S3Tag empty() {
        return new S3Tag(Map.of());
    }

    /**
     * Builder 클래스
     */
    public static final class Builder {
        private final Map<String, String> tags = new HashMap<>();

        private Builder() {}

        /**
         * 태그 추가
         *
         * @param key 태그 키
         * @param value 태그 값
         * @return Builder
         */
        public Builder tag(String key, String value) {
            Objects.requireNonNull(key, "태그 키는 null일 수 없습니다");
            this.tags.put(key, value);
            return this;
        }

        /**
         * 태그 맵 설정
         *
         * @param tags 태그 맵
         * @return Builder
         */
        public Builder tags(Map<String, String> tags) {
            if (tags != null) {
                this.tags.putAll(tags);
            }
            return this;
        }

        /**
         * 환경 태그 추가
         *
         * @param environment 환경 (dev, staging, prod)
         * @return Builder
         */
        public Builder environment(String environment) {
            return tag("Environment", environment);
        }

        /**
         * 프로젝트 태그 추가
         *
         * @param project 프로젝트명
         * @return Builder
         */
        public Builder project(String project) {
            return tag("Project", project);
        }

        /**
         * 소유자 태그 추가
         *
         * @param owner 소유자
         * @return Builder
         */
        public Builder owner(String owner) {
            return tag("Owner", owner);
        }

        /**
         * 비용 센터 태그 추가
         *
         * @param costCenter 비용 센터
         * @return Builder
         */
        public Builder costCenter(String costCenter) {
            return tag("CostCenter", costCenter);
        }

        /**
         * S3Tag 빌드
         *
         * @return S3Tag 객체
         */
        public S3Tag build() {
            return new S3Tag(this.tags);
        }
    }
}