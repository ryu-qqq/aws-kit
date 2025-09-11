package com.ryuqq.aws.dynamodb.service;

import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified DynamoDB service interface with essential operations only
 */
public interface DynamoDbService<T> {

    /**
     * Save an item to DynamoDB table
     * 
     * @param item the item to save
     * @param tableName the table name
     * @return CompletableFuture that completes when save is done
     */
    CompletableFuture<Void> save(T item, String tableName);

    /**
     * Load an item by key
     * 
     * @param itemClass the item class
     * @param key the key to load
     * @param tableName the table name
     * @return CompletableFuture with the loaded item
     */
    CompletableFuture<T> load(Class<T> itemClass, Key key, String tableName);

    /**
     * Delete an item by key
     * 
     * @param key the key to delete
     * @param tableName the table name
     * @param itemClass the item class
     * @return CompletableFuture that completes when delete is done
     */
    CompletableFuture<Void> delete(Key key, String tableName, Class<T> itemClass);

    /**
     * Query items with query conditional
     * 
     * @param itemClass the item class
     * @param queryConditional the query condition
     * @param tableName the table name
     * @return CompletableFuture with query results
     */
    CompletableFuture<List<T>> query(Class<T> itemClass, QueryConditional queryConditional, String tableName);

    /**
     * Scan all items in table
     * 
     * @param itemClass the item class
     * @param tableName the table name
     * @return CompletableFuture with scan results
     */
    CompletableFuture<List<T>> scan(Class<T> itemClass, String tableName);

    /**
     * Batch save multiple items
     * 
     * @param items the items to save
     * @param tableName the table name
     * @return CompletableFuture that completes when batch save is done
     */
    CompletableFuture<Void> batchSave(List<T> items, String tableName);

    /**
     * Batch load multiple items by keys
     * 
     * @param itemClass the item class
     * @param keys the keys to load
     * @param tableName the table name
     * @return CompletableFuture with loaded items
     */
    CompletableFuture<List<T>> batchLoad(Class<T> itemClass, List<Key> keys, String tableName);

    /**
     * Execute transaction write
     * 
     * @param items the transaction write items
     * @return CompletableFuture that completes when transaction is done
     */
    CompletableFuture<Void> transactWrite(List<TransactWriteItem> items);
}