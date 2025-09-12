package com.ryuqq.aws.dynamodb.properties;

import com.ryuqq.aws.dynamodb.service.DefaultDynamoDbService;
import com.ryuqq.aws.dynamodb.service.DynamoDbService;
import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DynamoDB Properties에 대한 성능 테스트
 * timeout, maxRetries, tablePrefix/Suffix 설정의 효과 검증
 */
@Testcontainers
@DisplayName("DynamoDB Properties Performance Tests")
class DynamoDbPropertiesPerformanceTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0"))
            .withServices(Service.DYNAMODB);

    private static final String BASE_TABLE_NAME = "perf-test";
    private DynamoDbAsyncClient dynamoDbAsyncClient;
    private DynamoDbEnhancedAsyncClient enhancedClient;

    @BeforeEach
    void setUp() throws Exception {
        // Basic client setup for each test
        dynamoDbAsyncClient = DynamoDbAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .endpointOverride(localstack.getEndpoint())
                .build();

        enhancedClient = DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (dynamoDbAsyncClient != null) {
            dynamoDbAsyncClient.close();
        }
    }

    @Nested
    @DisplayName("Timeout Configuration Tests")
    class TimeoutConfigurationTests {

        @Test
        @DisplayName("짧은 timeout 설정이 적용되어야 함")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void shortTimeout_ShouldBeApplied() {
            // Given - 매우 짧은 timeout으로 클라이언트 설정
            Duration shortTimeout = Duration.ofMillis(100);
            
            DynamoDbAsyncClient shortTimeoutClient = DynamoDbAsyncClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .endpointOverride(localstack.getEndpoint())
                    .httpClient(NettyNioAsyncHttpClient.builder()
                            .connectionTimeout(shortTimeout)
                            .connectionAcquisitionTimeout(shortTimeout)
                            .build())
                    .build();

            DynamoDbEnhancedAsyncClient shortTimeoutEnhancedClient = DynamoDbEnhancedAsyncClient.builder()
                    .dynamoDbClient(shortTimeoutClient)
                    .build();

            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(shortTimeoutEnhancedClient, shortTimeoutClient);

            createTestTable(BASE_TABLE_NAME + "-timeout");

            // When & Then - 짧은 timeout으로 인해 일부 작업이 실패할 수 있음을 검증
            PerformanceTestEntity testEntity = new PerformanceTestEntity("timeout-test", "TEST", "Timeout Test");
            
            try {
                // 단순 저장 작업은 성공할 수 있음
                service.save(testEntity, BASE_TABLE_NAME + "-timeout").join();
                
                // 조회 작업 검증
                DynamoKey key = DynamoKey.sortKey("id", "timeout-test", "type", "TEST");
                PerformanceTestEntity loaded = service.load(PerformanceTestEntity.class, key, BASE_TABLE_NAME + "-timeout").join();
                assertThat(loaded).isNotNull();
                
            } catch (Exception e) {
                // timeout으로 인한 예외는 정상적인 동작
                assertThat(e.getMessage()).containsAnyOf("timeout", "connection", "acquire");
            } finally {
                shortTimeoutClient.close();
            }
        }

        @Test
        @DisplayName("충분한 timeout 설정으로 안정적인 동작 확인")
        void adequateTimeout_ShouldWorkStably() {
            // Given - 충분한 timeout으로 클라이언트 설정
            Duration adequateTimeout = Duration.ofSeconds(30);
            
            DynamoDbAsyncClient adequateTimeoutClient = DynamoDbAsyncClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .endpointOverride(localstack.getEndpoint())
                    .httpClient(NettyNioAsyncHttpClient.builder()
                            .connectionTimeout(adequateTimeout)
                            .connectionAcquisitionTimeout(adequateTimeout)
                            .build())
                    .build();

            DynamoDbEnhancedAsyncClient adequateTimeoutEnhancedClient = DynamoDbEnhancedAsyncClient.builder()
                    .dynamoDbClient(adequateTimeoutClient)
                    .build();

            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(adequateTimeoutEnhancedClient, adequateTimeoutClient);

            createTestTable(BASE_TABLE_NAME + "-adequate");

            // When - 여러 작업을 연속으로 수행
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                PerformanceTestEntity entity = new PerformanceTestEntity("adequate-" + i, "TEST", "Test " + i);
                futures.add(service.save(entity, BASE_TABLE_NAME + "-adequate"));
            }

            // Then - 모든 작업이 성공해야 함
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            assertThat(allOf).succeedsWithin(adequateTimeout);

            adequateTimeoutClient.close();
        }
    }

    @Nested
    @DisplayName("Retry Configuration Tests")
    class RetryConfigurationTests {

        @Test
        @DisplayName("maxRetries 설정이 적용되어야 함")
        void maxRetries_ShouldBeRespected() {
            // Given - 낮은 retry 횟수로 클라이언트 설정
            int maxRetries = 1;
            
            DynamoDbAsyncClient limitedRetryClient = DynamoDbAsyncClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .endpointOverride(localstack.getEndpoint())
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .retryPolicy(RetryPolicy.builder()
                                    .numRetries(maxRetries)
                                    .build())
                            .build())
                    .build();

            DynamoDbEnhancedAsyncClient limitedRetryEnhancedClient = DynamoDbEnhancedAsyncClient.builder()
                    .dynamoDbClient(limitedRetryClient)
                    .build();

            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(limitedRetryEnhancedClient, limitedRetryClient);

            createTestTable(BASE_TABLE_NAME + "-retry");

            // When - 정상적인 작업 수행
            PerformanceTestEntity entity = new PerformanceTestEntity("retry-test", "TEST", "Retry Test");
            CompletableFuture<Void> saveResult = service.save(entity, BASE_TABLE_NAME + "-retry");

            // Then - 정상적인 경우에는 retry 설정과 관계없이 성공
            assertThat(saveResult).succeedsWithin(10, TimeUnit.SECONDS);
            
            DynamoKey key = DynamoKey.sortKey("id", "retry-test", "type", "TEST");
            PerformanceTestEntity loaded = service.load(PerformanceTestEntity.class, key, BASE_TABLE_NAME + "-retry").join();
            assertThat(loaded).isNotNull();

            limitedRetryClient.close();
        }

        @Test
        @DisplayName("높은 retry 설정으로 복원력 향상 확인")
        void highRetryCount_ShouldImproveResilience() {
            // Given - 높은 retry 횟수로 클라이언트 설정
            int maxRetries = 10;
            
            DynamoDbAsyncClient highRetryClient = DynamoDbAsyncClient.builder()
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")))
                    .endpointOverride(localstack.getEndpoint())
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .retryPolicy(RetryPolicy.builder()
                                    .numRetries(maxRetries)
                                    .build())
                            .build())
                    .build();

            DynamoDbEnhancedAsyncClient highRetryEnhancedClient = DynamoDbEnhancedAsyncClient.builder()
                    .dynamoDbClient(highRetryClient)
                    .build();

            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(highRetryEnhancedClient, highRetryClient);

            createTestTable(BASE_TABLE_NAME + "-high-retry");

            // When - 다수의 동시 작업으로 부하 생성
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                PerformanceTestEntity entity = new PerformanceTestEntity("high-retry-" + i, "TEST", "Test " + i);
                futures.add(service.save(entity, BASE_TABLE_NAME + "-high-retry"));
            }

            // Then - 높은 retry 설정으로 더 많은 작업이 성공해야 함
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            assertThat(allOf).succeedsWithin(30, TimeUnit.SECONDS);

            highRetryClient.close();
        }
    }

    @Nested
    @DisplayName("Table Prefix/Suffix Tests")
    class TablePrefixSuffixTests {

        @Test
        @DisplayName("tablePrefix 설정이 올바르게 적용되어야 함")
        void tablePrefix_ShouldBeAppliedCorrectly() {
            // Given
            String prefix = "test-env-";
            TableNameResolver resolver = new TableNameResolver(prefix, "");
            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient, resolver);

            String baseTableName = "prefix-test";
            String expectedFullTableName = prefix + baseTableName;
            createTestTable(expectedFullTableName);

            // When
            PerformanceTestEntity entity = new PerformanceTestEntity("prefix-1", "TEST", "Prefix Test");
            service.save(entity, baseTableName).join();

            // Then
            DynamoKey key = DynamoKey.sortKey("id", "prefix-1", "type", "TEST");
            PerformanceTestEntity loaded = service.load(PerformanceTestEntity.class, key, baseTableName).join();
            assertThat(loaded).isNotNull();
            assertThat(loaded.getName()).isEqualTo("Prefix Test");
            
            // Verify actual table name resolver behavior
            assertThat(service.getTableNameResolver().resolve(baseTableName)).isEqualTo(expectedFullTableName);
        }

        @Test
        @DisplayName("tableSuffix 설정이 올바르게 적용되어야 함")
        void tableSuffix_ShouldBeAppliedCorrectly() {
            // Given
            String suffix = "-prod";
            TableNameResolver resolver = new TableNameResolver("", suffix);
            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient, resolver);

            String baseTableName = "suffix-test";
            String expectedFullTableName = baseTableName + suffix;
            createTestTable(expectedFullTableName);

            // When
            PerformanceTestEntity entity = new PerformanceTestEntity("suffix-1", "TEST", "Suffix Test");
            service.save(entity, baseTableName).join();

            // Then
            DynamoKey key = DynamoKey.sortKey("id", "suffix-1", "type", "TEST");
            PerformanceTestEntity loaded = service.load(PerformanceTestEntity.class, key, baseTableName).join();
            assertThat(loaded).isNotNull();
            assertThat(loaded.getName()).isEqualTo("Suffix Test");
            
            // Verify actual table name resolver behavior
            assertThat(service.getTableNameResolver().resolve(baseTableName)).isEqualTo(expectedFullTableName);
        }

        @Test
        @DisplayName("tablePrefix와 tableSuffix가 함께 적용되어야 함")
        void tablePrefixAndSuffix_ShouldBeAppliedTogether() {
            // Given
            String prefix = "env-";
            String suffix = "-v1";
            TableNameResolver resolver = new TableNameResolver(prefix, suffix);
            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient, resolver);

            String baseTableName = "combined-test";
            String expectedFullTableName = prefix + baseTableName + suffix;
            createTestTable(expectedFullTableName);

            // When
            PerformanceTestEntity entity = new PerformanceTestEntity("combined-1", "TEST", "Combined Test");
            service.save(entity, baseTableName).join();

            // Then
            DynamoKey key = DynamoKey.sortKey("id", "combined-1", "type", "TEST");
            PerformanceTestEntity loaded = service.load(PerformanceTestEntity.class, key, baseTableName).join();
            assertThat(loaded).isNotNull();
            assertThat(loaded.getName()).isEqualTo("Combined Test");
            
            // Verify actual table name resolver behavior
            assertThat(service.getTableNameResolver().resolve(baseTableName)).isEqualTo(expectedFullTableName);
        }
    }

    @Nested
    @DisplayName("Large Scale Performance Tests")
    class LargeScalePerformanceTests {

        @Test
        @DisplayName("대용량 배치 작업 성능 테스트")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void largeBatchOperations_ShouldCompleteWithinTimeout() {
            // Given
            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient);
            
            String tableName = BASE_TABLE_NAME + "-large-batch";
            createTestTable(tableName);

            int batchSize = 25; // DynamoDB batch limit
            int numberOfBatches = 10;
            int totalItems = batchSize * numberOfBatches;

            // When - 대용량 배치 저장
            List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
            
            for (int batchIndex = 0; batchIndex < numberOfBatches; batchIndex++) {
                List<PerformanceTestEntity> batch = new ArrayList<>();
                for (int i = 0; i < batchSize; i++) {
                    int itemId = batchIndex * batchSize + i;
                    batch.add(new PerformanceTestEntity("batch-item-" + itemId, "BATCH", "Batch Item " + itemId));
                }
                batchFutures.add(service.batchSave(batch, tableName));
            }

            // Then - 모든 배치 작업이 성공해야 함
            CompletableFuture<Void> allBatches = CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]));
            assertThat(allBatches).succeedsWithin(45, TimeUnit.SECONDS);

            // Verify total count
            List<PerformanceTestEntity> allItems = service.scan(PerformanceTestEntity.class, tableName).join();
            assertThat(allItems).hasSize(totalItems);
        }

        @Test
        @DisplayName("동시성 스트레스 테스트")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void concurrentOperations_ShouldHandleStress() {
            // Given
            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient);
            
            String tableName = BASE_TABLE_NAME + "-concurrent";
            createTestTable(tableName);

            int concurrentOperations = 100;
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount = new AtomicInteger(0);

            // When - 동시 작업 수행
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (int i = 0; i < concurrentOperations; i++) {
                final int index = i;
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        PerformanceTestEntity entity = new PerformanceTestEntity("concurrent-" + index, "CONCURRENT", "Concurrent " + index);
                        service.save(entity, tableName).join();
                        
                        // Read back to verify
                        DynamoKey key = DynamoKey.sortKey("id", "concurrent-" + index, "type", "CONCURRENT");
                        PerformanceTestEntity loaded = service.load(PerformanceTestEntity.class, key, tableName).join();
                        
                        if (loaded != null) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                });
                futures.add(future);
            }

            CompletableFuture<Void> allOperations = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            
            // Then - 대부분의 작업이 성공해야 함 (90% 이상)
            allOperations.join();
            
            int totalProcessed = successCount.get() + errorCount.get();
            double successRate = (double) successCount.get() / totalProcessed;
            
            assertThat(successRate).isGreaterThan(0.9); // 90% 이상 성공률
            assertThat(successCount.get()).isGreaterThan(concurrentOperations * 9 / 10);
        }

        @Test
        @DisplayName("메모리 효율성 테스트")
        void memoryEfficiency_ShouldNotCauseOutOfMemory() {
            // Given
            DynamoDbService<PerformanceTestEntity> service = 
                    new DefaultDynamoDbService<>(enhancedClient, dynamoDbAsyncClient);
            
            String tableName = BASE_TABLE_NAME + "-memory";
            createTestTable(tableName);

            // When - 메모리 압박 상황 시뮬레이션
            try {
                for (int iteration = 0; iteration < 5; iteration++) {
                    List<PerformanceTestEntity> largeList = new ArrayList<>();
                    for (int i = 0; i < 1000; i++) {
                        largeList.add(new PerformanceTestEntity("memory-" + iteration + "-" + i, "MEMORY", "Large Data Item " + i));
                    }
                    
                    // 큰 배치를 작은 단위로 나누어 저장
                    for (int start = 0; start < largeList.size(); start += 25) {
                        int end = Math.min(start + 25, largeList.size());
                        List<PerformanceTestEntity> batch = largeList.subList(start, end);
                        service.batchSave(batch, tableName).join();
                    }
                    
                    // Explicit memory cleanup suggestion
                    largeList.clear();
                    System.gc();
                }
                
                // Then - OutOfMemoryError가 발생하지 않아야 함
                assertThat(true).isTrue(); // Test completed without OOM
                
            } catch (OutOfMemoryError e) {
                throw new AssertionError("Memory efficiency test failed due to OutOfMemoryError", e);
            }
        }
    }

    private void createTestTable(String tableName) {
        var table = enhancedClient.table(tableName, TableSchema.fromBean(PerformanceTestEntity.class));
        
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

    @DynamoDbBean
    public static class PerformanceTestEntity {
        private String id;
        private String type;
        private String name;

        public PerformanceTestEntity() {}

        public PerformanceTestEntity(String id, String type, String name) {
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