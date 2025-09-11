package com.ryuqq.aws.dynamodb.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Library-specific transaction representation for DynamoDB operations.
 * Replaces AWS SDK TransactWriteItem to avoid exposing SDK types in public API.
 */
public final class DynamoTransaction {
    
    private final List<DynamoTransactionItem> items;
    
    private DynamoTransaction(Builder builder) {
        this.items = List.copyOf(builder.items);
    }
    
    public List<DynamoTransactionItem> getItems() {
        return items;
    }
    
    public int size() {
        return items.size();
    }
    
    public boolean isEmpty() {
        return items.isEmpty();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static DynamoTransaction singlePut(Object item, String tableName) {
        return builder().put(item, tableName).build();
    }
    
    public static DynamoTransaction singleUpdate(DynamoKey key, String tableName, 
                                               String updateExpression, Object... values) {
        return builder().update(key, tableName, updateExpression, values).build();
    }
    
    public static DynamoTransaction singleDelete(DynamoKey key, String tableName) {
        return builder().delete(key, tableName).build();
    }
    
    public static final class Builder {
        private final List<DynamoTransactionItem> items = new ArrayList<>();
        
        public Builder put(Object item, String tableName) {
            Objects.requireNonNull(item, "Item cannot be null");
            Objects.requireNonNull(tableName, "Table name cannot be null");
            items.add(DynamoTransactionItem.put(item, tableName));
            return this;
        }
        
        public Builder update(DynamoKey key, String tableName, String updateExpression, Object... values) {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(tableName, "Table name cannot be null");
            Objects.requireNonNull(updateExpression, "Update expression cannot be null");
            items.add(DynamoTransactionItem.update(key, tableName, updateExpression, values));
            return this;
        }
        
        public Builder delete(DynamoKey key, String tableName) {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(tableName, "Table name cannot be null");
            items.add(DynamoTransactionItem.delete(key, tableName));
            return this;
        }
        
        public Builder conditionCheck(DynamoKey key, String tableName, String conditionExpression, Object... values) {
            Objects.requireNonNull(key, "Key cannot be null");
            Objects.requireNonNull(tableName, "Table name cannot be null");
            Objects.requireNonNull(conditionExpression, "Condition expression cannot be null");
            items.add(DynamoTransactionItem.conditionCheck(key, tableName, conditionExpression, values));
            return this;
        }
        
        public Builder addItem(DynamoTransactionItem item) {
            Objects.requireNonNull(item, "Transaction item cannot be null");
            items.add(item);
            return this;
        }
        
        public DynamoTransaction build() {
            if (items.isEmpty()) {
                throw new IllegalStateException("At least one transaction item must be specified");
            }
            if (items.size() > 100) {
                throw new IllegalStateException("Transaction cannot contain more than 100 items");
            }
            return new DynamoTransaction(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamoTransaction that = (DynamoTransaction) o;
        return Objects.equals(items, that.items);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(items);
    }
    
    @Override
    public String toString() {
        return "DynamoTransaction{items=" + items + "}";
    }
}