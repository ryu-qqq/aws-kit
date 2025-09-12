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
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Transaction 기능이 활성화된 DynamoDbService 테스트
 * raw DynamoDB client가 제공된 경우의 트랜잭션 기능을 테스트합니다.
 */
@ExtendWith(MockitoExtension.class)
class TransactionEnabledServiceTest {

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;

    @Mock
    private DynamoDbAsyncClient rawClient;

    private MockedStatic<DynamoTypeAdapter> mockedTypeAdapter;

    private DefaultDynamoDbService<TestEntity> dynamoDbService;

    @BeforeEach
    void setUp() {
        // raw client가 제공되는 생성자 사용
        dynamoDbService = new DefaultDynamoDbService<>(enhancedClient, rawClient);
        
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
    @DisplayName("transactWrite - 트랜잭션 성공적으로 실행")
    void transactWrite_ShouldExecuteTransactionSuccessfully() {
        // Given
        TestEntity entity = new TestEntity("pk1", "data1");
        DynamoTransaction transaction = DynamoTransaction.builder()
                .put(entity, "test-table")
                .build();

        // AWS SDK TransactWriteItem 목록을 모킹
        List<TransactWriteItem> transactWriteItems = List.of(
            TransactWriteItem.builder()
                .put(Put.builder()
                    .tableName("test-table")
                    .item(java.util.Map.of(
                        "pk", AttributeValue.builder().s("pk1").build(),
                        "data", AttributeValue.builder().s("data1").build()
                    ))
                    .build())
                .build()
        );

        // DynamoTypeAdapter.toAwsTransactWriteItems() 모킹
        mockedTypeAdapter.when(() -> DynamoTypeAdapter.toAwsTransactWriteItems(transaction))
                .thenReturn(transactWriteItems);

        // rawClient.transactWriteItems() 모킹
        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(rawClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        
        // rawClient.transactWriteItems()가 호출되었는지 확인
        verify(rawClient).transactWriteItems((TransactWriteItemsRequest) argThat(request -> {
            TransactWriteItemsRequest req = (TransactWriteItemsRequest) request;
            assertThat(req.transactItems()).hasSize(1);
            assertThat(req.transactItems().get(0).put()).isNotNull();
            assertThat(req.transactItems().get(0).put().tableName()).isEqualTo("test-table");
            return true;
        }));
    }

    @Test
    @DisplayName("transactWrite - null 트랜잭션 처리")
    void transactWrite_ShouldHandleNullTransaction() {
        // When & Then
        assertThatThrownBy(() -> dynamoDbService.transactWrite(null).join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DynamoTransaction cannot be null");

        // rawClient가 호출되지 않았는지 확인
        verifyNoInteractions(rawClient);
    }

    @Test
    @DisplayName("transactWrite - 빈 트랜잭션 처리")
    void transactWrite_ShouldHandleEmptyTransaction() {
        // Given - 빈 트랜잭션은 실제로 생성할 수 없으므로 모킹
        DynamoTransaction emptyTransaction = mock(DynamoTransaction.class);
        when(emptyTransaction.isEmpty()).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> dynamoDbService.transactWrite(emptyTransaction).join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DynamoTransaction cannot be empty");

        // rawClient가 호출되지 않았는지 확인
        verifyNoInteractions(rawClient);
    }

    @Test
    @DisplayName("transactWrite - DynamoDB 예외 처리")
    void transactWrite_ShouldHandleDynamoDbException() {
        // Given
        TestEntity entity = new TestEntity("pk1", "data1");
        DynamoTransaction transaction = DynamoTransaction.builder()
                .put(entity, "test-table")
                .build();

        // AWS SDK TransactWriteItem 목록을 모킹
        List<TransactWriteItem> transactWriteItems = List.of(
            TransactWriteItem.builder()
                .put(Put.builder()
                    .tableName("test-table")
                    .item(java.util.Map.of("pk", AttributeValue.builder().s("pk1").build()))
                    .build())
                .build()
        );

        // DynamoTypeAdapter.toAwsTransactWriteItems() 모킹
        mockedTypeAdapter.when(() -> DynamoTypeAdapter.toAwsTransactWriteItems(transaction))
                .thenReturn(transactWriteItems);

        // rawClient.transactWriteItems()에서 예외 발생
        when(rawClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                    TransactionCanceledException.builder()
                        .message("Transaction cancelled")
                        .build()
                ));

        // When & Then
        assertThatThrownBy(() -> dynamoDbService.transactWrite(transaction).join())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Transaction execution failed");

        // rawClient.transactWriteItems()가 호출되었는지 확인
        verify(rawClient).transactWriteItems(any(TransactWriteItemsRequest.class));
    }

    @Test
    @DisplayName("transactWrite - 복합 트랜잭션 실행")
    void transactWrite_ShouldExecuteComplexTransaction() {
        // Given
        TestEntity putEntity = new TestEntity("pk1", "put-data");
        DynamoKey updateKey = DynamoKey.partitionKey("pk", "pk2");
        DynamoKey deleteKey = DynamoKey.partitionKey("pk", "pk3");
        
        DynamoTransaction transaction = DynamoTransaction.builder()
                .put(putEntity, "test-table")
                .update(updateKey, "test-table", "SET #data = :val", "updated-data")
                .delete(deleteKey, "test-table")
                .build();

        // AWS SDK TransactWriteItem 목록을 모킹
        List<TransactWriteItem> transactWriteItems = List.of(
            TransactWriteItem.builder()
                .put(Put.builder().tableName("test-table").build())
                .build(),
            TransactWriteItem.builder()
                .update(Update.builder().tableName("test-table").build())
                .build(),
            TransactWriteItem.builder()
                .delete(Delete.builder().tableName("test-table").build())
                .build()
        );

        // DynamoTypeAdapter.toAwsTransactWriteItems() 모킹
        mockedTypeAdapter.when(() -> DynamoTypeAdapter.toAwsTransactWriteItems(transaction))
                .thenReturn(transactWriteItems);

        // rawClient.transactWriteItems() 모킹
        TransactWriteItemsResponse response = TransactWriteItemsResponse.builder().build();
        when(rawClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        
        // rawClient.transactWriteItems()가 호출되었는지 확인
        verify(rawClient).transactWriteItems((TransactWriteItemsRequest) argThat(request -> {
            TransactWriteItemsRequest req = (TransactWriteItemsRequest) request;
            assertThat(req.transactItems()).hasSize(3);
            // PUT, UPDATE, DELETE 작업이 모두 포함되었는지 확인
            assertThat(req.transactItems().get(0).put()).isNotNull();
            assertThat(req.transactItems().get(1).update()).isNotNull();
            assertThat(req.transactItems().get(2).delete()).isNotNull();
            return true;
        }));
    }

    @Test
    @DisplayName("transactWrite - 변환 오류 처리")
    void transactWrite_ShouldHandleConversionError() {
        // Given
        TestEntity entity = new TestEntity("pk1", "data1");
        DynamoTransaction transaction = DynamoTransaction.builder()
                .put(entity, "test-table")
                .build();

        // DynamoTypeAdapter.toAwsTransactWriteItems()에서 예외 발생
        mockedTypeAdapter.when(() -> DynamoTypeAdapter.toAwsTransactWriteItems(transaction))
                .thenThrow(new RuntimeException("Conversion failed"));

        // When & Then
        assertThatThrownBy(() -> dynamoDbService.transactWrite(transaction).join())
                .hasCauseInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to convert transaction to AWS SDK format");

        // rawClient가 호출되지 않았는지 확인
        verifyNoInteractions(rawClient);
    }

    // 테스트용 단순한 엔티티 클래스
    @DynamoDbBean
    public static class TestEntity {
        private String pk;
        private String data;

        public TestEntity() {}

        public TestEntity(String pk, String data) {
            this.pk = pk;
            this.data = data;
        }

        @DynamoDbPartitionKey
        public String getPk() { return pk; }
        public void setPk(String pk) { this.pk = pk; }
        
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestEntity that = (TestEntity) obj;
            return java.util.Objects.equals(pk, that.pk) &&
                   java.util.Objects.equals(data, that.data);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(pk, data);
        }

        @Override
        public String toString() {
            return "TestEntity{pk='" + pk + "', data='" + data + "'}";
        }
    }
}