package com.ryuqq.aws.dynamodb.repository;

import com.ryuqq.aws.commons.async.AwsAsyncUtils;
import com.ryuqq.aws.commons.exception.AwsExceptionHandler;
import com.ryuqq.aws.dynamodb.properties.DynamoDbProperties;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * DynamoDB 범용 Repository 구현
 */
@Slf4j
public abstract class DynamoDbRepository<T> {

    protected final DynamoDbAsyncClient dynamoDbClient;
    protected final DynamoDbEnhancedAsyncClient enhancedClient;
    protected final DynamoDbAsyncTable<T> table;
    protected final DynamoDbProperties properties;
    protected final Class<T> entityClass;
    protected final String tableName;

    protected DynamoDbRepository(DynamoDbAsyncClient dynamoDbClient,
                                DynamoDbEnhancedAsyncClient enhancedClient,
                                DynamoDbProperties properties,
                                Class<T> entityClass,
                                String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.enhancedClient = enhancedClient;
        this.properties = properties;
        this.entityClass = entityClass;
        this.tableName = getFullTableName(tableName);
        this.table = enhancedClient.table(this.tableName, TableSchema.fromBean(entityClass));
    }

    /**
     * 아이템 저장
     */
    public CompletableFuture<T> save(T item) {
        return AwsAsyncUtils.withTimeout(
                table.putItem(item)
                        .thenApply(v -> item),
                Duration.ofSeconds(10),
                "save"
        ).exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "save"));
    }

    /**
     * 조건부 저장 (존재하지 않을 때만)
     */
    public CompletableFuture<T> saveIfNotExists(T item, String partitionKeyName) {
        Expression conditionExpression = Expression.builder()
                .expression("attribute_not_exists(" + partitionKeyName + ")")
                .build();

        PutItemEnhancedRequest<T> request = PutItemEnhancedRequest.builder(entityClass)
                .item(item)
                .conditionExpression(conditionExpression)
                .build();

        return table.putItem(request)
                .thenApply(v -> item)
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof ConditionalCheckFailedException) {
                        log.debug("Item already exists, skipping save");
                        return null;
                    }
                    throw AwsExceptionHandler.handleException("dynamodb", "saveIfNotExists", throwable);
                });
    }

    /**
     * 아이템 조회
     */
    public CompletableFuture<T> findById(Key key) {
        return AwsAsyncUtils.withTimeout(
                table.getItem(key),
                Duration.ofSeconds(5),
                "findById"
        ).exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "findById"));
    }

    /**
     * 아이템 업데이트
     */
    public CompletableFuture<T> update(T item) {
        return AwsAsyncUtils.withTimeout(
                table.updateItem(item),
                Duration.ofSeconds(10),
                "update"
        ).exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "update"));
    }

    /**
     * 아이템 삭제
     */
    public CompletableFuture<T> delete(Key key) {
        return AwsAsyncUtils.withTimeout(
                table.deleteItem(key),
                Duration.ofSeconds(5),
                "delete"
        ).exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "delete"));
    }

    /**
     * 배치 쓰기
     */
    public CompletableFuture<Void> batchWrite(List<T> itemsToSave, List<Key> keysToDelete) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // 배치 크기에 맞게 분할
        int batchSize = properties.getBatchConfig().getBatchWriteSize();
        
        // 저장할 아이템 처리
        if (itemsToSave != null && !itemsToSave.isEmpty()) {
            for (int i = 0; i < itemsToSave.size(); i += batchSize) {
                List<T> batch = itemsToSave.subList(i, 
                        Math.min(i + batchSize, itemsToSave.size()));
                futures.add(batchWriteItems(batch, null));
            }
        }
        
        // 삭제할 키 처리
        if (keysToDelete != null && !keysToDelete.isEmpty()) {
            for (int i = 0; i < keysToDelete.size(); i += batchSize) {
                List<Key> batch = keysToDelete.subList(i, 
                        Math.min(i + batchSize, keysToDelete.size()));
                futures.add(batchWriteItems(null, batch));
            }
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "batchWrite"));
    }

    private CompletableFuture<Void> batchWriteItems(List<T> itemsToSave, List<Key> keysToDelete) {
        WriteBatch.Builder<T> batchBuilder = WriteBatch.builder(entityClass)
                .mappedTableResource(table);

        if (itemsToSave != null) {
            itemsToSave.forEach(batchBuilder::addPutItem);
        }
        
        if (keysToDelete != null) {
            keysToDelete.forEach(batchBuilder::addDeleteItem);
        }

        BatchWriteItemEnhancedRequest request = BatchWriteItemEnhancedRequest.builder()
                .writeBatches(batchBuilder.build())
                .build();

        return enhancedClient.batchWriteItem(request)
                .thenAccept(response -> {
                    if (!response.unprocessedPutItemsForTable(table).isEmpty() ||
                        !response.unprocessedDeleteItemsForTable(table).isEmpty()) {
                        log.warn("Some items were not processed in batch write");
                    }
                });
    }

    /**
     * 배치 읽기
     */
    public CompletableFuture<List<T>> batchRead(List<Key> keys) {
        List<CompletableFuture<List<T>>> futures = new ArrayList<>();
        int batchSize = properties.getBatchConfig().getBatchReadSize();
        
        for (int i = 0; i < keys.size(); i += batchSize) {
            List<Key> batch = keys.subList(i, Math.min(i + batchSize, keys.size()));
            futures.add(batchReadItems(batch));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                .exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "batchRead"));
    }

    private CompletableFuture<List<T>> batchReadItems(List<Key> keys) {
        ReadBatch.Builder<T> batchBuilder = ReadBatch.builder(entityClass)
                .mappedTableResource(table);
        
        keys.forEach(batchBuilder::addGetItem);

        BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                .readBatches(batchBuilder.build())
                .build();

        return enhancedClient.batchGetItem(request)
                .thenApply(response -> {
                    List<T> results = new ArrayList<>();
                    response.resultsForTable(table).forEach(results::add);
                    return results;
                });
    }

    /**
     * 쿼리 실행
     */
    public CompletableFuture<PageIterable<T>> query(QueryConditional queryConditional) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(properties.getQueryConfig().getDefaultPageSize())
                .consistentRead(properties.getQueryConfig().isConsistentRead())
                .build();

        return CompletableFuture.completedFuture(table.query(request))
                .exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "query"));
    }

    /**
     * 인덱스 쿼리
     */
    public CompletableFuture<PageIterable<T>> queryIndex(String indexName, 
                                                        QueryConditional queryConditional) {
        DynamoDbAsyncIndex<T> index = table.index(indexName);
        
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(properties.getQueryConfig().getDefaultPageSize())
                .build();

        return CompletableFuture.completedFuture(index.query(request))
                .exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "queryIndex"));
    }

    /**
     * 스캔 실행
     */
    public CompletableFuture<PageIterable<T>> scan() {
        ScanEnhancedRequest request = ScanEnhancedRequest.builder()
                .limit(properties.getQueryConfig().getDefaultPageSize())
                .consistentRead(properties.getQueryConfig().isConsistentRead())
                .build();

        return CompletableFuture.completedFuture(table.scan(request))
                .exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "scan"));
    }

    /**
     * 병렬 스캔
     */
    public CompletableFuture<List<T>> parallelScan(int totalSegments) {
        List<CompletableFuture<List<T>>> segmentFutures = new ArrayList<>();
        
        for (int segment = 0; segment < totalSegments; segment++) {
            ScanEnhancedRequest request = ScanEnhancedRequest.builder()
                    .segment(segment)
                    .totalSegments(totalSegments)
                    .limit(properties.getQueryConfig().getDefaultPageSize())
                    .build();

            CompletableFuture<List<T>> segmentFuture = 
                    CompletableFuture.supplyAsync(() -> {
                        List<T> items = new ArrayList<>();
                        table.scan(request).items().subscribe(items::add).join();
                        return items;
                    });
            
            segmentFutures.add(segmentFuture);
        }

        return CompletableFuture.allOf(segmentFutures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> segmentFutures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                .exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "parallelScan"));
    }

    /**
     * 트랜잭션 쓰기
     */
    public CompletableFuture<Void> transactWrite(List<TransactWriteItem> transactItems) {
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(transactItems)
                .build();

        return AwsAsyncUtils.withRetry(
                () -> dynamoDbClient.transactWriteItems(request)
                        .thenApply(response -> null),
                3,
                Duration.ofSeconds(1),
                "transactWrite"
        ).exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "transactWrite"));
    }

    /**
     * 테이블 존재 여부 확인
     */
    public CompletableFuture<Boolean> tableExists() {
        DescribeTableRequest request = DescribeTableRequest.builder()
                .tableName(tableName)
                .build();

        return dynamoDbClient.describeTable(request)
                .thenApply(response -> true)
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof ResourceNotFoundException) {
                        return false;
                    }
                    throw AwsExceptionHandler.handleException("dynamodb", "tableExists", throwable);
                });
    }

    /**
     * 테이블 생성
     */
    public CompletableFuture<Void> createTable() {
        return table.createTable()
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof ResourceInUseException) {
                        log.info("Table {} already exists", tableName);
                        return null;
                    }
                    throw AwsExceptionHandler.handleException("dynamodb", "createTable", throwable);
                });
    }

    /**
     * 아이템 수 카운트
     */
    public CompletableFuture<Long> count() {
        DescribeTableRequest request = DescribeTableRequest.builder()
                .tableName(tableName)
                .build();

        return dynamoDbClient.describeTable(request)
                .thenApply(response -> response.table().itemCount())
                .exceptionally(AwsExceptionHandler.handleAsync("dynamodb", "count"));
    }

    /**
     * 전체 테이블 이름 생성
     */
    private String getFullTableName(String baseName) {
        String prefix = properties.getTablePrefix();
        String suffix = properties.getTableSuffix();
        
        StringBuilder fullName = new StringBuilder();
        
        if (prefix != null && !prefix.isEmpty()) {
            fullName.append(prefix);
        }
        
        fullName.append(baseName);
        
        if (suffix != null && !suffix.isEmpty()) {
            fullName.append(suffix);
        }
        
        return fullName.toString();
    }
}