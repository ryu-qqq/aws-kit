package com.ryuqq.aws.dynamodb.repository;

import com.ryuqq.aws.dynamodb.model.DynamoDbItem;
import com.ryuqq.aws.dynamodb.properties.DynamoDbProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DynamoDB Repository 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class DynamoDbRepositoryTest {

    @Mock
    private DynamoDbAsyncClient dynamoDbClient;

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;

    @Mock
    private DynamoDbAsyncTable<TestEntity> table;

    private DynamoDbProperties properties;
    private TestRepository repository;

    @BeforeEach
    void setUp() {
        properties = new DynamoDbProperties();
        properties.setTablePrefix("test-");
        properties.setTableSuffix("-dev");
        
        when(enhancedClient.table(anyString(), any(TableSchema.class))).thenReturn(table);
        
        repository = new TestRepository(dynamoDbClient, enhancedClient, properties);
    }

    @Test
    void shouldSaveItem() {
        // Given
        TestEntity entity = new TestEntity("pk1", "sk1", "data");
        when(table.putItem(entity)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<TestEntity> result = repository.save(entity);

        // Then
        assertThat(result).isCompletedWithValue(entity);
        verify(table).putItem(entity);
    }

    @Test
    void shouldSaveItemIfNotExists() {
        // Given
        TestEntity entity = new TestEntity("pk1", "sk1", "data");
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<TestEntity> result = repository.saveIfNotExists(entity, "partitionKey");

        // Then
        assertThat(result).isCompletedWithValue(entity);
        verify(table).putItem(any(PutItemEnhancedRequest.class));
    }

    @Test
    void shouldReturnNullWhenItemAlreadyExists() {
        // Given
        TestEntity entity = new TestEntity("pk1", "sk1", "data");
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new ConditionalCheckFailedException("Item already exists")));

        // When
        CompletableFuture<TestEntity> result = repository.saveIfNotExists(entity, "partitionKey");

        // Then
        assertThat(result).isCompletedWithValue(null);
    }

    @Test
    void shouldFindById() {
        // Given
        Key key = Key.builder()
                .partitionValue("pk1")
                .sortValue("sk1")
                .build();
        TestEntity entity = new TestEntity("pk1", "sk1", "data");
        when(table.getItem(key)).thenReturn(CompletableFuture.completedFuture(entity));

        // When
        CompletableFuture<TestEntity> result = repository.findById(key);

        // Then
        assertThat(result).isCompletedWithValue(entity);
        verify(table).getItem(key);
    }

    @Test
    void shouldUpdateItem() {
        // Given
        TestEntity entity = new TestEntity("pk1", "sk1", "updated data");
        when(table.updateItem(entity)).thenReturn(CompletableFuture.completedFuture(entity));

        // When
        CompletableFuture<TestEntity> result = repository.update(entity);

        // Then
        assertThat(result).isCompletedWithValue(entity);
        verify(table).updateItem(entity);
    }

    @Test
    void shouldDeleteItem() {
        // Given
        Key key = Key.builder()
                .partitionValue("pk1")
                .sortValue("sk1")
                .build();
        TestEntity entity = new TestEntity("pk1", "sk1", "data");
        when(table.deleteItem(key)).thenReturn(CompletableFuture.completedFuture(entity));

        // When
        CompletableFuture<TestEntity> result = repository.delete(key);

        // Then
        assertThat(result).isCompletedWithValue(entity);
        verify(table).deleteItem(key);
    }

    @Test
    void shouldBatchReadItems() {
        // Given
        List<Key> keys = List.of(
                Key.builder().partitionValue("pk1").sortValue("sk1").build(),
                Key.builder().partitionValue("pk2").sortValue("sk2").build()
        );
        List<TestEntity> entities = List.of(
                new TestEntity("pk1", "sk1", "data1"),
                new TestEntity("pk2", "sk2", "data2")
        );
        
        // Enhanced client mock setup
        DynamoDbEnhancedAsyncClient mockEnhancedClient = mock(DynamoDbEnhancedAsyncClient.class);
        BatchGetResultPage mockResultPage = mock(BatchGetResultPage.class);
        when(mockResultPage.resultsForTable(any())).thenReturn(entities);
        when(mockEnhancedClient.batchGetItem(any())).thenReturn(
                CompletableFuture.completedFuture(mockResultPage));

        TestRepository repo = new TestRepository(dynamoDbClient, mockEnhancedClient, properties);

        // When
        CompletableFuture<List<TestEntity>> result = repo.batchRead(keys);

        // Then
        assertThat(result).isCompletedWithValueMatching(list -> list.size() == 2);
    }

    @Test
    void shouldCheckTableExists() {
        // Given
        DescribeTableResponse response = DescribeTableResponse.builder()
                .table(TableDescription.builder()
                        .tableName("test-entities-dev")
                        .itemCount(100L)
                        .build())
                .build();
        when(dynamoDbClient.describeTable(any(DescribeTableRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Boolean> result = repository.tableExists();

        // Then
        assertThat(result).isCompletedWithValue(true);
    }

    @Test
    void shouldGetItemCount() {
        // Given
        DescribeTableResponse response = DescribeTableResponse.builder()
                .table(TableDescription.builder()
                        .tableName("test-entities-dev")
                        .itemCount(42L)
                        .build())
                .build();
        when(dynamoDbClient.describeTable(any(DescribeTableRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Long> result = repository.count();

        // Then
        assertThat(result).isCompletedWithValue(42L);
    }

    // Test entity class
    static class TestEntity {
        private String partitionKey;
        private String sortKey;
        private String data;

        public TestEntity() {}

        public TestEntity(String partitionKey, String sortKey, String data) {
            this.partitionKey = partitionKey;
            this.sortKey = sortKey;
            this.data = data;
        }

        // Getters and setters
        public String getPartitionKey() { return partitionKey; }
        public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }
        public String getSortKey() { return sortKey; }
        public void setSortKey(String sortKey) { this.sortKey = sortKey; }
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
    }

    // Test repository implementation
    static class TestRepository extends DynamoDbRepository<TestEntity> {
        public TestRepository(DynamoDbAsyncClient dynamoDbClient,
                            DynamoDbEnhancedAsyncClient enhancedClient,
                            DynamoDbProperties properties) {
            super(dynamoDbClient, enhancedClient, properties, TestEntity.class, "entities");
        }
    }
}