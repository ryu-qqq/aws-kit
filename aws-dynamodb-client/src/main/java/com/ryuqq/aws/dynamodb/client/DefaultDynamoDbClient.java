package com.ryuqq.aws.dynamodb.client;

import com.ryuqq.aws.commons.exception.AwsServiceException;
import com.ryuqq.aws.commons.metrics.AwsMetricsProvider;
import com.ryuqq.aws.dynamodb.model.QueryRequest;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * DynamoDB 클라이언트 기본 구현체 (간소화 버전)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDynamoDbClient implements DynamoDbClient {

    private static final String SERVICE_NAME = "dynamodb";
    private final DynamoDbAsyncClient dynamoDbAsyncClient;
    private final DynamoDbEnhancedAsyncClient enhancedClient;
    private final AwsMetricsProvider metricsProvider;

    @Override
    public <T> CompletableFuture<Void> putItem(String tableName, T item, Class<T> itemType) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "putItem");
        
        try {
            DynamoDbAsyncTable<T> table = enhancedClient.table(tableName, TableSchema.fromBean(itemType));
            
            return table.putItem(item)
                    .thenApply(response -> {
                        metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "putItem", true);
                        metricsProvider.incrementSuccessCounter(SERVICE_NAME, "putItem");
                        log.debug("Item saved successfully to table: {}", tableName);
                        return (Void) null;
                    })
                    .exceptionally(throwable -> {
                        metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "putItem", false);
                        String errorCode = extractErrorCode(throwable);
                        metricsProvider.incrementErrorCounter(SERVICE_NAME, "putItem", errorCode);
                        log.error("Failed to put item to table: {}", tableName, throwable);
                        throw new AwsServiceException(SERVICE_NAME, "Failed to put item", throwable);
                    });
        } catch (Exception e) {
            metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "putItem", false);
            throw new AwsServiceException(SERVICE_NAME, "Failed to put item", e);
        }
    }

    @Override
    public <T> CompletableFuture<T> getItem(String tableName, String partitionKey, String sortKey, Class<T> itemType) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "getItem");
        
        try {
            DynamoDbAsyncTable<T> table = enhancedClient.table(tableName, TableSchema.fromBean(itemType));
            
            Key key = sortKey != null ? 
                Key.builder().partitionValue(partitionKey).sortValue(sortKey).build() :
                Key.builder().partitionValue(partitionKey).build();
            
            return table.getItem(key)
                    .thenApply(item -> {
                        metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getItem", true);
                        metricsProvider.incrementSuccessCounter(SERVICE_NAME, "getItem");
                        log.debug("Item retrieved from table: {}, partitionKey: {}", tableName, partitionKey);
                        return item;
                    })
                    .exceptionally(throwable -> {
                        metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getItem", false);
                        String errorCode = extractErrorCode(throwable);
                        metricsProvider.incrementErrorCounter(SERVICE_NAME, "getItem", errorCode);
                        log.error("Failed to get item from table: {}", tableName, throwable);
                        throw new AwsServiceException(SERVICE_NAME, "Failed to get item", throwable);
                    });
        } catch (Exception e) {
            metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getItem", false);
            throw new AwsServiceException(SERVICE_NAME, "Failed to get item", e);
        }
    }

    @Override
    public CompletableFuture<Void> putItem(String tableName, Map<String, AttributeValue> item) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "putItemRaw");
        
        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();
        
        return dynamoDbAsyncClient.putItem(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "putItemRaw", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "putItemRaw");
                    log.debug("Raw item saved successfully to table: {}", tableName);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "putItemRaw", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "putItemRaw", errorCode);
                    log.error("Failed to put raw item to table: {}", tableName, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to put raw item", throwable);
                });
    }

    @Override
    public CompletableFuture<Map<String, AttributeValue>> getItem(String tableName, Map<String, AttributeValue> key) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "getItemRaw");
        
        GetItemRequest request = GetItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .build();
        
        return dynamoDbAsyncClient.getItem(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getItemRaw", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "getItemRaw");
                    log.debug("Raw item retrieved from table: {}", tableName);
                    return response.item();
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getItemRaw", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "getItemRaw", errorCode);
                    log.error("Failed to get raw item from table: {}", tableName, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to get raw item", throwable);
                });
    }

    @Override
    public CompletableFuture<Boolean> tableExists(String tableName) {
        return describeTable(tableName)
                .thenApply(tableDescription -> true)
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof ResourceNotFoundException) {
                        return false;
                    }
                    throw new AwsServiceException(SERVICE_NAME, "Failed to check table existence", throwable);
                });
    }

    @Override
    public CompletableFuture<TableDescription> describeTable(String tableName) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "describeTable");
        
        DescribeTableRequest request = DescribeTableRequest.builder()
                .tableName(tableName)
                .build();
        
        return dynamoDbAsyncClient.describeTable(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "describeTable", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "describeTable");
                    return response.table();
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "describeTable", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "describeTable", errorCode);
                    log.error("Failed to describe table: {}", tableName, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to describe table", throwable);
                });
    }

    // 나머지 메서드들은 기본 구현으로 제공
    @Override
    public <T> CompletableFuture<T> deleteItem(String tableName, String partitionKey, String sortKey, Class<T> itemType) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> CompletableFuture<T> updateItem(String tableName, T item, Class<T> itemType) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> CompletableFuture<Void> putItemWithCondition(String tableName, T item, Class<T> itemType, String conditionExpression, Map<String, AttributeValue> expressionAttributeValues) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> CompletableFuture<List<T>> query(QueryRequest queryRequest, Class<T> itemType) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<Map<String, AttributeValue>>> queryRaw(QueryRequest queryRequest) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public <T> CompletableFuture<List<T>> scan(String tableName, Class<T> itemType) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public <T> CompletableFuture<List<T>> scanWithFilter(String tableName, String filterExpression, Map<String, AttributeValue> expressionAttributeValues, Class<T> itemType) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public <T> CompletableFuture<List<T>> parallelScan(String tableName, int totalSegments, Class<T> itemType) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public <T> CompletableFuture<Void> batchPutItems(String tableName, List<T> items, Class<T> itemType) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public <T> CompletableFuture<List<T>> batchGetItems(String tableName, List<Map<String, AttributeValue>> keys, Class<T> itemType) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<Void> batchDeleteItems(String tableName, List<Map<String, AttributeValue>> keys) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> transactWrite(List<TransactWriteItem> items) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<List<Map<String, AttributeValue>>> transactRead(List<TransactGetItem> items) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<Void> createTable(String tableName, List<KeySchemaElement> keySchema, List<AttributeDefinition> attributeDefinitions) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> deleteTable(String tableName) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Long> getItemCount(String tableName) {
        return CompletableFuture.completedFuture(0L);
    }

    private String extractErrorCode(Throwable throwable) {
        if (throwable.getCause() instanceof DynamoDbException dynamoException) {
            return dynamoException.awsErrorDetails().errorCode();
        }
        return "UNKNOWN_ERROR";
    }
}