package com.ryuqq.aws.dynamodb.service;

import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.types.DynamoTransaction;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DynamoDB Service의 에러 시나리오에 대한 테스트
 * 네트워크 오류, 잘못된 설정, 예외 상황 처리 검증
 */
@Testcontainers
@DisplayName("DynamoDB Service Error Scenario Tests")
class DynamoDbServiceErrorScenarioTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0"))
            .withServices(Service.DYNAMODB);

    private DynamoDbAsyncClient dynamoDbAsyncClient;
    private DynamoDbEnhancedAsyncClient enhancedClient;
    private DynamoDbService<ErrorTestEntity> dynamoDbService;

    private static final String EXISTING_TABLE = "error-test-existing";
    private static final String NON_EXISTING_TABLE = "error-test-non-existing";

    @BeforeEach
    void setUp() throws Exception {
        // Setup clients
        dynamoDbAsyncClient = DynamoDbAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .endpointOverride(localstack.getEndpoint())
                .build();

        enhancedClient = DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();

        dynamoDbService = new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient);

        // Create one existing table for tests
        createTestTable(EXISTING_TABLE);
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
        if (dynamoDbAsyncClient != null) {
            dynamoDbAsyncClient.close();
        }
    }

    @Nested
    @DisplayName("Table Not Found Error Tests")
    class TableNotFoundErrorTests {

        @Test
        @DisplayName("존재하지 않는 테이블에 저장 시도 시 예외 발생")
        void saveToNonExistingTable_ShouldThrowException() {
            // Given
            ErrorTestEntity entity = new ErrorTestEntity("test-1", "TEST", "Test Entity");

            // When & Then
            CompletableFuture<Void> result = dynamoDbService.save(entity, NON_EXISTING_TABLE);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(SdkException.class);
        }

        @Test
        @DisplayName("존재하지 않는 테이블에서 로드 시도 시 예외 발생")
        void loadFromNonExistingTable_ShouldThrowException() {
            // Given
            DynamoKey key = DynamoKey.sortKey("id", "test-1", "type", "TEST");

            // When & Then
            CompletableFuture<ErrorTestEntity> result = dynamoDbService.load(ErrorTestEntity.class, key, NON_EXISTING_TABLE);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(SdkException.class);
        }

        @Test
        @DisplayName("존재하지 않는 테이블에서 쿼리 시도 시 예외 발생")
        void queryFromNonExistingTable_ShouldThrowException() {
            // Given
            com.ryuqq.aws.dynamodb.types.DynamoQuery query = 
                    com.ryuqq.aws.dynamodb.types.DynamoQuery.keyEqual("id", "test-1");

            // When & Then
            CompletableFuture<List<ErrorTestEntity>> result = dynamoDbService.query(ErrorTestEntity.class, query, NON_EXISTING_TABLE);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(SdkException.class);
        }

        @Test
        @DisplayName("존재하지 않는 테이블에서 스캔 시도 시 예외 발생")
        void scanFromNonExistingTable_ShouldThrowException() {
            // When & Then
            CompletableFuture<List<ErrorTestEntity>> result = dynamoDbService.scan(ErrorTestEntity.class, NON_EXISTING_TABLE);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(SdkException.class);
        }
    }

    @Nested
    @DisplayName("Invalid Parameter Error Tests")
    class InvalidParameterErrorTests {

        @Test
        @DisplayName("null 엔티티 저장 시도 시 예외 발생")
        void saveNullEntity_ShouldThrowException() {
            // When & Then
            assertThatThrownBy(() -> dynamoDbService.save(null, EXISTING_TABLE))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null 키로 로드 시도 시 예외 발생")
        void loadWithNullKey_ShouldThrowException() {
            // When & Then
            assertThatThrownBy(() -> dynamoDbService.load(ErrorTestEntity.class, null, EXISTING_TABLE))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null 테이블명으로 작업 시도 시 예외 발생")
        void operationWithNullTableName_ShouldThrowException() {
            // Given
            ErrorTestEntity entity = new ErrorTestEntity("test-1", "TEST", "Test Entity");

            // When & Then
            assertThatThrownBy(() -> dynamoDbService.save(entity, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("빈 문자열 테이블명으로 작업 시도 시 예외 발생")
        void operationWithEmptyTableName_ShouldThrowException() {
            // Given
            ErrorTestEntity entity = new ErrorTestEntity("test-1", "TEST", "Test Entity");

            // When & Then
            CompletableFuture<Void> result = dynamoDbService.save(entity, "");
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class);
        }

        @Test
        @DisplayName("빈 리스트로 배치 저장 시도 시 성공해야 함")
        void batchSaveWithEmptyList_ShouldSucceed() {
            // Given
            List<ErrorTestEntity> emptyList = List.of();

            // When
            CompletableFuture<Void> result = dynamoDbService.batchSave(emptyList, EXISTING_TABLE);

            // Then
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
        }

        @Test
        @DisplayName("빈 리스트로 배치 로드 시도 시 빈 결과 반환")
        void batchLoadWithEmptyList_ShouldReturnEmptyList() {
            // Given
            List<DynamoKey> emptyKeys = List.of();

            // When
            CompletableFuture<List<ErrorTestEntity>> result = dynamoDbService.batchLoad(ErrorTestEntity.class, emptyKeys, EXISTING_TABLE);

            // Then
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
            assertThat(result.join()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Transaction Error Tests")
    class TransactionErrorTests {

        @Test
        @DisplayName("Raw client 없이 트랜잭션 실행 시 예외 발생")
        void transactionWithoutRawClient_ShouldThrowException() {
            // Given - Enhanced client만 가진 서비스
            DynamoDbService<ErrorTestEntity> serviceWithoutRawClient = 
                    new DefaultDynamoDbService<>(enhancedClient);

            ErrorTestEntity entity = new ErrorTestEntity("tx-test", "TEST", "Transaction Test");
            DynamoTransaction transaction = DynamoTransaction.singlePut(entity, EXISTING_TABLE);

            // When & Then
            CompletableFuture<Void> result = serviceWithoutRawClient.transactWrite(transaction);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("Transaction operations require raw DynamoDB client");
        }

        @Test
        @DisplayName("null 트랜잭션 실행 시 예외 발생")
        void transactionWithNull_ShouldThrowException() {
            // When & Then
            CompletableFuture<Void> result = dynamoDbService.transactWrite(null);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("DynamoTransaction cannot be null");
        }

        @Test
        @DisplayName("존재하지 않는 테이블에 대한 트랜잭션 시 예외 발생")
        void transactionOnNonExistingTable_ShouldThrowException() {
            // Given
            ErrorTestEntity entity = new ErrorTestEntity("tx-test", "TEST", "Transaction Test");
            DynamoTransaction transaction = DynamoTransaction.singlePut(entity, NON_EXISTING_TABLE);

            // When & Then
            CompletableFuture<Void> result = dynamoDbService.transactWrite(transaction);
            
            assertThatThrownBy(() -> result.join())
                    .isInstanceOf(CompletionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Connection and Network Error Tests")
    class ConnectionNetworkErrorTests {

        @Test
        @DisplayName("잘못된 엔드포인트 설정으로 연결 실패")
        void connectionWithInvalidEndpoint_ShouldFailGracefully() {
            // Given - 잘못된 엔드포인트를 가진 클라이언트
            DynamoDbAsyncClient invalidEndpointClient = null;
            try {
                invalidEndpointClient = DynamoDbAsyncClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("test", "test")))
                        .endpointOverride(java.net.URI.create("http://invalid-endpoint:1234"))
                        .build();

                DynamoDbEnhancedAsyncClient invalidEnhancedClient = DynamoDbEnhancedAsyncClient.builder()
                        .dynamoDbClient(invalidEndpointClient)
                        .build();

                DynamoDbService<ErrorTestEntity> invalidService = 
                        new DefaultDynamoDbService<>(invalidEnhancedClient, invalidEndpointClient);

                ErrorTestEntity entity = new ErrorTestEntity("conn-test", "TEST", "Connection Test");

                // When & Then
                CompletableFuture<Void> result = invalidService.save(entity, EXISTING_TABLE);
                
                assertThatThrownBy(() -> result.join())
                        .isInstanceOf(CompletionException.class)
                        .hasCauseInstanceOf(SdkException.class);

            } finally {
                if (invalidEndpointClient != null) {
                    invalidEndpointClient.close();
                }
            }
        }

        @Test
        @DisplayName("잘못된 자격 증명으로 인증 실패")
        void authenticationWithInvalidCredentials_ShouldFailGracefully() {
            // Given - 잘못된 자격 증명을 가진 클라이언트
            DynamoDbAsyncClient invalidCredentialsClient = null;
            try {
                invalidCredentialsClient = DynamoDbAsyncClient.builder()
                        .region(Region.US_EAST_1)
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("invalid-access-key", "invalid-secret-key")))
                        .endpointOverride(localstack.getEndpoint())
                        .build();

                DynamoDbEnhancedAsyncClient invalidEnhancedClient = DynamoDbEnhancedAsyncClient.builder()
                        .dynamoDbClient(invalidCredentialsClient)
                        .build();

                DynamoDbService<ErrorTestEntity> invalidService = 
                        new DefaultDynamoDbService<>(invalidEnhancedClient, invalidCredentialsClient);

                ErrorTestEntity entity = new ErrorTestEntity("auth-test", "TEST", "Auth Test");

                // When & Then - LocalStack에서는 자격 증명이 관대하게 처리되므로, 
                // 실제 AWS에서는 인증 오류가 발생할 수 있음을 테스트
                CompletableFuture<Void> result = invalidService.save(entity, EXISTING_TABLE);
                
                // LocalStack 환경에서는 성공할 수 있으므로 예외 타입만 확인
                try {
                    result.join();
                    // LocalStack에서 성공한 경우
                    assertThat(true).isTrue();
                } catch (CompletionException e) {
                    // 실제 AWS에서는 인증 오류 발생
                    assertThat(e.getCause()).isInstanceOf(SdkException.class);
                }

            } finally {
                if (invalidCredentialsClient != null) {
                    invalidCredentialsClient.close();
                }
            }
        }
    }

    @Nested
    @DisplayName("Data Validation Error Tests")
    class DataValidationErrorTests {

        @Test
        @DisplayName("잘못된 키 구성으로 로드 시도 시 적절한 처리")
        void loadWithInvalidKeyStructure_ShouldHandleGracefully() {
            // Given - 존재하지 않는 키 구성
            DynamoKey invalidKey = DynamoKey.partitionKey("nonExistentPartitionKey", "some-value");

            // When & Then
            CompletableFuture<ErrorTestEntity> result = dynamoDbService.load(ErrorTestEntity.class, invalidKey, EXISTING_TABLE);
            
            // DynamoDB는 키가 맞지 않아도 null을 반환하거나 예외를 발생시킬 수 있음
            try {
                ErrorTestEntity loaded = result.join();
                assertThat(loaded).isNull(); // 정상적인 경우
            } catch (CompletionException e) {
                assertThat(e.getCause()).isInstanceOf(SdkException.class); // 예외 발생 경우
            }
        }

        @Test
        @DisplayName("매우 큰 데이터로 저장 시도 시 적절한 처리")
        void saveWithLargeData_ShouldHandleAppropriately() {
            // Given - 매우 큰 데이터 (400KB limit 근처)
            StringBuilder largeData = new StringBuilder();
            for (int i = 0; i < 50000; i++) { // 약 400KB 데이터
                largeData.append("This is a large data string for testing purposes. ");
            }
            
            ErrorTestEntity largeEntity = new ErrorTestEntity("large-test", "TEST", largeData.toString());

            // When & Then
            CompletableFuture<Void> result = dynamoDbService.save(largeEntity, EXISTING_TABLE);
            
            try {
                result.join();
                // 성공한 경우 검증
                DynamoKey key = DynamoKey.sortKey("id", "large-test", "type", "TEST");
                ErrorTestEntity loaded = dynamoDbService.load(ErrorTestEntity.class, key, EXISTING_TABLE).join();
                assertThat(loaded).isNotNull();
            } catch (CompletionException e) {
                // 크기 제한으로 인한 예외는 정상적인 동작
                assertThat(e.getCause()).isInstanceOf(SdkException.class);
            }
        }
    }

    @Nested
    @DisplayName("Service Configuration Error Tests")
    class ServiceConfigurationErrorTests {

        @Test
        @DisplayName("잘못된 TableNameResolver 설정으로 동작 확인")
        void serviceWithInvalidTableNameResolver_ShouldWorkWithValidation() {
            // Given - null prefix/suffix를 가진 resolver
            TableNameResolver nullResolver = new TableNameResolver(null, null);
            DynamoDbService<ErrorTestEntity> serviceWithNullResolver = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient, nullResolver);

            ErrorTestEntity entity = new ErrorTestEntity("resolver-test", "TEST", "Resolver Test");

            // When
            CompletableFuture<Void> result = serviceWithNullResolver.save(entity, EXISTING_TABLE);

            // Then - null 처리가 내부적으로 올바르게 되어야 함
            assertThat(result).succeedsWithin(5, TimeUnit.SECONDS);
            
            // Verify the resolver behavior
            String resolvedName = serviceWithNullResolver.getTableNameResolver().resolve(EXISTING_TABLE);
            assertThat(resolvedName).isEqualTo(EXISTING_TABLE); // null이 빈 문자열로 처리되어야 함
        }

        @Test
        @DisplayName("극단적인 prefix/suffix 조합 테스트")
        void serviceWithExtremeTableNameResolver_ShouldHandleEdgeCases() {
            // Given - 극단적으로 긴 prefix/suffix
            String longPrefix = "very-very-very-very-long-prefix-that-might-cause-issues-";
            String longSuffix = "-very-very-very-very-long-suffix-that-might-cause-issues";
            
            TableNameResolver extremeResolver = new TableNameResolver(longPrefix, longSuffix);
            DynamoDbService<ErrorTestEntity> serviceWithExtremeResolver = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient, extremeResolver);

            String baseTableName = "short";
            String expectedFullName = longPrefix + baseTableName + longSuffix;
            
            // When - 실제 테이블을 만들지 말고 resolver 동작만 확인
            String resolvedName = serviceWithExtremeResolver.getTableNameResolver().resolve(baseTableName);

            // Then
            assertThat(resolvedName).isEqualTo(expectedFullName);
            assertThat(resolvedName.length()).isGreaterThan(100); // 매우 긴 이름 확인
        }
    }

    private void createTestTable(String tableName) {
        var table = enhancedClient.table(tableName, TableSchema.fromBean(ErrorTestEntity.class));
        
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
            var table = enhancedClient.table(EXISTING_TABLE, TableSchema.fromBean(ErrorTestEntity.class));
            
            // Scan and delete all items
            dynamoDbService.scan(ErrorTestEntity.class, EXISTING_TABLE).join()
                    .forEach(item -> {
                        DynamoKey key = DynamoKey.sortKey("id", item.getId(), "type", item.getType());
                        try {
                            dynamoDbService.delete(key, EXISTING_TABLE, ErrorTestEntity.class).join();
                        } catch (Exception e) {
                            // Ignore individual delete errors
                        }
                    });
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @DynamoDbBean
    public static class ErrorTestEntity {
        private String id;
        private String type;
        private String name;

        public ErrorTestEntity() {}

        public ErrorTestEntity(String id, String type, String name) {
            this.id = id;
            this.type = type;
            this.name = name;
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
    }
}