package com.ryuqq.aws.s3.types;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class S3Tag {
    
    /**
     * 태그 키-값 맵
     */
    @Builder.Default
    private Map<String, String> tags = new HashMap<>();
    
    /**
     * 태그 추가
     * 
     * @param key 태그 키
     * @param value 태그 값
     * @return this (fluent API)
     */
    public S3Tag addTag(String key, String value) {
        validateTag(key, value);
        tags.put(key, value);
        return this;
    }
    
    /**
     * 여러 태그 추가
     * 
     * @param newTags 추가할 태그들
     * @return this (fluent API)
     */
    public S3Tag addTags(Map<String, String> newTags) {
        newTags.forEach(this::addTag);
        return this;
    }
    
    /**
     * 태그 제거
     * 
     * @param key 제거할 태그 키
     * @return this (fluent API)
     */
    public S3Tag removeTag(String key) {
        tags.remove(key);
        return this;
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
        if (tags.size() >= 10) {
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
     * @return this (fluent API)
     */
    public S3Tag withEnvironment(String environment) {
        return addTag("Environment", environment);
    }
    
    /**
     * 프로젝트 태그 추가 (편의 메서드)
     * 
     * @param project 프로젝트명
     * @return this (fluent API)
     */
    public S3Tag withProject(String project) {
        return addTag("Project", project);
    }
    
    /**
     * 소유자 태그 추가 (편의 메서드)
     * 
     * @param owner 소유자
     * @return this (fluent API)
     */
    public S3Tag withOwner(String owner) {
        return addTag("Owner", owner);
    }
    
    /**
     * 비용 센터 태그 추가 (편의 메서드)
     * 
     * @param costCenter 비용 센터
     * @return this (fluent API)
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
        return S3Tag.builder().tags(tagMap).build();
    }
}