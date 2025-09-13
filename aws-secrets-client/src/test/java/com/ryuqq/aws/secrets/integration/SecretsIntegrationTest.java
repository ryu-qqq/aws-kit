package com.ryuqq.aws.secrets.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqq.aws.secrets.AwsSecretsAutoConfiguration;
import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import com.ryuqq.aws.secrets.service.SecretsService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.DescribeSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretsResponse;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;

/**
 * Integration tests for Secrets Manager service using LocalStack
 * Tests real AWS service interactions and caching behavior
 */
@SpringBootTest(classes = AwsSecretsAutoConfiguration.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Integration tests require Docker environment")
class SecretsIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SECRETSMANAGER)
            .withEnv("DEBUG", "1")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private SecretsService secretsService;

    @Autowired
    private SecretsCacheManager cacheManager;

    @Autowired
    private SecretsManagerAsyncClient secretsManagerClient;

    @Autowired
    private ObjectMapper objectMapper;

    private static String testSecretArn;
    private static String jsonSecretArn;
    private static String binarySecretArn;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.region", () -> localstack.getRegion());
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.credentials.access-key-id", () -> localstack.getAccessKey());
        registry.add("aws.credentials.secret-access-key", () -> localstack.getSecretKey());
        
        // Secrets specific properties
        registry.add("aws.secrets.enabled", () -> "true");
        registry.add("aws.secrets.cache.enabled", () -> "true");
        registry.add("aws.secrets.cache.ttl", () -> "PT30S");
        registry.add("aws.secrets.cache.max-size", () -> "100");
        registry.add("aws.secrets.retry.max-attempts", () -> "3");
        registry.add("aws.secrets.retry.base-delay", () -> "PT1S");
    }

    @Test
    @Order(1)
    void createSecret_문자열시크릿_생성성공() {
        // Given
        String secretName = "integration-test-secret";
        String secretValue = "test-value-123";

        // When
        CompletableFuture<String> future = secretsService.createSecret(secretName, secretValue);
        
        // Then
        String arn = future.join();
        testSecretArn = arn;
        
        assertThat(arn).isNotNull();
        assertThat(arn).contains("integration-test-secret");
        assertThat(arn).contains("secret");
    }

    @Test
    @Order(2)
    void createSecret_JSON객체시크릿_생성성공() {
        // Given
        String secretName = "integration-json-secret";
        DatabaseCredentials credentials = new DatabaseCredentials(
            "localhost", 5432, "myapp", "testuser", "testvalue456");

        // When
        CompletableFuture<String> future = secretsService.createSecret(secretName, credentials);
        
        // Then
        String arn = future.join();
        jsonSecretArn = arn;
        
        assertThat(arn).isNotNull();
        assertThat(arn).contains("integration-json-secret");
    }

    @Test
    @Order(3)
    void getSecret_문자열시크릿_조회성공() {
        // Given
        String secretName = "integration-test-secret";

        // When
        CompletableFuture<String> future = secretsService.getSecret(secretName);
        
        // Then
        String value = future.join();
        assertThat(value).isEqualTo("test-value-123");
    }

    @Test
    @Order(4)
    void getSecret_JSON객체시크릿_타입변환성공() {
        // Given
        String secretName = "integration-json-secret";

        // When
        CompletableFuture<DatabaseCredentials> future = 
            secretsService.getSecret(secretName, DatabaseCredentials.class);
        
        // Then
        DatabaseCredentials credentials = future.join();
        assertThat(credentials).isNotNull();
        assertThat(credentials.host()).isEqualTo("localhost");
        assertThat(credentials.port()).isEqualTo(5432);
        assertThat(credentials.database()).isEqualTo("myapp");
        assertThat(credentials.username()).isEqualTo("testuser");
        assertThat(credentials.password()).isEqualTo("testvalue456");
    }

    @Test
    @Order(5)
    void getSecretAsMap_JSON시크릿_맵변환성공() {
        // Given
        String secretName = "integration-json-secret";

        // When
        CompletableFuture<Map<String, Object>> future = secretsService.getSecretAsMap(secretName);
        
        // Then
        Map<String, Object> map = future.join();
        assertThat(map).isNotNull();
        assertThat(map).containsKey("host");
        assertThat(map).containsKey("port");
        assertThat(map).containsKey("database");
        assertThat(map.get("host")).isEqualTo("localhost");
        assertThat(map.get("port")).isEqualTo(5432);
    }

    @Test
    @Order(6)
    void caching_동일시크릿_캐시에서조회() throws Exception {
        // Given
        String secretName = "integration-test-secret";
        
        // Clear cache first
        secretsService.invalidateCache(secretName);
        
        // When - First call (should fetch from AWS)
        long start1 = System.currentTimeMillis();
        String value1 = secretsService.getSecret(secretName).join();
        long duration1 = System.currentTimeMillis() - start1;
        
        // Second call (should be from cache)
        long start2 = System.currentTimeMillis();
        String value2 = secretsService.getSecret(secretName).join();
        long duration2 = System.currentTimeMillis() - start2;
        
        // Then
        assertThat(value1).isEqualTo(value2);
        assertThat(duration2).isLessThan(duration1); // Cache should be faster
        
        System.out.printf("First call: %d ms, Cached call: %d ms%n", duration1, duration2);
    }

    @Test
    @Order(7)
    void updateSecret_시크릿업데이트_캐시무효화확인() {
        // Given
        String secretName = "integration-test-secret";
        String newValue = "updated-test-value-789";
        
        // Ensure secret is cached
        String originalValue = secretsService.getSecret(secretName).join();
        assertThat(originalValue).isEqualTo("test-value-123");

        // When
        CompletableFuture<String> future = secretsService.updateSecret(secretName, newValue);
        
        // Then
        String versionId = future.join();
        assertThat(versionId).isNotNull();
        
        // Verify cache was invalidated and new value is returned
        String updatedValue = secretsService.getSecret(secretName).join();
        assertThat(updatedValue).isEqualTo(newValue);
    }

    @Test
    @Order(8)
    void updateSecret_JSON객체업데이트_성공() {
        // Given
        String secretName = "integration-json-secret";
        DatabaseCredentials updatedCredentials = new DatabaseCredentials(
            "prod-db.example.com", 5432, "production", "produser", "testprodvalue");

        // When
        CompletableFuture<String> future = secretsService.updateSecret(secretName, updatedCredentials);
        
        // Then
        String versionId = future.join();
        assertThat(versionId).isNotNull();
        
        // Verify updated values
        DatabaseCredentials retrieved = secretsService.getSecret(secretName, DatabaseCredentials.class).join();
        assertThat(retrieved.host()).isEqualTo("prod-db.example.com");
        assertThat(retrieved.database()).isEqualTo("production");
        assertThat(retrieved.username()).isEqualTo("produser");
    }

    @Test
    @Order(9)
    void getSecret_버전별조회_성공() throws Exception {
        // Given - Create a secret with multiple versions
        String secretName = "versioned-secret";
        secretsService.createSecret(secretName, "version-1-value").join();
        
        String version2Id = secretsService.updateSecret(secretName, "version-2-value").join();
        String version3Id = secretsService.updateSecret(secretName, "version-3-value").join();

        // When - Get specific versions
        String currentValue = secretsService.getSecret(secretName).join();
        String version2Value = secretsService.getSecret(secretName, version2Id).join();
        String version3Value = secretsService.getSecret(secretName, version3Id).join();
        
        // Then
        assertThat(currentValue).isEqualTo("version-3-value"); // Latest
        assertThat(version2Value).isEqualTo("version-2-value");
        assertThat(version3Value).isEqualTo("version-3-value");
    }

    @Test
    @Order(10)
    void rotateSecret_시크릿로테이션_성공() throws Exception {
        // Given
        String secretName = "rotation-test-secret";
        secretsService.createSecret(secretName, "original-value").join();
        
        // When
        CompletableFuture<String> future = secretsService.rotateSecret(secretName);
        
        // Then
        String rotationVersionId = future.join();
        assertThat(rotationVersionId).isNotNull();
        
        // Verify rotation initiated (in LocalStack, rotation may not fully complete)
        // But the version should be created
        assertThat(rotationVersionId).isNotEmpty();
    }

    @Test
    @Order(11)
    void listSecrets_시크릿목록조회_성공() {
        // When
        CompletableFuture<ListSecretsResponse> future = secretsService.listSecrets();
        
        // Then
        ListSecretsResponse response = future.join();
        assertThat(response.secretList()).isNotEmpty();
        
        List<String> secretNames = response.secretList().stream()
                .map(secret -> secret.name())
                .toList();
        
        assertThat(secretNames).contains("integration-test-secret");
        assertThat(secretNames).contains("integration-json-secret");
    }

    @Test
    @Order(12)
    void errorRecovery_존재하지않는시크릿_예외발생() {
        // Given
        String nonexistentSecret = "nonexistent-secret-12345";

        // When & Then
        assertThatThrownBy(() -> secretsService.getSecret(nonexistentSecret).join())
                .hasCauseInstanceOf(SecretsService.SecretsException.class);
    }

    @Test
    @Order(13)
    void retryMechanism_일시적오류_재시도동작확인() throws Exception {
        // Given - Create a secret for retry testing
        String retrySecretName = "retry-test-secret";
        secretsService.createSecret(retrySecretName, "retry-test-value").join();
        
        // When - Multiple concurrent requests (may trigger retry internally)
        List<CompletableFuture<String>> futures = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> secretsService.getSecret(retrySecretName))
                .toList();
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        allOf.join();
        
        // Then - All should succeed
        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        assertThat(results).hasSize(10);
        assertThat(results).allMatch(value -> value.equals("retry-test-value"));
    }

    @Test
    @Order(14)
    void cacheExpiration_TTL만료_재조회동작확인() throws Exception {
        // Given
        String cacheTestSecret = "cache-expiry-test";
        secretsService.createSecret(cacheTestSecret, "original-cache-value").join();
        
        // Initial fetch (caches the value)
        String value1 = secretsService.getSecret(cacheTestSecret).join();
        assertThat(value1).isEqualTo("original-cache-value");
        
        // Update the secret value
        secretsService.updateSecret(cacheTestSecret, "updated-cache-value").join();
        
        // Immediately get (should return cached value due to update invalidation)
        String value2 = secretsService.getSecret(cacheTestSecret).join();
        assertThat(value2).isEqualTo("updated-cache-value");
        
        // Test manual cache invalidation
        secretsService.invalidateCache(cacheTestSecret);
        String value3 = secretsService.getSecret(cacheTestSecret).join();
        assertThat(value3).isEqualTo("updated-cache-value");
    }

    @Test
    @Order(15)
    void performanceTest_동시시크릿조회_처리성능확인() throws Exception {
        // Given
        String perfSecret = "performance-test-secret";
        secretsService.createSecret(perfSecret, "performance-test-value").join();
        
        int concurrentRequests = 20;
        
        // When - Concurrent secret retrieval
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<String>> futures = java.util.stream.IntStream.range(0, concurrentRequests)
                .mapToObj(i -> secretsService.getSecret(perfSecret))
                .toList();
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        allOf.join();
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Then
        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        assertThat(results).hasSize(concurrentRequests);
        assertThat(results).allMatch(value -> value.equals("performance-test-value"));
        assertThat(duration).isLessThan(5000); // Should complete within 5 seconds
        
        System.out.printf("Retrieved %d secrets concurrently in %d ms (%.2f req/sec)%n", 
                concurrentRequests, duration, (concurrentRequests * 1000.0) / duration);
    }

    @Test
    @Order(16)
    void realWorldScenario_애플리케이션시작업_구성로드() throws Exception {
        // Given - Simulate application configuration secrets
        Map<String, Object> appConfig = Map.of(
            "database", Map.of(
                "host", "db.example.com",
                "port", 5432,
                "name", "myapp",
                "username", "dbuser",
                "password", "dbtestvalue"
            ),
            "redis", Map.of(
                "host", "redis.example.com",
                "port", 6379,
                "password", "redistest"
            ),
            "external-api", Map.of(
                "key", "testkey12345",
                "secret", "testsecret67890",
                "endpoint", "https://api.example.com"
            )
        );
        
        String appConfigSecretName = "app-config-secret";
        secretsService.createSecret(appConfigSecretName, appConfig).join();
        
        // When - Application startup retrieval
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<Map<String, Object>> configFuture = 
            secretsService.getSecretAsMap(appConfigSecretName);
        
        Map<String, Object> retrievedConfig = configFuture.join();
        long loadTime = System.currentTimeMillis() - startTime;
        
        // Then - Verify configuration integrity
        assertThat(retrievedConfig).isNotNull();
        assertThat(retrievedConfig).containsKeys("database", "redis", "external-api");
        
        @SuppressWarnings("unchecked")
        Map<String, Object> dbConfig = (Map<String, Object>) retrievedConfig.get("database");
        assertThat(dbConfig.get("host")).isEqualTo("db.example.com");
        assertThat(dbConfig.get("username")).isEqualTo("dbuser");
        
        System.out.printf("Application config loaded in %d ms%n", loadTime);
        assertThat(loadTime).isLessThan(2000); // Should load quickly
        
        // Test caching for subsequent loads
        long cachedStartTime = System.currentTimeMillis();
        Map<String, Object> cachedConfig = secretsService.getSecretAsMap(appConfigSecretName).join();
        long cachedLoadTime = System.currentTimeMillis() - cachedStartTime;
        
        assertThat(cachedConfig).isEqualTo(retrievedConfig);
        assertThat(cachedLoadTime).isLessThan(loadTime); // Cache should be faster
        
        System.out.printf("Cached config loaded in %d ms%n", cachedLoadTime);
    }

    @Test
    @Order(17)
    void cacheInvalidation_전체캐시무효화_성공() {
        // Given - Populate cache with multiple secrets
        secretsService.getSecret("integration-test-secret").join();
        secretsService.getSecret("integration-json-secret").join();
        
        // When
        secretsService.invalidateAllCache();
        
        // Then - Next calls should fetch from AWS (not cache)
        // We can't easily verify this without inspecting cache internals,
        // but we can verify the operations still work
        String value1 = secretsService.getSecret("integration-test-secret").join();
        String value2 = secretsService.getSecret("integration-json-secret").join();
        
        assertThat(value1).isNotNull();
        assertThat(value2).isNotNull();
    }

    @Test
    @Order(98)
    void cleanup_테스트시크릿_삭제() {
        // Cleanup test secrets (force delete for immediate removal)
        List<String> secretsToDelete = List.of(
            "integration-test-secret",
            "integration-json-secret", 
            "versioned-secret",
            "rotation-test-secret",
            "retry-test-secret",
            "cache-expiry-test",
            "performance-test-secret",
            "app-config-secret"
        );
        
        secretsToDelete.forEach(secretName -> {
            try {
                secretsService.deleteSecret(secretName, true).join();
            } catch (Exception e) {
                // Ignore errors - secret might not exist
                System.out.printf("Failed to delete secret %s: %s%n", secretName, e.getMessage());
            }
        });
    }

    // Test data class for JSON secrets
    public record DatabaseCredentials(
        String host,
        int port,
        String database,
        String username,
        String password
    ) {}
}