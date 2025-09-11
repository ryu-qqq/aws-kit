package com.ryuqq.aws.dynamodb.service;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.types.DynamoQuery;
import com.ryuqq.aws.dynamodb.types.DynamoTransaction;
import com.ryuqq.aws.dynamodb.adapter.DynamoTypeAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simple DynamoDB service implementation using AWS Enhanced DynamoDB client directly
 */
public class DefaultDynamoDbService<T> implements DynamoDbService<T> {

    private final DynamoDbEnhancedAsyncClient enhancedClient;

    public DefaultDynamoDbService(DynamoDbEnhancedAsyncClient enhancedClient) {
        this.enhancedClient = enhancedClient;
    }

    @Override
    public CompletableFuture<Void> save(T item, String tableName) {
        @SuppressWarnings("unchecked")
        Class<T> itemClass = (Class<T>) item.getClass();
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        return table.putItem(item);
    }

    @Override
    public CompletableFuture<T> load(Class<T> itemClass, DynamoKey key, String tableName) {
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        return table.getItem(DynamoTypeAdapter.toAwsKey(key));
    }

    @Override
    public CompletableFuture<Void> delete(DynamoKey key, String tableName, Class<T> itemClass) {
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        return table.deleteItem(DynamoTypeAdapter.toAwsKey(key)).thenApply(item -> null);
    }

    @Override
    public CompletableFuture<List<T>> query(Class<T> itemClass, DynamoQuery dynamoQuery, String tableName) {
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(DynamoTypeAdapter.toAwsQueryConditional(dynamoQuery))
                .build();
        
        // Collect all pages using CompletableFuture
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        List<T> allItems = new ArrayList<>();
        
        table.query(request)
                .subscribe(page -> allItems.addAll(page.items()))
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    } else {
                        result.complete(allItems);
                    }
                });
        
        return result;
    }

    @Override
    public CompletableFuture<List<T>> scan(Class<T> itemClass, String tableName) {
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        
        // Collect all pages using CompletableFuture
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        List<T> allItems = new ArrayList<>();
        
        table.scan()
                .subscribe(page -> allItems.addAll(page.items()))
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    } else {
                        result.complete(allItems);
                    }
                });
        
        return result;
    }

    @Override
    public CompletableFuture<Void> batchSave(List<T> items, String tableName) {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("unchecked")
        Class<T> itemClass = (Class<T>) items.get(0).getClass();
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);

        WriteBatch.Builder<T> batchBuilder = WriteBatch.builder(itemClass)
                .mappedTableResource(table);
        
        items.forEach(batchBuilder::addPutItem);

        BatchWriteItemEnhancedRequest request = BatchWriteItemEnhancedRequest.builder()
                .writeBatches(batchBuilder.build())
                .build();

        return enhancedClient.batchWriteItem(request).thenApply(response -> null);
    }

    @Override
    public CompletableFuture<List<T>> batchLoad(Class<T> itemClass, List<DynamoKey> keys, String tableName) {
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        ReadBatch.Builder<T> batchBuilder = ReadBatch.builder(itemClass)
                .mappedTableResource(table);

        keys.stream()
                .map(DynamoTypeAdapter::toAwsKey)
                .forEach(batchBuilder::addGetItem);

        BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                .readBatches(batchBuilder.build())
                .build();

        // Collect results using CompletableFuture
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        List<T> allItems = new ArrayList<>();
        
        enhancedClient.batchGetItem(request)
                .resultsForTable(table)
                .subscribe(item -> allItems.add(item))
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    } else {
                        result.complete(allItems);
                    }
                });
        
        return result;
    }

    @Override
    public CompletableFuture<Void> transactWrite(DynamoTransaction transaction) {
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(DynamoTypeAdapter.toAwsTransactWriteItems(transaction))
                .build();

        // Need to get the underlying DynamoDB client
        return CompletableFuture.supplyAsync(() -> {
            // For now, throw an exception as this needs the raw client
            throw new UnsupportedOperationException("Transaction operations require direct DynamoDB client access");
        });
    }

    private DynamoDbAsyncTable<T> getTable(Class<T> itemClass, String tableName) {
        return enhancedClient.table(tableName, TableSchema.fromBean(itemClass));
    }
}