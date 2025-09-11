package com.ryuqq.aws.dynamodb.types;

import java.util.Objects;

/**
 * Library-specific query conditional representation for DynamoDB operations.
 * Replaces AWS SDK QueryConditional to avoid exposing SDK types in public API.
 */
public final class DynamoQuery {
    
    private final String partitionKey;
    private final Object partitionValue;
    private final String sortKey;
    private final ComparisonOperator operator;
    private final Object sortValue;
    private final Object sortValueEnd; // For between operations
    
    public enum ComparisonOperator {
        EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        BETWEEN,
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
    
    public boolean hasSortKeyCondition() {
        return sortKey != null && operator != null;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static DynamoQuery keyEqual(String partitionKey, Object partitionValue) {
        return builder()
                .partitionKeyEqual(partitionKey, partitionValue)
                .build();
    }
    
    public static DynamoQuery keyEqualAndSortEqual(String partitionKey, Object partitionValue,
                                                  String sortKey, Object sortValue) {
        return builder()
                .partitionKeyEqual(partitionKey, partitionValue)
                .sortKeyEqual(sortKey, sortValue)
                .build();
    }
    
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