package com.ryuqq.aws.dynamodb.service;

import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * DynamoDB 서비스 인터페이스
 */
public interface DynamoDbService<T> {

    /**
     * 아이템 저장 (생성 또는 업데이트)
     */
    CompletableFuture<T> save(T item);

    /**
     * 조건부 저장
     */
    CompletableFuture<T> saveIfNotExists(T item);

    /**
     * 아이템 조회
     */
    CompletableFuture<T> findById(String partitionKey, String sortKey);

    /**
     * 파티션 키로 모든 아이템 조회
     */
    CompletableFuture<List<T>> findByPartitionKey(String partitionKey);

    /**
     * 파티션 키와 정렬 키 범위로 조회
     */
    CompletableFuture<List<T>> findByPartitionKeyAndSortKeyBetween(
            String partitionKey, String sortKeyStart, String sortKeyEnd);

    /**
     * 아이템 업데이트
     */
    CompletableFuture<T> update(T item);

    /**
     * 부분 업데이트
     */
    CompletableFuture<T> updatePartial(String partitionKey, String sortKey, 
                                      Map<String, Object> updates);

    /**
     * 아이템 삭제
     */
    CompletableFuture<T> delete(String partitionKey, String sortKey);

    /**
     * 조건부 삭제
     */
    CompletableFuture<Boolean> deleteIf(String partitionKey, String sortKey, 
                                       String conditionExpression);

    /**
     * 배치 저장
     */
    CompletableFuture<Void> batchSave(List<T> items);

    /**
     * 배치 조회
     */
    CompletableFuture<List<T>> batchGet(List<Map<String, String>> keys);

    /**
     * 배치 삭제
     */
    CompletableFuture<Void> batchDelete(List<Map<String, String>> keys);

    /**
     * 쿼리 실행 (페이징)
     */
    CompletableFuture<Page<T>> query(String partitionKey, int limit);

    /**
     * 스캔 실행 (페이징)
     */
    CompletableFuture<Page<T>> scan(int limit);

    /**
     * 병렬 스캔
     */
    CompletableFuture<List<T>> parallelScan(int totalSegments);

    /**
     * 트랜잭션 쓰기
     */
    CompletableFuture<Void> transactWrite(Consumer<TransactionBuilder<T>> builder);

    /**
     * 인덱스 쿼리
     */
    CompletableFuture<List<T>> queryIndex(String indexName, String partitionKey);

    /**
     * 아이템 수 카운트
     */
    CompletableFuture<Long> count();

    /**
     * 조건에 맞는 아이템 수 카운트
     */
    CompletableFuture<Long> countByCondition(String filterExpression);

    /**
     * 테이블 존재 여부 확인
     */
    CompletableFuture<Boolean> tableExists();

    /**
     * 테이블 생성
     */
    CompletableFuture<Void> createTableIfNotExists();

    /**
     * 트랜잭션 빌더 인터페이스
     */
    interface TransactionBuilder<T> {
        TransactionBuilder<T> put(T item);
        TransactionBuilder<T> update(T item);
        TransactionBuilder<T> delete(String partitionKey, String sortKey);
        TransactionBuilder<T> conditionCheck(String partitionKey, String sortKey, String condition);
    }
}