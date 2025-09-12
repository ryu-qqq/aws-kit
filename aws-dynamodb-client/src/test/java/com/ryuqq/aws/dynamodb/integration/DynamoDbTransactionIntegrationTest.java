package com.ryuqq.aws.dynamodb.integration;

import com.ryuqq.aws.dynamodb.service.DefaultDynamoDbService;
import com.ryuqq.aws.dynamodb.service.DynamoDbService;
import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.types.DynamoTransaction;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DynamoDB Transaction 기능에 대한 통합 테스트
 * LocalStack을 사용한 실제 DynamoDB 환경 테스트
 */
@Testcontainers
@DisplayName("DynamoDB Transaction Integration Tests")
class DynamoDbTransactionIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0"))
            .withServices(Service.DYNAMODB);

    private DynamoDbAsyncClient dynamoDbAsyncClient;
    private DynamoDbEnhancedAsyncClient enhancedClient;
    private DynamoDbService<TransactionTestEntity> dynamoDbService;

    private static final String USERS_TABLE = "transaction-users";
    private static final String ORDERS_TABLE = "transaction-orders";

    @BeforeEach
    void setUp() throws Exception {
        // Setup DynamoDB clients
        dynamoDbAsyncClient = DynamoDbAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .endpointOverride(localstack.getEndpoint())
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();

        enhancedClient = DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();

        // Setup service with both enhanced and raw clients
        dynamoDbService = new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient);

        // Create test tables
        createTestTables();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
        if (dynamoDbAsyncClient != null) {
            dynamoDbAsyncClient.close();
        }
    }

    @Nested
    @DisplayName("Basic Transaction Operations")
    class BasicTransactionTests {

        @Test
        @DisplayName("단일 Put 트랜잭션이 성공적으로 실행되어야 함")
        void singlePutTransaction_ShouldExecuteSuccessfully() {
            // Given
            TransactionTestEntity user = new TransactionTestEntity("tx-user-1", "USER", "John Doe", 100);
            DynamoTransaction transaction = DynamoTransaction.singlePut(user, USERS_TABLE);

            // When
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);

            // Then
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
            
            // Verify item was saved
            DynamoKey key = DynamoKey.sortKey("id", "tx-user-1", "type", "USER");
            TransactionTestEntity savedUser = dynamoDbService.load(TransactionTestEntity.class, key, USERS_TABLE).join();
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getName()).isEqualTo("John Doe");
            assertThat(savedUser.getBalance()).isEqualTo(100);
        }

        @Test
        @DisplayName("단일 Update 트랜잭션이 성공적으로 실행되어야 함")
        void singleUpdateTransaction_ShouldExecuteSuccessfully() {
            // Given - 먼저 아이템 저장
            TransactionTestEntity user = new TransactionTestEntity("tx-user-2", "USER", "Jane Doe", 200);
            dynamoDbService.save(user, USERS_TABLE).join();

            DynamoKey key = DynamoKey.sortKey("id", "tx-user-2", "type", "USER");
            DynamoTransaction transaction = DynamoTransaction.singleUpdate(
                    key, USERS_TABLE, "SET #balance = #balance + :amount", 50);

            // When
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);

            // Then
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
            
            // Verify balance was updated
            TransactionTestEntity updatedUser = dynamoDbService.load(TransactionTestEntity.class, key, USERS_TABLE).join();
            assertThat(updatedUser.getBalance()).isEqualTo(250);
        }

        @Test
        @DisplayName("단일 Delete 트랜잭션이 성공적으로 실행되어야 함")
        void singleDeleteTransaction_ShouldExecuteSuccessfully() {
            // Given - 먼저 아이템 저장
            TransactionTestEntity user = new TransactionTestEntity("tx-user-3", "USER", "Bob Smith", 300);
            dynamoDbService.save(user, USERS_TABLE).join();

            DynamoKey key = DynamoKey.sortKey("id", "tx-user-3", "type", "USER");
            DynamoTransaction transaction = DynamoTransaction.singleDelete(key, USERS_TABLE);

            // When
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);

            // Then
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
            
            // Verify item was deleted
            TransactionTestEntity deletedUser = dynamoDbService.load(TransactionTestEntity.class, key, USERS_TABLE).join();
            assertThat(deletedUser).isNull();
        }
    }

    @Nested
    @DisplayName("Multi-Item Transaction Operations")
    class MultiItemTransactionTests {

        @Test
        @DisplayName("여러 테이블에 걸친 복합 트랜잭션이 성공적으로 실행되어야 함")
        void multiTableTransaction_ShouldExecuteAtomically() {
            // Given
            TransactionTestEntity user = new TransactionTestEntity("tx-user-4", "USER", "Alice Brown", 500);
            TransactionTestEntity order = new TransactionTestEntity("order-1", "ORDER", "Order for Alice", 100);

            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(user, USERS_TABLE)
                    .put(order, ORDERS_TABLE)
                    .update(
                            DynamoKey.sortKey("id", "tx-user-4", "type", "USER"),
                            USERS_TABLE,
                            "SET #balance = #balance - :amount",
                            100
                    )
                    .build();

            // When
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);

            // Then
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
            
            // Verify both items exist and user balance is updated
            DynamoKey userKey = DynamoKey.sortKey("id", "tx-user-4", "type", "USER");
            DynamoKey orderKey = DynamoKey.sortKey("id", "order-1", "type", "ORDER");
            
            TransactionTestEntity savedUser = dynamoDbService.load(TransactionTestEntity.class, userKey, USERS_TABLE).join();
            TransactionTestEntity savedOrder = dynamoDbService.load(TransactionTestEntity.class, orderKey, ORDERS_TABLE).join();
            
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getBalance()).isEqualTo(400); // 500 - 100
            assertThat(savedOrder).isNotNull();
            assertThat(savedOrder.getName()).isEqualTo("Order for Alice");
        }

        @Test
        @DisplayName("조건부 체크가 포함된 트랜잭션이 성공적으로 실행되어야 함")
        void transactionWithConditionCheck_ShouldExecuteSuccessfully() {
            // Given - 기존 사용자 생성
            TransactionTestEntity existingUser = new TransactionTestEntity("tx-user-5", "USER", "Charlie Green", 1000);
            dynamoDbService.save(existingUser, USERS_TABLE).join();

            DynamoKey userKey = DynamoKey.sortKey("id", "tx-user-5", "type", "USER");
            TransactionTestEntity order = new TransactionTestEntity("order-2", "ORDER", "High Value Order", 500);

            DynamoTransaction transaction = DynamoTransaction.builder()
                    .conditionCheck(userKey, USERS_TABLE, "#balance >= :minBalance", 500)
                    .put(order, ORDERS_TABLE)
                    .update(userKey, USERS_TABLE, "SET #balance = #balance - :amount", 500)
                    .build();

            // When
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);

            // Then
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
            
            // Verify transaction effects
            TransactionTestEntity updatedUser = dynamoDbService.load(TransactionTestEntity.class, userKey, USERS_TABLE).join();
            DynamoKey orderKey = DynamoKey.sortKey("id", "order-2", "type", "ORDER");
            TransactionTestEntity savedOrder = dynamoDbService.load(TransactionTestEntity.class, orderKey, ORDERS_TABLE).join();
            
            assertThat(updatedUser.getBalance()).isEqualTo(500); // 1000 - 500
            assertThat(savedOrder).isNotNull();
        }
    }

    @Nested
    @DisplayName("Transaction Error Scenarios")
    class TransactionErrorTests {

        @Test
        @DisplayName("조건부 체크 실패 시 전체 트랜잭션이 실패해야 함")
        void transactionWithFailingCondition_ShouldFailAtomically() {
            // Given - 잔액이 부족한 사용자
            TransactionTestEntity poorUser = new TransactionTestEntity("tx-user-6", "USER", "Poor User", 50);
            dynamoDbService.save(poorUser, USERS_TABLE).join();

            DynamoKey userKey = DynamoKey.sortKey("id", "tx-user-6", "type", "USER");
            TransactionTestEntity expensiveOrder = new TransactionTestEntity("order-3", "ORDER", "Expensive Order", 1000);

            DynamoTransaction transaction = DynamoTransaction.builder()
                    .conditionCheck(userKey, USERS_TABLE, "#balance >= :minBalance", 1000) // 이 조건이 실패함
                    .put(expensiveOrder, ORDERS_TABLE) // 이 작업도 롤백되어야 함
                    .update(userKey, USERS_TABLE, "SET #balance = #balance - :amount", 1000)
                    .build();

            // When & Then
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
            
            // Verify no changes were made
            TransactionTestEntity unchangedUser = dynamoDbService.load(TransactionTestEntity.class, userKey, USERS_TABLE).join();
            DynamoKey orderKey = DynamoKey.sortKey("id", "order-3", "type", "ORDER");
            TransactionTestEntity nonExistentOrder = dynamoDbService.load(TransactionTestEntity.class, orderKey, ORDERS_TABLE).join();
            
            assertThat(unchangedUser.getBalance()).isEqualTo(50); // 변경되지 않음
            assertThat(nonExistentOrder).isNull(); // 생성되지 않음
        }

        @Test
        @DisplayName("빈 트랜잭션 요청 시 예외가 발생해야 함")
        void emptyTransaction_ShouldThrowException() {
            // Given
            DynamoTransaction emptyTransaction = null;

            // When & Then
            CompletableFuture<Void> result = dynamoDbService.transactWrite(emptyTransaction);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DynamoTransaction cannot be null");
        }

        @Test
        @DisplayName("너무 많은 아이템을 포함한 트랜잭션 시 예외가 발생해야 함")
        void oversizedTransaction_ShouldThrowException() {
            // Given - 100개 초과 아이템으로 트랜잭션 구성
            DynamoTransaction.Builder builder = DynamoTransaction.builder();
            for (int i = 0; i < 101; i++) {
                TransactionTestEntity item = new TransactionTestEntity("item-" + i, "ITEM", "Item " + i, i);
                builder.put(item, USERS_TABLE);
            }

            // When & Then
            assertThatThrownBy(builder::build)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Transaction cannot contain more than 100 items");
        }
    }

    @Nested
    @DisplayName("ACID Properties Verification")
    class ACIDPropertiesTests {

        @Test
        @DisplayName("트랜잭션의 원자성(Atomicity)이 보장되어야 함")
        void transactionAtomicity_ShouldBeGuaranteed() {
            // Given - 중간에 실패하는 트랜잭션 생성
            TransactionTestEntity user1 = new TransactionTestEntity("atomic-user-1", "USER", "User 1", 100);
            TransactionTestEntity user2 = new TransactionTestEntity("atomic-user-2", "USER", "User 2", 200);
            
            // 먼저 user2를 저장하여 중복 키 에러를 유발
            dynamoDbService.save(user2, USERS_TABLE).join();

            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(user1, USERS_TABLE) // 성공해야 하는 작업
                    .conditionCheck( // 실패해야 하는 조건 체크
                            DynamoKey.sortKey("id", "atomic-user-2", "type", "USER"),
                            USERS_TABLE, 
                            "#balance > :amount", 
                            1000 // user2의 잔액(200)보다 큰 값
                    )
                    .build();

            // When & Then
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class);
            
            // Verify user1 was not created (atomicity)
            DynamoKey user1Key = DynamoKey.sortKey("id", "atomic-user-1", "type", "USER");
            TransactionTestEntity nonExistentUser = dynamoDbService.load(TransactionTestEntity.class, user1Key, USERS_TABLE).join();
            assertThat(nonExistentUser).isNull();
        }

        @Test
        @DisplayName("트랜잭션의 일관성(Consistency)이 보장되어야 함")
        void transactionConsistency_ShouldBeGuaranteed() {
            // Given - 계좌 이체 시나리오
            TransactionTestEntity fromAccount = new TransactionTestEntity("account-from", "ACCOUNT", "From Account", 1000);
            TransactionTestEntity toAccount = new TransactionTestEntity("account-to", "ACCOUNT", "To Account", 500);
            
            dynamoDbService.save(fromAccount, USERS_TABLE).join();
            dynamoDbService.save(toAccount, USERS_TABLE).join();

            int transferAmount = 300;
            DynamoKey fromKey = DynamoKey.sortKey("id", "account-from", "type", "ACCOUNT");
            DynamoKey toKey = DynamoKey.sortKey("id", "account-to", "type", "ACCOUNT");

            DynamoTransaction transaction = DynamoTransaction.builder()
                    .conditionCheck(fromKey, USERS_TABLE, "#balance >= :amount", transferAmount)
                    .update(fromKey, USERS_TABLE, "SET #balance = #balance - :amount", transferAmount)
                    .update(toKey, USERS_TABLE, "SET #balance = #balance + :amount", transferAmount)
                    .build();

            // When
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);

            // Then - Verify consistency (total balance unchanged)
            TransactionTestEntity updatedFromAccount = dynamoDbService.load(TransactionTestEntity.class, fromKey, USERS_TABLE).join();
            TransactionTestEntity updatedToAccount = dynamoDbService.load(TransactionTestEntity.class, toKey, USERS_TABLE).join();
            
            int totalBalanceAfter = updatedFromAccount.getBalance() + updatedToAccount.getBalance();
            int totalBalanceBefore = fromAccount.getBalance() + toAccount.getBalance();
            
            assertThat(totalBalanceAfter).isEqualTo(totalBalanceBefore);
            assertThat(updatedFromAccount.getBalance()).isEqualTo(700); // 1000 - 300
            assertThat(updatedToAccount.getBalance()).isEqualTo(800); // 500 + 300
        }

        @Test
        @DisplayName("트랜잭션의 격리성(Isolation)이 보장되어야 함")
        void transactionIsolation_ShouldBeGuaranteed() {
            // Given - 동시성 테스트를 위한 공유 리소스
            TransactionTestEntity sharedResource = new TransactionTestEntity("shared-resource", "RESOURCE", "Shared", 1000);
            dynamoDbService.save(sharedResource, USERS_TABLE).join();

            DynamoKey resourceKey = DynamoKey.sortKey("id", "shared-resource", "type", "RESOURCE");

            // 두 개의 동시 트랜잭션 생성
            DynamoTransaction transaction1 = DynamoTransaction.builder()
                    .update(resourceKey, USERS_TABLE, "SET #balance = #balance - :amount", 100)
                    .build();

            DynamoTransaction transaction2 = DynamoTransaction.builder()
                    .update(resourceKey, USERS_TABLE, "SET #balance = #balance - :amount", 200)
                    .build();

            // When - 동시에 실행
            CompletableFuture<Void> result1 = dynamoDbService.transactWrite(transaction1);
            CompletableFuture<Void> result2 = dynamoDbService.transactWrite(transaction2);

            // Then - 둘 다 성공해야 함 (DynamoDB는 결과적 일관성을 보장)
            assertThat(result1).succeedsWithin(5, TimeUnit.SECONDS);
            assertThat(result2).succeedsWithin(5, TimeUnit.SECONDS);
            
            // 최종 결과 검증 (1000 - 100 - 200 = 700)
            TransactionTestEntity finalResource = dynamoDbService.load(TransactionTestEntity.class, resourceKey, USERS_TABLE).join();
            assertThat(finalResource.getBalance()).isEqualTo(700);
        }

        @Test
        @DisplayName("트랜잭션의 지속성(Durability)이 보장되어야 함")
        void transactionDurability_ShouldBeGuaranteed() {
            // Given
            TransactionTestEntity durabilityTest = new TransactionTestEntity("durable-item", "DURABLE", "Durable Item", 999);
            DynamoTransaction transaction = DynamoTransaction.singlePut(durabilityTest, USERS_TABLE);

            // When - 트랜잭션 실행
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);

            // Then - 새로운 서비스 인스턴스로 데이터 조회 (지속성 검증)
            DynamoDbService<TransactionTestEntity> newServiceInstance = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient);
            
            DynamoKey key = DynamoKey.sortKey("id", "durable-item", "type", "DURABLE");
            TransactionTestEntity persistedItem = newServiceInstance.load(TransactionTestEntity.class, key, USERS_TABLE).join();
            
            assertThat(persistedItem).isNotNull();
            assertThat(persistedItem.getName()).isEqualTo("Durable Item");
            assertThat(persistedItem.getBalance()).isEqualTo(999);
        }
    }

    private void createTestTables() {
        createTableForEntity(USERS_TABLE);
        createTableForEntity(ORDERS_TABLE);
    }

    private void createTableForEntity(String tableName) {
        var table = enhancedClient.table(tableName, TableSchema.fromBean(TransactionTestEntity.class));
        
        try {
            CreateTableEnhancedRequest request = CreateTableEnhancedRequest.builder()
                    .build();
            
            table.createTable(request).join();
        } catch (Exception e) {
            // Table might already exist, ignore
            if (!e.getMessage().contains("Table already exists") && 
                !e.getMessage().contains("ResourceInUseException")) {
                throw e;
            }
        }
    }

    private void cleanupTestData() {
        try {
            cleanupTable(USERS_TABLE);
            cleanupTable(ORDERS_TABLE);
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    private void cleanupTable(String tableName) {
        try {
            var table = enhancedClient.table(tableName, TableSchema.fromBean(TransactionTestEntity.class));
            dynamoDbService.scan(TransactionTestEntity.class, tableName).join()
                    .forEach(item -> {
                        DynamoKey key = DynamoKey.sortKey("id", item.getId(), "type", item.getType());
                        try {
                            dynamoDbService.delete(key, tableName, TransactionTestEntity.class).join();
                        } catch (Exception e) {
                            // Ignore individual delete errors
                        }
                    });
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @DynamoDbBean
    public static class TransactionTestEntity {
        private String id;
        private String type;
        private String name;
        private Integer balance;

        public TransactionTestEntity() {}

        public TransactionTestEntity(String id, String type, String name, Integer balance) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.balance = balance;
        }

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @DynamoDbSortKey
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getBalance() {
            return balance;
        }

        public void setBalance(Integer balance) {
            this.balance = balance;
        }
    }
}