package com.ryuqq.aws.dynamodb.adapter;

import com.ryuqq.aws.dynamodb.types.*;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.ConditionCheck;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter class for converting between library types and AWS SDK types.
 * This class encapsulates all AWS SDK dependencies to keep them out of public APIs.
 */
public final class DynamoTypeAdapter {
    
    private DynamoTypeAdapter() {
        // Utility class
    }
    
    /**
     * Convert library DynamoKey to AWS SDK Key
     */
    public static Key toAwsKey(DynamoKey dynamoKey) {
        if (dynamoKey == null) {
            return null;
        }
        
        Map<String, Object> keyAttributes = dynamoKey.getKeyAttributes();
        
        if (keyAttributes.size() == 1) {
            Map.Entry<String, Object> entry = keyAttributes.entrySet().iterator().next();
            Object partitionValue = convertToKeyValue(entry.getValue());
            return buildKey(partitionValue, null);
        } else if (keyAttributes.size() == 2) {
            // Assume first is partition key, second is sort key
            Object[] values = keyAttributes.values().toArray();
            Object partitionValue = convertToKeyValue(values[0]);
            Object sortValue = convertToKeyValue(values[1]);
            return buildKey(partitionValue, sortValue);
        }
        
        throw new IllegalArgumentException("DynamoKey must have 1 or 2 attributes");
    }
    
    /**
     * Convert library DynamoQuery to AWS SDK QueryConditional
     */
    public static QueryConditional toAwsQueryConditional(DynamoQuery dynamoQuery) {
        if (dynamoQuery == null) {
            return null;
        }
        
        Object partitionValue = convertToKeyValue(dynamoQuery.getPartitionValue());
        
        // Start with partition key condition
        Key partitionKey = buildKey(partitionValue, null);
        
        if (!dynamoQuery.hasSortKeyCondition()) {
            return QueryConditional.keyEqualTo(partitionKey);
        }
        
        switch (dynamoQuery.getOperator()) {
            case EQUAL:
                Object sortValue = convertToKeyValue(dynamoQuery.getSortValue());
                return QueryConditional.keyEqualTo(buildKey(partitionValue, sortValue));
                
            case LESS_THAN:
                sortValue = convertToKeyValue(dynamoQuery.getSortValue());
                return QueryConditional.sortLessThan(buildKey(partitionValue, sortValue));
                
            case LESS_THAN_OR_EQUAL:
                sortValue = convertToKeyValue(dynamoQuery.getSortValue());
                return QueryConditional.sortLessThanOrEqualTo(buildKey(partitionValue, sortValue));
                
            case GREATER_THAN:
                sortValue = convertToKeyValue(dynamoQuery.getSortValue());
                return QueryConditional.sortGreaterThan(buildKey(partitionValue, sortValue));
                
            case GREATER_THAN_OR_EQUAL:
                sortValue = convertToKeyValue(dynamoQuery.getSortValue());
                return QueryConditional.sortGreaterThanOrEqualTo(buildKey(partitionValue, sortValue));
                
            case BETWEEN:
                Object startValue = convertToKeyValue(dynamoQuery.getSortValue());
                Object endValue = convertToKeyValue(dynamoQuery.getSortValueEnd());
                Key startKey = buildKey(partitionValue, startValue);
                Key endKey = buildKey(partitionValue, endValue);
                return QueryConditional.sortBetween(startKey, endKey);
                
            case BEGINS_WITH:
                sortValue = convertToKeyValue(dynamoQuery.getSortValue());
                return QueryConditional.sortBeginsWith(buildKey(partitionValue, sortValue));
                
            default:
                throw new IllegalArgumentException("Unsupported operator: " + dynamoQuery.getOperator());
        }
    }
    
    /**
     * Convert library DynamoTransaction to AWS SDK TransactWriteItem list
     */
    public static List<TransactWriteItem> toAwsTransactWriteItems(DynamoTransaction dynamoTransaction) {
        if (dynamoTransaction == null || dynamoTransaction.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<TransactWriteItem> awsItems = new ArrayList<>();
        
        for (DynamoTransactionItem item : dynamoTransaction.getItems()) {
            TransactWriteItem.Builder itemBuilder = TransactWriteItem.builder();
            
            switch (item.getType()) {
                case PUT:
                    itemBuilder.put(Put.builder()
                            .tableName(item.getTableName())
                            .item(convertItemToAttributeValueMap(item.getItem()))
                            .build());
                    break;
                    
                case UPDATE:
                    Update.Builder updateBuilder = Update.builder()
                            .tableName(item.getTableName())
                            .key(convertKeyToAttributeValueMap(item.getKey()))
                            .updateExpression(item.getExpression());
                    
                    if (item.getValues() != null && item.getValues().length > 0) {
                        updateBuilder.expressionAttributeValues(convertValuesToAttributeValueMap(item.getValues()));
                    }
                    
                    itemBuilder.update(updateBuilder.build());
                    break;
                    
                case DELETE:
                    itemBuilder.delete(Delete.builder()
                            .tableName(item.getTableName())
                            .key(convertKeyToAttributeValueMap(item.getKey()))
                            .build());
                    break;
                    
                case CONDITION_CHECK:
                    ConditionCheck.Builder conditionBuilder = ConditionCheck.builder()
                            .tableName(item.getTableName())
                            .key(convertKeyToAttributeValueMap(item.getKey()))
                            .conditionExpression(item.getExpression());
                    
                    if (item.getValues() != null && item.getValues().length > 0) {
                        conditionBuilder.expressionAttributeValues(convertValuesToAttributeValueMap(item.getValues()));
                    }
                    
                    itemBuilder.conditionCheck(conditionBuilder.build());
                    break;
                    
                default:
                    throw new IllegalArgumentException("Unsupported transaction type: " + item.getType());
            }
            
            awsItems.add(itemBuilder.build());
        }
        
        return awsItems;
    }
    
    private static Map<String, AttributeValue> convertItemToAttributeValueMap(Object item) {
        // This is a simplified conversion - in real implementation, you'd use
        // DynamoDB Enhanced Client's AttributeConverterProvider or similar
        Map<String, AttributeValue> attributeMap = new HashMap<>();
        
        // For now, return empty map - this should be implemented based on your
        // specific object mapping requirements
        // You might want to use reflection or annotations to convert objects
        
        return attributeMap;
    }
    
    private static Map<String, AttributeValue> convertKeyToAttributeValueMap(DynamoKey key) {
        Map<String, AttributeValue> attributeMap = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : key.getKeyAttributes().entrySet()) {
            attributeMap.put(entry.getKey(), convertValueToAttributeValue(entry.getValue()));
        }
        
        return attributeMap;
    }
    
    private static Map<String, AttributeValue> convertValuesToAttributeValueMap(Object[] values) {
        Map<String, AttributeValue> attributeMap = new HashMap<>();
        
        for (int i = 0; i < values.length; i++) {
            attributeMap.put(":val" + i, convertValueToAttributeValue(values[i]));
        }
        
        return attributeMap;
    }
    
    private static AttributeValue convertValueToAttributeValue(Object value) {
        if (value == null) {
            return AttributeValue.builder().nul(true).build();
        }
        
        if (value instanceof String) {
            return AttributeValue.builder().s((String) value).build();
        }
        
        if (value instanceof Number) {
            return AttributeValue.builder().n(value.toString()).build();
        }
        
        if (value instanceof Boolean) {
            return AttributeValue.builder().bool((Boolean) value).build();
        }
        
        // Add more type conversions as needed
        return AttributeValue.builder().s(value.toString()).build();
    }
    
    /**
     * Convert Object to appropriate type for Key builder
     */
    private static Object convertToKeyValue(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Key value cannot be null");
        }
        
        // Key builder accepts String, Number, or SdkBytes
        if (value instanceof String || value instanceof Number) {
            return value;
        }
        
        if (value instanceof byte[]) {
            return software.amazon.awssdk.core.SdkBytes.fromByteArray((byte[]) value);
        }
        
        // Convert other types to String
        return value.toString();
    }
    
    /**
     * Build a Key with proper type casting
     */
    private static Key buildKey(Object partitionValue, Object sortValue) {
        Key.Builder builder = Key.builder();
        
        // Cast partition value to appropriate type
        if (partitionValue instanceof String) {
            builder.partitionValue((String) partitionValue);
        } else if (partitionValue instanceof Number) {
            builder.partitionValue((Number) partitionValue);
        } else if (partitionValue instanceof software.amazon.awssdk.core.SdkBytes) {
            builder.partitionValue((software.amazon.awssdk.core.SdkBytes) partitionValue);
        } else {
            builder.partitionValue(partitionValue.toString());
        }
        
        // Cast sort value if present
        if (sortValue != null) {
            if (sortValue instanceof String) {
                builder.sortValue((String) sortValue);
            } else if (sortValue instanceof Number) {
                builder.sortValue((Number) sortValue);
            } else if (sortValue instanceof software.amazon.awssdk.core.SdkBytes) {
                builder.sortValue((software.amazon.awssdk.core.SdkBytes) sortValue);
            } else {
                builder.sortValue(sortValue.toString());
            }
        }
        
        return builder.build();
    }
}