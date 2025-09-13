package com.ryuqq.aws.dynamodb.types;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Library-specific key representation for DynamoDB operations.
 * Replaces AWS SDK Key to avoid exposing SDK types in public API.
 * 
 * DynamoDB 작업을 위한 라이브러리 전용 키 표현 클래스
 * 
 * <p>이 클래스는 AWS SDK의 Key 타입을 대체하여 공개 API에서 SDK 타입 노출을 방지합니다.
 * DynamoDB의 파티션 키(Partition Key)와 정렬 키(Sort Key)를 간편하게 다룰 수 있도록 
 * Builder 패턴과 정적 팩토리 메소드를 제공합니다.</p>
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>파티션 키만 있는 단순한 키 생성</li>
 *   <li>파티션 키 + 정렬 키를 포함한 복합 키 생성</li>
 *   <li>Builder 패턴을 통한 유연한 키 구성</li>
 *   <li>타입 안전성과 null 체크 제공</li>
 * </ul>
 * 
 * <p>사용 예시:</p>
 * <pre>
 * // 단순 키 (파티션 키만)
 * DynamoKey simpleKey = DynamoKey.partitionKey("userId", "12345");
 * 
 * // 복합 키 (파티션 키 + 정렬 키)
 * DynamoKey compositeKey = DynamoKey.sortKey("userId", "12345", "timestamp", 1640995200L);
 * 
 * // Builder 패턴 사용
 * DynamoKey builderKey = DynamoKey.builder()
 *     .partitionValue("status", "ACTIVE")
 *     .sortValue("createdAt", "2023-01-01")
 *     .build();
 * </pre>
 */
public final class DynamoKey {
    
    /** 키 속성들을 저장하는 불변 맵 (파티션 키, 정렬 키) */
    private final Map<String, Object> keyAttributes;
    
    /**
     * Builder로부터 DynamoKey 인스턴스를 생성합니다.
     * 
     * @param builder 키 속성이 설정된 Builder 인스턴스
     */
    private DynamoKey(Builder builder) {
        this.keyAttributes = Map.copyOf(builder.keyAttributes);
    }
    
    /**
     * 모든 키 속성을 반환합니다.
     * 
     * @return 키 속성들의 불변 맵 (속성명 → 값)
     */
    public Map<String, Object> getKeyAttributes() {
        return keyAttributes;
    }
    
    /**
     * 파티션 키의 값을 반환합니다.
     * 
     * @return 파티션 키 값 (첫 번째 키 속성의 값)
     */
    public Object getPartitionValue() {
        return keyAttributes.values().iterator().next();
    }
    
    /**
     * 파티션 키의 속성명을 반환합니다.
     * 
     * @return 파티션 키 속성명 (첫 번째 키 속성의 이름)
     */
    public String getPartitionKey() {
        return keyAttributes.keySet().iterator().next();
    }
    
    /**
     * DynamoKey Builder 인스턴스를 생성합니다.
     * 
     * @return 새로운 Builder 인스턴스
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 파티션 키만 있는 단순한 DynamoKey를 생성합니다.
     * 
     * @param attributeName 파티션 키 속성명
     * @param value 파티션 키 값
     * @return 생성된 DynamoKey 인스턴스
     * 
     * 사용 예시:
     * <pre>
     * DynamoKey key = DynamoKey.partitionKey("userId", "12345");
     * </pre>
     */
    public static DynamoKey partitionKey(String attributeName, Object value) {
        return builder().partitionValue(attributeName, value).build();
    }
    
    /**
     * 파티션 키와 정렬 키를 모두 포함한 복합 DynamoKey를 생성합니다.
     * 
     * @param partitionName 파티션 키 속성명
     * @param partitionValue 파티션 키 값
     * @param sortName 정렬 키 속성명
     * @param sortValue 정렬 키 값
     * @return 생성된 복합 DynamoKey 인스턴스
     * 
     * 사용 예시:
     * <pre>
     * DynamoKey key = DynamoKey.sortKey("userId", "12345", "timestamp", 1640995200L);
     * </pre>
     */
    public static DynamoKey sortKey(String partitionName, Object partitionValue, 
                                   String sortName, Object sortValue) {
        return builder()
                .partitionValue(partitionName, partitionValue)
                .sortValue(sortName, sortValue)
                .build();
    }
    
    /**
     * DynamoKey 객체를 생성하기 위한 Builder 클래스
     * 
     * <p>Builder 패턴을 사용하여 DynamoKey를 유연하게 생성할 수 있습니다.
     * 파티션 키와 정렬 키를 순서에 관계없이 설정할 수 있으며,
     * null 값에 대한 유효성 검사를 자동으로 수행합니다.</p>
     * 
     * 사용 예시:
     * <pre>
     * DynamoKey key = DynamoKey.builder()
     *     .partitionValue("status", "ACTIVE")
     *     .sortValue("createdAt", "2023-01-01")
     *     .build();
     * </pre>
     */
    public static final class Builder {
        /** 키 속성들을 저장하는 가변 맵 */
        private final Map<String, Object> keyAttributes = new HashMap<>();
        
        /**
         * 파티션 키 속성을 설정합니다.
         * 
         * @param attributeName 파티션 키 속성명 (null 불가)
         * @param value 파티션 키 값 (null 불가)
         * @return 이 Builder 인스턴스 (메소드 체이닝을 위한)
         * @throws NullPointerException 매개변수 중 하나라도 null인 경우
         */
        public Builder partitionValue(String attributeName, Object value) {
            Objects.requireNonNull(attributeName, "파티션 키 속성명은 null일 수 없습니다");
            Objects.requireNonNull(value, "파티션 키 값은 null일 수 없습니다");
            keyAttributes.put(attributeName, value);
            return this;
        }
        
        /**
         * 정렬 키 속성을 설정합니다.
         * 
         * @param attributeName 정렬 키 속성명 (null 불가)
         * @param value 정렬 키 값 (null 불가)
         * @return 이 Builder 인스턴스 (메소드 체이닝을 위한)
         * @throws NullPointerException 매개변수 중 하나라도 null인 경우
         */
        public Builder sortValue(String attributeName, Object value) {
            Objects.requireNonNull(attributeName, "정렬 키 속성명은 null일 수 없습니다");
            Objects.requireNonNull(value, "정렬 키 값은 null일 수 없습니다");
            keyAttributes.put(attributeName, value);
            return this;
        }
        
        /**
         * 설정된 속성들로 DynamoKey 인스턴스를 생성합니다.
         * 
         * @return 생성된 DynamoKey 인스턴스
         * @throws IllegalStateException 키 속성이 하나도 설정되지 않은 경우
         * 
         * <p><strong>주의:</strong> 최소 한 개의 키 속성(파티션 키 또는 정렬 키)은 반드시 설정되어야 합니다.</p>
         */
        public DynamoKey build() {
            if (keyAttributes.isEmpty()) {
                throw new IllegalStateException("최소 한 개의 키 속성은 반드시 지정되어야 합니다");
            }
            return new DynamoKey(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamoKey dynamoKey = (DynamoKey) o;
        return Objects.equals(keyAttributes, dynamoKey.keyAttributes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(keyAttributes);
    }
    
    @Override
    public String toString() {
        return "DynamoKey{keyAttributes=" + keyAttributes + "}";
    }
}