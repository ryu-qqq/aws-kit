package com.ryuqq.aws.dynamodb.types;

import java.util.Objects;

/**
 * Library-specific query conditional representation for DynamoDB operations.
 * Replaces AWS SDK QueryConditional to avoid exposing SDK types in public API.
 * 
 * DynamoDB 쿼리 작업을 위한 라이브러리 전용 쿼리 조건 표현 클래스
 * 
 * <p>이 클래스는 AWS SDK의 QueryConditional을 대체하여 공개 API에서 SDK 타입 노출을 방지합니다.
 * DynamoDB의 파티션 키 기반 쿼리와 정렬 키 조건을 조합하여 복잡한 쿼리를 표현할 수 있습니다.</p>
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>파티션 키 기반 기본 쿼리</li>
 *   <li>정렬 키 조건을 포함한 복합 쿼리</li>
 *   <li>다양한 비교 연산자 지원 (=, <, >, BETWEEN, BEGINS_WITH 등)</li>
 *   <li>Builder 패턴과 정적 팩토리 메소드 제공</li>
 * </ul>
 * 
 * <p>사용 예시:</p>
 * <pre>
 * // 파티션 키만으로 쿼리
 * DynamoQuery simpleQuery = DynamoQuery.keyEqual("userId", "12345");
 * 
 * // 파티션 키 + 정렬 키 조건
 * DynamoQuery complexQuery = DynamoQuery.builder()
 *     .partitionKeyEqual("userId", "12345")
 *     .sortKeyBetween("timestamp", 1640995200L, 1641081600L)
 *     .build();
 * 
 * // 문자열 시작 조건
 * DynamoQuery beginsWithQuery = DynamoQuery.keyEqualAndSortBeginsWith(
 *     "status", "ACTIVE", "name", "John");
 * </pre>
 */
public final class DynamoQuery {
    
    /** 파티션 키 속성명 */
    private final String partitionKey;
    /** 파티션 키 값 */
    private final Object partitionValue;
    /** 정렬 키 속성명 (선택사항) */
    private final String sortKey;
    /** 정렬 키 비교 연산자 (선택사항) */
    private final ComparisonOperator operator;
    /** 정렬 키 값 (선택사항) */
    private final Object sortValue;
    /** BETWEEN 연산을 위한 정렬 키 종료 값 (선택사항) */
    private final Object sortValueEnd; // For between operations
    
    /**
     * DynamoDB 정렬 키 쿠리에 사용할 수 있는 비교 연산자
     * 
     * <p>각 연산자의 의미:</p>
     * <ul>
     *   <li>EQUAL: 정확히 일치 (=)</li>
     *   <li>LESS_THAN: 보다 작음 (<)</li>
     *   <li>LESS_THAN_OR_EQUAL: 이하 (<=)</li>
     *   <li>GREATER_THAN: 보다 큼 (>)</li>
     *   <li>GREATER_THAN_OR_EQUAL: 이상 (>=)</li>
     *   <li>BETWEEN: 범위 내 값 (경계값 포함)</li>
     *   <li>BEGINS_WITH: 문자열 시작 패턴 (문자열 타입에만 사용)</li>
     * </ul>
     */
    public enum ComparisonOperator {
        /** 동등 비교 (=) */
        EQUAL,
        /** 미만 비교 (<) */
        LESS_THAN,
        /** 이하 비교 (<=) */
        LESS_THAN_OR_EQUAL,
        /** 초과 비교 (>) */
        GREATER_THAN,
        /** 이상 비교 (>=) */
        GREATER_THAN_OR_EQUAL,
        /** 범위 비교 (경계값 포함) */
        BETWEEN,
        /** 문자열 시작 패턴 */
        BEGINS_WITH
    }
    
    private DynamoQuery(Builder builder) {
        this.partitionKey = builder.partitionKey;
        this.partitionValue = builder.partitionValue;
        this.sortKey = builder.sortKey;
        this.operator = builder.operator;
        this.sortValue = builder.sortValue;
        this.sortValueEnd = builder.sortValueEnd;
    }
    
    public String getPartitionKey() {
        return partitionKey;
    }
    
    public Object getPartitionValue() {
        return partitionValue;
    }
    
    public String getSortKey() {
        return sortKey;
    }
    
    public ComparisonOperator getOperator() {
        return operator;
    }
    
    public Object getSortValue() {
        return sortValue;
    }
    
    public Object getSortValueEnd() {
        return sortValueEnd;
    }
    
    /**
     * 이 쿠리가 정렬 키 조건을 포함하고 있는지 확인합니다.
     * 
     * @return 정렬 키와 연산자가 모두 설정되어 있으면 true
     */
    public boolean hasSortKeyCondition() {
        return sortKey != null && operator != null;
    }
    
    /**
     * DynamoQuery Builder 인스턴스를 생성합니다.
     * 
     * @return 새로운 Builder 인스턴스
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 파티션 키만 사용하는 단순한 쿠리를 생성합니다.
     * 
     * @param partitionKey 파티션 키 속성명
     * @param partitionValue 파티션 키 값
     * @return 생성된 DynamoQuery 인스턴스
     */
    public static DynamoQuery keyEqual(String partitionKey, Object partitionValue) {
        return builder()
                .partitionKeyEqual(partitionKey, partitionValue)
                .build();
    }
    
    /**
     * 파티션 키와 정렬 키 동등 조건을 모두 사용하는 쿠리를 생성합니다.
     * 
     * @param partitionKey 파티션 키 속성명
     * @param partitionValue 파티션 키 값
     * @param sortKey 정렬 키 속성명
     * @param sortValue 정렬 키 값
     * @return 생성된 DynamoQuery 인스턴스
     */
    public static DynamoQuery keyEqualAndSortEqual(String partitionKey, Object partitionValue,
                                                  String sortKey, Object sortValue) {
        return builder()
                .partitionKeyEqual(partitionKey, partitionValue)
                .sortKeyEqual(sortKey, sortValue)
                .build();
    }
    
    /**
     * 파티션 키 동등 조건과 정렬 키 시작 문자열 조건을 사용하는 쿠리를 생성합니다.
     * 
     * @param partitionKey 파티션 키 속성명
     * @param partitionValue 파티션 키 값
     * @param sortKey 정렬 키 속성명
     * @param sortValue 정렬 키 시작 문자열
     * @return 생성된 DynamoQuery 인스턴스
     */
    public static DynamoQuery keyEqualAndSortBeginsWith(String partitionKey, Object partitionValue,
                                                       String sortKey, Object sortValue) {
        return builder()
                .partitionKeyEqual(partitionKey, partitionValue)
                .sortKeyBeginsWith(sortKey, sortValue)
                .build();
    }
    
    public static final class Builder {
        private String partitionKey;
        private Object partitionValue;
        private String sortKey;
        private ComparisonOperator operator;
        private Object sortValue;
        private Object sortValueEnd;
        
        public Builder partitionKeyEqual(String key, Object value) {
            Objects.requireNonNull(key, "Partition key cannot be null");
            Objects.requireNonNull(value, "Partition value cannot be null");
            this.partitionKey = key;
            this.partitionValue = value;
            return this;
        }
        
        public Builder sortKeyEqual(String key, Object value) {
            Objects.requireNonNull(key, "Sort key cannot be null");
            Objects.requireNonNull(value, "Sort value cannot be null");
            this.sortKey = key;
            this.operator = ComparisonOperator.EQUAL;
            this.sortValue = value;
            return this;
        }
        
        public Builder sortKeyLessThan(String key, Object value) {
            Objects.requireNonNull(key, "Sort key cannot be null");
            Objects.requireNonNull(value, "Sort value cannot be null");
            this.sortKey = key;
            this.operator = ComparisonOperator.LESS_THAN;
            this.sortValue = value;
            return this;
        }
        
        public Builder sortKeyLessThanOrEqual(String key, Object value) {
            Objects.requireNonNull(key, "Sort key cannot be null");
            Objects.requireNonNull(value, "Sort value cannot be null");
            this.sortKey = key;
            this.operator = ComparisonOperator.LESS_THAN_OR_EQUAL;
            this.sortValue = value;
            return this;
        }
        
        public Builder sortKeyGreaterThan(String key, Object value) {
            Objects.requireNonNull(key, "Sort key cannot be null");
            Objects.requireNonNull(value, "Sort value cannot be null");
            this.sortKey = key;
            this.operator = ComparisonOperator.GREATER_THAN;
            this.sortValue = value;
            return this;
        }
        
        public Builder sortKeyGreaterThanOrEqual(String key, Object value) {
            Objects.requireNonNull(key, "Sort key cannot be null");
            Objects.requireNonNull(value, "Sort value cannot be null");
            this.sortKey = key;
            this.operator = ComparisonOperator.GREATER_THAN_OR_EQUAL;
            this.sortValue = value;
            return this;
        }
        
        public Builder sortKeyBetween(String key, Object startValue, Object endValue) {
            Objects.requireNonNull(key, "Sort key cannot be null");
            Objects.requireNonNull(startValue, "Start value cannot be null");
            Objects.requireNonNull(endValue, "End value cannot be null");
            this.sortKey = key;
            this.operator = ComparisonOperator.BETWEEN;
            this.sortValue = startValue;
            this.sortValueEnd = endValue;
            return this;
        }
        
        public Builder sortKeyBeginsWith(String key, Object value) {
            Objects.requireNonNull(key, "Sort key cannot be null");
            Objects.requireNonNull(value, "Sort value cannot be null");
            this.sortKey = key;
            this.operator = ComparisonOperator.BEGINS_WITH;
            this.sortValue = value;
            return this;
        }
        
        public DynamoQuery build() {
            if (partitionKey == null || partitionValue == null) {
                throw new IllegalStateException("Partition key and value are required");
            }
            
            if (sortKey != null && operator == null) {
                throw new IllegalStateException("Sort key operator is required when sort key is specified");
            }
            
            return new DynamoQuery(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamoQuery that = (DynamoQuery) o;
        return Objects.equals(partitionKey, that.partitionKey) &&
               Objects.equals(partitionValue, that.partitionValue) &&
               Objects.equals(sortKey, that.sortKey) &&
               operator == that.operator &&
               Objects.equals(sortValue, that.sortValue) &&
               Objects.equals(sortValueEnd, that.sortValueEnd);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(partitionKey, partitionValue, sortKey, operator, sortValue, sortValueEnd);
    }
    
    @Override
    public String toString() {
        return "DynamoQuery{" +
               "partitionKey='" + partitionKey + '\'' +
               ", partitionValue=" + partitionValue +
               ", sortKey='" + sortKey + '\'' +
               ", operator=" + operator +
               ", sortValue=" + sortValue +
               ", sortValueEnd=" + sortValueEnd +
               '}';
    }
}