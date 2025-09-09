package com.ryuqq.aws.dynamodb.client;

import com.ryuqq.aws.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * DynamoDB 클라이언트 인터페이스
 */
public interface DynamoDbClient {

    /**
     * Enhanced Client를 사용한 아이템 저장
     */
    <T> CompletableFuture<Void> putItem(String tableName, T item, Class<T> itemType);

    /**
     * Enhanced Client를 사용한 아이템 조회
     */
    <T> CompletableFuture<T> getItem(String tableName, String partitionKey, String sortKey, Class<T> itemType);

    /**
     * Enhanced Client를 사용한 아이템 삭제
     */
    <T> CompletableFuture<T> deleteItem(String tableName, String partitionKey, String sortKey, Class<T> itemType);

    /**
     * Enhanced Client를 사용한 아이템 업데이트
     */
    <T> CompletableFuture<T> updateItem(String tableName, T item, Class<T> itemType);

    /**
     * 조건부 아이템 저장
     */
    <T> CompletableFuture<Void> putItemWithCondition(String tableName, T item, Class<T> itemType, 
                                                    String conditionExpression, 
                                                    Map<String, AttributeValue> expressionAttributeValues);

    /**
     * Raw DynamoDB를 사용한 아이템 저장
     */
    CompletableFuture<Void> putItem(String tableName, Map<String, AttributeValue> item);

    /**
     * Raw DynamoDB를 사용한 아이템 조회
     */
    CompletableFuture<Map<String, AttributeValue>> getItem(String tableName, 
                                                          Map<String, AttributeValue> key);

    /**
     * 쿼리 실행
     */
    <T> CompletableFuture<List<T>> query(QueryRequest queryRequest, Class<T> itemType);

    /**
     * Raw 쿼리 실행
     */
    CompletableFuture<List<Map<String, AttributeValue>>> queryRaw(QueryRequest queryRequest);

    /**
     * 테이블 스캔
     */
    <T> CompletableFuture<List<T>> scan(String tableName, Class<T> itemType);

    /**
     * 필터 조건을 사용한 테이블 스캔
     */
    <T> CompletableFuture<List<T>> scanWithFilter(String tableName, String filterExpression,
                                                 Map<String, AttributeValue> expressionAttributeValues,
                                                 Class<T> itemType);

    /**
     * 병렬 스캔
     */
    <T> CompletableFuture<List<T>> parallelScan(String tableName, int totalSegments, Class<T> itemType);

    /**
     * 배치 아이템 쓰기
     */
    <T> CompletableFuture<Void> batchPutItems(String tableName, List<T> items, Class<T> itemType);

    /**
     * 배치 아이템 읽기
     */
    <T> CompletableFuture<List<T>> batchGetItems(String tableName, 
                                               List<Map<String, AttributeValue>> keys, 
                                               Class<T> itemType);

    /**
     * 배치 아이템 삭제
     */
    CompletableFuture<Void> batchDeleteItems(String tableName, List<Map<String, AttributeValue>> keys);

    /**
     * 트랜잭션 쓰기
     */
    CompletableFuture<Void> transactWrite(List<software.amazon.awssdk.services.dynamodb.model.TransactWriteItem> items);

    /**
     * 트랜잭션 읽기
     */
    CompletableFuture<List<Map<String, AttributeValue>>> transactRead(
            List<software.amazon.awssdk.services.dynamodb.model.TransactGetItem> items);

    /**
     * 테이블 생성
     */
    CompletableFuture<Void> createTable(String tableName, 
                                       List<software.amazon.awssdk.services.dynamodb.model.KeySchemaElement> keySchema,
                                       List<software.amazon.awssdk.services.dynamodb.model.AttributeDefinition> attributeDefinitions);

    /**
     * 테이블 삭제
     */
    CompletableFuture<Void> deleteTable(String tableName);

    /**
     * 테이블 존재 여부 확인
     */
    CompletableFuture<Boolean> tableExists(String tableName);

    /**
     * 테이블 정보 조회
     */
    CompletableFuture<software.amazon.awssdk.services.dynamodb.model.TableDescription> describeTable(String tableName);

    /**
     * 테이블 아이템 수 조회
     */
    CompletableFuture<Long> getItemCount(String tableName);
}