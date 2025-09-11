package com.ryuqq.aws.dynamodb.service;

import com.ryuqq.aws.dynamodb.adapter.DynamoTypeAdapter;
import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.types.DynamoTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DefaultDynamoDbService 단위 테스트
 * 핵심 DynamoDB 서비스 기능 테스트
 */
@ExtendWith(MockitoExtension.class)
class DefaultDynamoDbServiceTest {

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;

    @Mock
    private DynamoDbAsyncTable<TestEntity> mockTable;

    private MockedStatic<DynamoTypeAdapter> mockedTypeAdapter;

    private DefaultDynamoDbService<TestEntity> dynamoDbService;

    private final String tableName = "test-table";

    @BeforeEach
    void setUp() {
        dynamoDbService = new DefaultDynamoDbService<>(enhancedClient);
        
        // DynamoTypeAdapter static methods 모킹
        mockedTypeAdapter = mockStatic(DynamoTypeAdapter.class);
    }

    @AfterEach
    void tearDown() {
        if (mockedTypeAdapter != null) {
            mockedTypeAdapter.close();
        }
    }

    @Test
    @DisplayName("save - 항목 저장 성공")
    void save_ShouldSaveItemSuccessfully() {
        // Given
        TestEntity entity = new TestEntity("pk1", "sk1", "data");
        when(enhancedClient.table(eq(tableName), any(TableSchema.class))).thenReturn(mockTable);
        when(mockTable.putItem(entity)).thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = dynamoDbService.save(entity, tableName);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        verify(mockTable).putItem(entity);
        verify(enhancedClient).table(tableName, TableSchema.fromBean(TestEntity.class));
    }

    @Test
    @DisplayName("load - 키로 항목 조회 성공")
    void load_ShouldLoadItemSuccessfully() {
        // Given
        DynamoKey dynamoKey = DynamoKey.builder()
                .partitionValue("PK", "pk1")
                .sortValue("SK", "sk1")
                .build();
        
        Key awsKey = Key.builder()
                .partitionValue("pk1")
                .sortValue("sk1")
                .build();
        
        TestEntity expectedEntity = new TestEntity("pk1", "sk1", "data");
        
        // Mock enhanced client and static method
        when(enhancedClient.table(eq(tableName), any(TableSchema.class))).thenReturn(mockTable);
        mockedTypeAdapter.when(() -> DynamoTypeAdapter.toAwsKey(dynamoKey)).thenReturn(awsKey);
        when(mockTable.getItem(awsKey)).thenReturn(CompletableFuture.completedFuture(expectedEntity));

        // When
        CompletableFuture<TestEntity> result = dynamoDbService.load(TestEntity.class, dynamoKey, tableName);

        // Then
        assertThat(result.join()).isEqualTo(expectedEntity);
        verify(mockTable).getItem(awsKey);
    }

    @Test
    @DisplayName("delete - 키로 항목 삭제 성공")
    void delete_ShouldDeleteItemSuccessfully() {
        // Given
        DynamoKey dynamoKey = DynamoKey.builder()
                .partitionValue("PK", "pk1")
                .sortValue("SK", "sk1")
                .build();
        
        Key awsKey = Key.builder()
                .partitionValue("pk1")
                .sortValue("sk1")
                .build();
        
        TestEntity deletedEntity = new TestEntity("pk1", "sk1", "data");
        
        // Mock enhanced client and static method
        when(enhancedClient.table(eq(tableName), any(TableSchema.class))).thenReturn(mockTable);
        mockedTypeAdapter.when(() -> DynamoTypeAdapter.toAwsKey(dynamoKey)).thenReturn(awsKey);
        when(mockTable.deleteItem(awsKey)).thenReturn(CompletableFuture.completedFuture(deletedEntity));

        // When
        CompletableFuture<Void> result = dynamoDbService.delete(dynamoKey, tableName, TestEntity.class);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        verify(mockTable).deleteItem(awsKey);
    }

    @Test
    @DisplayName("batchSave - 빈 목록 처리")
    void batchSave_ShouldHandleEmptyList() {
        // Given
        List<TestEntity> emptyItems = List.of();

        // When
        CompletableFuture<Void> result = dynamoDbService.batchSave(emptyItems, tableName);

        // Then
        assertThat(result.join()).isNull();
        verifyNoInteractions(enhancedClient);
    }

    @Test
    @DisplayName("batchLoad - 빈 키 목록 처리")
    void batchLoad_ShouldHandleEmptyKeyList() {
        // Given
        List<DynamoKey> emptyKeys = List.of();

        // When
        CompletableFuture<List<TestEntity>> result = dynamoDbService.batchLoad(TestEntity.class, emptyKeys, tableName);

        // Then
        assertThat(result.join()).isEmpty();
        verifyNoInteractions(enhancedClient);
    }

    @Test
    @DisplayName("transactWrite - 트랜잭션 미지원 예외")
    void transactWrite_ShouldThrowUnsupportedOperationException() {
        // Given
        DynamoTransaction transaction = DynamoTransaction.builder()
                .put(new TestEntity("pk1", "sk1", "data"), "test-table")
                .build();

        // When & Then
        assertThatThrownBy(() -> dynamoDbService.transactWrite(transaction).join())
                .hasCauseInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Transaction operations require direct DynamoDB client access");
    }

    @Test
    @DisplayName("constructor - Enhanced client 설정 확인")
    void constructor_ShouldAcceptEnhancedClient() {
        // Given & When
        DefaultDynamoDbService<TestEntity> service = new DefaultDynamoDbService<>(enhancedClient);

        // Then
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("save - null 항목 처리")
    void save_ShouldHandleNullItem() {
        // Given
        TestEntity nullEntity = null;

        // When & Then
        assertThatThrownBy(() -> dynamoDbService.save(nullEntity, tableName))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("load - null 클래스 처리")
    void load_ShouldHandleNullClass() {
        // Given
        DynamoKey dynamoKey = DynamoKey.builder()
                .partitionValue("PK", "pk1")
                .build();

        // When & Then
        assertThatThrownBy(() -> dynamoDbService.load(null, dynamoKey, tableName))
                .isInstanceOf(NullPointerException.class);
    }

    // 테스트용 엔티티 클래스
    @DynamoDbBean
    public static class TestEntity {
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
        @DynamoDbPartitionKey
        public String getPartitionKey() { return partitionKey; }
        public void setPartitionKey(String partitionKey) { this.partitionKey = partitionKey; }
        
        @DynamoDbSortKey
        public String getSortKey() { return sortKey; }
        public void setSortKey(String sortKey) { this.sortKey = sortKey; }
        
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestEntity that = (TestEntity) obj;
            return java.util.Objects.equals(partitionKey, that.partitionKey) &&
                   java.util.Objects.equals(sortKey, that.sortKey) &&
                   java.util.Objects.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(partitionKey, sortKey, data);
        }

        @Override
        public String toString() {
            return "TestEntity{" +
                    "partitionKey='" + partitionKey + '\'' +
                    ", sortKey='" + sortKey + '\'' +
                    ", data='" + data + '\'' +
                    '}';
        }
    }
}