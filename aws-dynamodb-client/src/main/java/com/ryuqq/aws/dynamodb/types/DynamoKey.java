package com.ryuqq.aws.dynamodb.types;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Library-specific key representation for DynamoDB operations.
 * Replaces AWS SDK Key to avoid exposing SDK types in public API.
 */
public final class DynamoKey {
    
    private final Map<String, Object> keyAttributes;
    
    private DynamoKey(Builder builder) {
        this.keyAttributes = Map.copyOf(builder.keyAttributes);
    }
    
    public Map<String, Object> getKeyAttributes() {
        return keyAttributes;
    }
    
    public Object getPartitionValue() {
        return keyAttributes.values().iterator().next();
    }
    
    public String getPartitionKey() {
        return keyAttributes.keySet().iterator().next();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static DynamoKey partitionKey(String attributeName, Object value) {
        return builder().partitionValue(attributeName, value).build();
    }
    
    public static DynamoKey sortKey(String partitionName, Object partitionValue, 
                                   String sortName, Object sortValue) {
        return builder()
                .partitionValue(partitionName, partitionValue)
                .sortValue(sortName, sortValue)
                .build();
    }
    
    public static final class Builder {
        private final Map<String, Object> keyAttributes = new HashMap<>();
        
        public Builder partitionValue(String attributeName, Object value) {
            Objects.requireNonNull(attributeName, "Partition key attribute name cannot be null");
            Objects.requireNonNull(value, "Partition key value cannot be null");
            keyAttributes.put(attributeName, value);
            return this;
        }
        
        public Builder sortValue(String attributeName, Object value) {
            Objects.requireNonNull(attributeName, "Sort key attribute name cannot be null");
            Objects.requireNonNull(value, "Sort key value cannot be null");
            keyAttributes.put(attributeName, value);
            return this;
        }
        
        public DynamoKey build() {
            if (keyAttributes.isEmpty()) {
                throw new IllegalStateException("At least one key attribute must be specified");
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