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
 * 
 * 라이브러리 타입과 AWS SDK 타입 간 변환을 위한 어댑터 클래스
 * 
 * <p>이 클래스는 AWS SDK의 모든 의존성을 캡슐화하여 공개 API에서 숨기는 역할을 합니다.
 * 사용자는 라이브러리의 타입(DynamoKey, DynamoQuery 등)만 사용하면 되고,
 * AWS SDK의 복잡한 타입은 내부에서 자동으로 변환됩니다.</p>
 * 
 * <p>주요 변환 기능:</p>
 * <ul>
 *   <li>DynamoKey → AWS SDK Key</li>
 *   <li>DynamoQuery → AWS SDK QueryConditional</li>
 *   <li>DynamoTransaction → AWS SDK TransactWriteItem 리스트</li>
 *   <li>일반 객체 → AttributeValue 맵</li>
 * </ul>
 */
public final class DynamoTypeAdapter {
    
    /**
     * 유틸리티 클래스이므로 인스턴스 생성 방지
     */
    private DynamoTypeAdapter() {
        // Utility class - 인스턴스화 방지
    }
    
    /**
     * Convert library DynamoKey to AWS SDK Key
     * 
     * 라이브러리의 DynamoKey를 AWS SDK의 Key로 변환합니다.
     * 
     * @param dynamoKey 변환할 라이브러리의 DynamoKey 객체
     * @return AWS SDK의 Key 객체 (null 입력 시 null 반환)
     * @throws IllegalArgumentException 키 속성이 1개 또는 2개가 아닌 경우
     * 
     * <p>지원하는 키 형태:</p>
     * <ul>
     *   <li>단일 키: 파티션 키만 있는 경우</li>
     *   <li>복합 키: 파티션 키 + 정렬 키가 있는 경우</li>
     * </ul>
     * 
     * 사용 예시:
     * <pre>
     * DynamoKey simpleKey = DynamoKey.of("userId", "12345");
     * Key awsKey = DynamoTypeAdapter.toAwsKey(simpleKey);
     * </pre>
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
     * 
     * 라이브러리의 DynamoQuery를 AWS SDK의 QueryConditional로 변환합니다.
     * 
     * @param dynamoQuery 변환할 라이브러리의 DynamoQuery 객체
     * @return AWS SDK의 QueryConditional 객체 (null 입력 시 null 반환)
     * @throws IllegalArgumentException 지원하지 않는 연산자인 경우
     * 
     * <p>지원하는 쿼리 조건:</p>
     * <ul>
     *   <li>파티션 키만: keyEqualTo</li>
     *   <li>정렬 키 조건: EQUAL, LESS_THAN, GREATER_THAN, BETWEEN, BEGINS_WITH</li>
     * </ul>
     * 
     * 사용 예시:
     * <pre>
     * DynamoQuery query = DynamoQuery.builder()
     *     .partitionKey("status", "ACTIVE")
     *     .sortKeyBeginsWith("name", "John")
     *     .build();
     * QueryConditional condition = DynamoTypeAdapter.toAwsQueryConditional(query);
     * </pre>
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
     * 
     * 라이브러리의 DynamoTransaction을 AWS SDK의 TransactWriteItem 리스트로 변환합니다.
     * 
     * @param dynamoTransaction 변환할 라이브러리의 DynamoTransaction 객체
     * @return AWS SDK의 TransactWriteItem 리스트 (빈 트랜잭션인 경우 빈 리스트)
     * @throws IllegalArgumentException 지원하지 않는 트랜잭션 타입인 경우
     * 
     * <p>지원하는 트랜잭션 타입:</p>
     * <ul>
     *   <li>PUT: 아이템 삽입 또는 업데이트</li>
     *   <li>UPDATE: 아이템 업데이트 (expression 사용)</li>
     *   <li>DELETE: 아이템 삭제</li>
     *   <li>CONDITION_CHECK: 조건 검사만 수행</li>
     * </ul>
     * 
     * 사용 예시:
     * <pre>
     * DynamoTransaction transaction = DynamoTransaction.builder()
     *     .addPut(user, "users")
     *     .addUpdate(userKey, "SET #status = :status", "ACTIVE")
     *     .build();
     * List<TransactWriteItem> items = DynamoTypeAdapter.toAwsTransactWriteItems(transaction);
     * </pre>
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
    
    /**
     * 객체를 DynamoDB AttributeValue 맵으로 변환합니다.
     * 
     * <p>이 메소드는 Java 객체의 필드를 리플렉션을 사용하여 검사하고,
     * 각 필드 값을 적절한 DynamoDB AttributeValue로 변환합니다.</p>
     * 
     * <p><strong>지원하는 타입:</strong></p>
     * <ul>
     *   <li>String → S (문자열)</li>
     *   <li>Number (Integer, Long, Double, etc.) → N (숫자)</li>
     *   <li>Boolean → BOOL</li>
     *   <li>List → L (리스트)</li>
     *   <li>Map → M (맵)</li>
     *   <li>null → NULL</li>
     *   <li>기타 객체 → 재귀적으로 변환</li>
     * </ul>
     * 
     * @param item 변환할 객체 (null 가능)
     * @return DynamoDB AttributeValue 맵
     * @throws RuntimeException 리플렉션 실행 중 오류가 발생한 경우
     * 
     * <p><strong>사용 예시:</strong></p>
     * <pre>
     * public class User {
     *     private String id;
     *     private String name;
     *     private Integer age;
     *     private Boolean active;
     *     // getters/setters...
     * }
     * 
     * User user = new User("123", "홍길동", 30, true);
     * Map<String, AttributeValue> attributeMap = convertItemToAttributeValueMap(user);
     * // Result: {"id": {S: "123"}, "name": {S: "홍길동"}, "age": {N: "30"}, "active": {BOOL: true}}
     * </pre>
     */
    private static Map<String, AttributeValue> convertItemToAttributeValueMap(Object item) {
        if (item == null) {
            return new HashMap<>();
        }
        
        Map<String, AttributeValue> attributeMap = new HashMap<>();
        Class<?> itemClass = item.getClass();
        
        // 기본 타입이나 래퍼 클래스인 경우 직접 변환
        if (isPrimitiveOrWrapper(itemClass) || item instanceof String) {
            // 단일 값 객체는 "value"라는 키로 저장
            attributeMap.put("value", convertObjectToAttributeValue(item));
            return attributeMap;
        }
        
        // 맵인 경우 직접 변환
        if (item instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) item;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                attributeMap.put(entry.getKey(), convertObjectToAttributeValue(entry.getValue()));
            }
            return attributeMap;
        }
        
        // 일반 객체는 리플렉션을 사용하여 필드 변환
        try {
            java.lang.reflect.Field[] fields = itemClass.getDeclaredFields();
            
            for (java.lang.reflect.Field field : fields) {
                // static, final 필드는 제외
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                    java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                    continue;
                }
                
                field.setAccessible(true);
                Object fieldValue = field.get(item);
                
                // null이 아닌 필드만 포함
                if (fieldValue != null) {
                    String fieldName = field.getName();
                    attributeMap.put(fieldName, convertObjectToAttributeValue(fieldValue));
                }
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Failed to convert object to AttributeValue map using reflection", e);
        }
        
        return attributeMap;
    }
    
    /**
     * Java 객체를 재귀적으로 DynamoDB AttributeValue로 변환합니다.
     * 
     * @param value 변환할 객체
     * @return 대응하는 DynamoDB AttributeValue
     */
    private static AttributeValue convertObjectToAttributeValue(Object value) {
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
        
        if (value instanceof byte[]) {
            return AttributeValue.builder()
                .b(software.amazon.awssdk.core.SdkBytes.fromByteArray((byte[]) value))
                .build();
        }
        
        // List 처리
        if (value instanceof java.util.Collection<?>) {
            java.util.Collection<?> collection = (java.util.Collection<?>) value;
            List<AttributeValue> attributeValues = new ArrayList<>();
            
            for (Object item : collection) {
                attributeValues.add(convertObjectToAttributeValue(item));
            }
            
            return AttributeValue.builder().l(attributeValues).build();
        }
        
        // Map 처리
        if (value instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            Map<String, AttributeValue> attributeMap = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                attributeMap.put(entry.getKey(), convertObjectToAttributeValue(entry.getValue()));
            }
            
            return AttributeValue.builder().m(attributeMap).build();
        }
        
        // 복잡한 객체는 재귀적으로 Map으로 변환 후 M 타입으로 래핑
        Map<String, AttributeValue> nestedMap = convertItemToAttributeValueMap(value);
        return AttributeValue.builder().m(nestedMap).build();
    }
    
    /**
     * 주어진 클래스가 기본 타입이나 래퍼 클래스인지 확인합니다.
     * 
     * @param clazz 확인할 클래스
     * @return 기본 타입이나 래퍼 클래스인 경우 true
     */
    private static boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() ||
               clazz == Boolean.class ||
               clazz == Integer.class ||
               clazz == Character.class ||
               clazz == Byte.class ||
               clazz == Short.class ||
               clazz == Double.class ||
               clazz == Long.class ||
               clazz == Float.class;
    }
    
    /**
     * DynamoKey를 DynamoDB AttributeValue 맵으로 변환합니다.
     * 
     * @param key 변환할 DynamoKey 객체
     * @return 키 속성을 포함하는 AttributeValue 맵
     * 
     * <p>키의 모든 속성(파티션 키, 정렬 키)을 순회하며
     * 각 값을 적절한 AttributeValue로 변환합니다.</p>
     */
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
    
    /**
     * Java 객체를 DynamoDB AttributeValue로 변환합니다.
     * 
     * @param value 변환할 Java 객체
     * @return 대응하는 DynamoDB AttributeValue
     * 
     * <p>지원하는 타입 변환:</p>
     * <ul>
     *   <li>null → NULL AttributeValue</li>
     *   <li>String → S (String) AttributeValue</li>
     *   <li>Number → N (Number) AttributeValue</li>
     *   <li>Boolean → BOOL AttributeValue</li>
     *   <li>기타 → S (toString() 사용)</li>
     * </ul>
     * 
     * <p><strong>참고:</strong> 필요에 따라 추가 타입 변환을 구현할 수 있습니다.</p>
     */
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
        
        // 필요에 따라 추가 타입 변환 구현
        return AttributeValue.builder().s(value.toString()).build();
    }
    
    /**
     * Convert Object to appropriate type for Key builder
     * 
     * 객체를 Key 빌더에 적합한 타입으로 변환합니다.
     * 
     * @param value 변환할 객체
     * @return Key 빌더에서 사용 가능한 타입 (String, Number, SdkBytes)
     * @throws IllegalArgumentException 값이 null인 경우
     * 
     * <p>지원하는 변환:</p>
     * <ul>
     *   <li>String → String (그대로 유지)</li>
     *   <li>Number → Number (그대로 유지)</li>
     *   <li>byte[] → SdkBytes</li>
     *   <li>기타 → String (toString() 사용)</li>
     * </ul>
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
     * 
     * 적절한 타입 캐스팅으로 Key를 생성합니다.
     * 
     * @param partitionValue 파티션 키 값 (필수)
     * @param sortValue 정렬 키 값 (선택사항, null 가능)
     * @return 생성된 AWS SDK Key 객체
     * 
     * <p>각 값의 타입에 따른 적절한 메소드 호출:</p>
     * <ul>
     *   <li>String: partitionValue(String) / sortValue(String)</li>
     *   <li>Number: partitionValue(Number) / sortValue(Number)</li>
     *   <li>SdkBytes: partitionValue(SdkBytes) / sortValue(SdkBytes)</li>
     *   <li>기타: toString()으로 변환 후 String 메소드 사용</li>
     * </ul>
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