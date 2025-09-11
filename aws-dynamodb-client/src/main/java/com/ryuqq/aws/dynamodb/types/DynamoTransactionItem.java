package com.ryuqq.aws.dynamodb.types;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a single item in a DynamoDB transaction.
 * Library-specific type to avoid exposing AWS SDK types in public API.
 */
public final class DynamoTransactionItem {
    
    public enum TransactionType {
        PUT, UPDATE, DELETE, CONDITION_CHECK
    }
    
    private final TransactionType type;
    private final Object item;
    private final DynamoKey key;
    private final String tableName;
    private final String expression;
    private final Object[] values;
    
    private DynamoTransactionItem(TransactionType type, Object item, DynamoKey key, 
                                String tableName, String expression, Object[] values) {
        this.type = type;
        this.item = item;
        this.key = key;
        this.tableName = tableName;
        this.expression = expression;
        this.values = values != null ? Arrays.copyOf(values, values.length) : null;
    }
    
    public TransactionType getType() {
        return type;
    }
    
    public Object getItem() {
        return item;
    }
    
    public DynamoKey getKey() {
        return key;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public String getExpression() {
        return expression;
    }
    
    public Object[] getValues() {
        return values != null ? Arrays.copyOf(values, values.length) : null;
    }
    
    public static DynamoTransactionItem put(Object item, String tableName) {
        Objects.requireNonNull(item, "Item cannot be null");
        Objects.requireNonNull(tableName, "Table name cannot be null");
        return new DynamoTransactionItem(TransactionType.PUT, item, null, tableName, null, null);
    }
    
    public static DynamoTransactionItem update(DynamoKey key, String tableName, 
                                             String updateExpression, Object... values) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(tableName, "Table name cannot be null");
        Objects.requireNonNull(updateExpression, "Update expression cannot be null");
        return new DynamoTransactionItem(TransactionType.UPDATE, null, key, tableName, updateExpression, values);
    }
    
    public static DynamoTransactionItem delete(DynamoKey key, String tableName) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(tableName, "Table name cannot be null");
        return new DynamoTransactionItem(TransactionType.DELETE, null, key, tableName, null, null);
    }
    
    public static DynamoTransactionItem conditionCheck(DynamoKey key, String tableName, 
                                                     String conditionExpression, Object... values) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(tableName, "Table name cannot be null");
        Objects.requireNonNull(conditionExpression, "Condition expression cannot be null");
        return new DynamoTransactionItem(TransactionType.CONDITION_CHECK, null, key, tableName, conditionExpression, values);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DynamoTransactionItem that = (DynamoTransactionItem) o;
        return type == that.type &&
               Objects.equals(item, that.item) &&
               Objects.equals(key, that.key) &&
               Objects.equals(tableName, that.tableName) &&
               Objects.equals(expression, that.expression) &&
               Arrays.equals(values, that.values);
    }
    
    @Override
    public int hashCode() {
        int result = Objects.hash(type, item, key, tableName, expression);
        result = 31 * result + Arrays.hashCode(values);
        return result;
    }
    
    @Override
    public String toString() {
        return "DynamoTransactionItem{" +
               "type=" + type +
               ", item=" + item +
               ", key=" + key +
               ", tableName='" + tableName + '\'' +
               ", expression='" + expression + '\'' +
               ", values=" + Arrays.toString(values) +
               '}';
    }
}