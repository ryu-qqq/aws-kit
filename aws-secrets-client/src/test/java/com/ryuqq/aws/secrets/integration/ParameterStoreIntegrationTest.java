package com.ryuqq.aws.secrets.integration;

import com.ryuqq.aws.secrets.AwsSecretsAutoConfiguration;
import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import com.ryuqq.aws.secrets.service.ParameterStoreService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.ssm.SsmAsyncClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SSM;

/**
 * Integration tests for Parameter Store service using LocalStack
 * Tests real AWS Systems Manager Parameter Store interactions and caching
 */
@SpringBootTest(classes = AwsSecretsAutoConfiguration.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Integration tests require Docker environment")
class ParameterStoreIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SSM)
            .withEnv("DEBUG", "1")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private ParameterStoreService parameterStoreService;

    @Autowired
    private SecretsCacheManager cacheManager;

    @Autowired
    private SsmAsyncClient ssmClient;

    private static final String TEST_APP_PREFIX = "/integration-test/myapp";
    private static final String TEST_ENV_PREFIX = "/integration-test/env";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.region", () -> localstack.getRegion());
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.credentials.access-key-id", () -> localstack.getAccessKey());
        registry.add("aws.credentials.secret-access-key", () -> localstack.getSecretKey());
        
        // Parameter Store specific properties
        registry.add("aws.secrets.enabled", () -> "true");
        registry.add("aws.secrets.cache.enabled", () -> "true");
        registry.add("aws.secrets.cache.ttl", () -> "PT30S");
        registry.add("aws.secrets.cache.max-size", () -> "100");
        registry.add("aws.secrets.parameter-store.enabled", () -> "true");
    }

    @Test
    @Order(1)
    void putParameter_문자열파라미터_생성성공() {
        // Given
        String parameterName = TEST_APP_PREFIX + "/database/host";
        String parameterValue = "localhost";

        // When
        CompletableFuture<Long> future = parameterStoreService.putParameter(
            parameterName, parameterValue, ParameterType.STRING);
        
        // Then
        Long version = future.join();
        assertThat(version).isEqualTo(1L);
    }

    @Test
    @Order(2)
    void putParameter_보안문자열파라미터_생성성공() {
        // Given
        String parameterName = TEST_APP_PREFIX + "/database/password";
        String parameterValue = "fake-integration-password";

        // When
        CompletableFuture<Long> future = parameterStoreService.putSecureParameter(
            parameterName, parameterValue);
        
        // Then
        Long version = future.join();
        assertThat(version).isEqualTo(1L);
    }

    @Test
    @Order(3)
    void putParameter_문자열리스트파라미터_생성성공() {
        // Given
        String parameterName = TEST_APP_PREFIX + "/allowed-hosts";
        String parameterValue = "localhost,example.com,api.example.com";

        // When
        CompletableFuture<Long> future = parameterStoreService.putParameter(
            parameterName, parameterValue, ParameterType.STRING_LIST);
        
        // Then
        Long version = future.join();
        assertThat(version).isEqualTo(1L);
    }

    @Test
    @Order(4)
    void getParameter_일반파라미터_조회성공() {
        // Given
        String parameterName = TEST_APP_PREFIX + "/database/host";

        // When
        CompletableFuture<String> future = parameterStoreService.getParameter(parameterName);
        
        // Then
        String value = future.join();
        assertThat(value).isEqualTo("localhost");
    }

    @Test
    @Order(5)
    void getParameter_보안파라미터_복호화조회성공() {
        // Given
        String parameterName = TEST_APP_PREFIX + "/database/password";

        // When
        CompletableFuture<String> future = parameterStoreService.getParameter(parameterName, true);
        
        // Then
        String value = future.join();
        assertThat(value).isEqualTo("fake-integration-password");
    }

    @Test
    @Order(6)
    void setupApplicationParameters_계층구조생성_성공() {
        // Given - Create hierarchical parameters for a typical application
        Map<String, ParameterConfig> parameters = Map.of(
            TEST_APP_PREFIX + "/database/port", new ParameterConfig("5432", ParameterType.STRING),
            TEST_APP_PREFIX + "/database/name", new ParameterConfig("myapp", ParameterType.STRING),
            TEST_APP_PREFIX + "/database/username", new ParameterConfig("app_user", ParameterType.STRING),
            TEST_APP_PREFIX + "/redis/host", new ParameterConfig("redis.example.com", ParameterType.STRING),
            TEST_APP_PREFIX + "/redis/port", new ParameterConfig("6379", ParameterType.STRING),
            TEST_APP_PREFIX + "/redis/password", new ParameterConfig("redis_secure_pass", ParameterType.SECURE_STRING),
            TEST_APP_PREFIX + "/api/max-connections", new ParameterConfig("100", ParameterType.STRING),
            TEST_APP_PREFIX + "/api/timeout-seconds", new ParameterConfig("30", ParameterType.STRING),
            TEST_ENV_PREFIX + "/deployment-region", new ParameterConfig("us-east-1", ParameterType.STRING),
            TEST_ENV_PREFIX + "/log-level", new ParameterConfig("INFO", ParameterType.STRING)
        );

        // When - Create all parameters
        List<CompletableFuture<Long>> futures = parameters.entrySet().stream()
                .map(entry -> parameterStoreService.putParameter(
                    entry.getKey(), 
                    entry.getValue().value(), 
                    entry.getValue().type()))
                .toList();

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        allOf.join();

        // Then - Verify all parameters were created
        List<Long> versions = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        assertThat(versions).hasSize(parameters.size());
        assertThat(versions).allMatch(version -> version >= 1L);
    }

    @Test
    @Order(7)
    void getParameters_배치파라미터조회_성공() {
        // Given
        List<String> parameterNames = List.of(
            TEST_APP_PREFIX + "/database/host",
            TEST_APP_PREFIX + "/database/port",
            TEST_APP_PREFIX + "/database/name",
            TEST_APP_PREFIX + "/database/username"
        );

        // When
        CompletableFuture<Map<String, String>> future = 
            parameterStoreService.getParameters(parameterNames);
        
        // Then
        Map<String, String> parameters = future.join();
        assertThat(parameters).hasSize(4);
        assertThat(parameters).containsEntry(TEST_APP_PREFIX + "/database/host", "localhost");
        assertThat(parameters).containsEntry(TEST_APP_PREFIX + "/database/port", "5432");
        assertThat(parameters).containsEntry(TEST_APP_PREFIX + "/database/name", "myapp");
        assertThat(parameters).containsEntry(TEST_APP_PREFIX + "/database/username", "app_user");
    }

    @Test
    @Order(8)
    void getParameters_보안파라미터포함_복호화조회성공() {
        // Given
        List<String> parameterNames = List.of(
            TEST_APP_PREFIX + "/database/username",
            TEST_APP_PREFIX + "/database/password",
            TEST_APP_PREFIX + "/redis/password"
        );

        // When
        CompletableFuture<Map<String, String>> future = 
            parameterStoreService.getParameters(parameterNames, true);
        
        // Then
        Map<String, String> parameters = future.join();
        assertThat(parameters).hasSize(3);
        assertThat(parameters).containsEntry(TEST_APP_PREFIX + "/database/username", "app_user");
        assertThat(parameters).containsEntry(TEST_APP_PREFIX + "/database/password", "fake-integration-password");
        assertThat(parameters).containsEntry(TEST_APP_PREFIX + "/redis/password", "redis_secure_pass");
    }

    @Test
    @Order(9)
    void getParametersByPath_계층조회_성공() {
        // When - Get all database parameters
        CompletableFuture<Map<String, String>> future = 
            parameterStoreService.getParametersByPath(TEST_APP_PREFIX + "/database");
        
        // Then
        Map<String, String> dbParams = future.join();
        assertThat(dbParams).hasSize(4); // host, port, name, username (password excluded as it's secure)
        assertThat(dbParams).containsKey(TEST_APP_PREFIX + "/database/host");
        assertThat(dbParams).containsKey(TEST_APP_PREFIX + "/database/port");
        assertThat(dbParams).containsKey(TEST_APP_PREFIX + "/database/name");
        assertThat(dbParams).containsKey(TEST_APP_PREFIX + "/database/username");
    }

    @Test
    @Order(10)
    void getParametersByPath_재귀조회_전체애플리케이션구성() {
        // When - Get all application parameters recursively
        CompletableFuture<Map<String, String>> future = 
            parameterStoreService.getParametersByPath(TEST_APP_PREFIX, true, false);
        
        // Then
        Map<String, String> appParams = future.join();
        assertThat(appParams).hasSizeGreaterThanOrEqualTo(7); // All non-secure parameters
        
        // Verify key parameters exist
        assertThat(appParams).containsKey(TEST_APP_PREFIX + "/database/host");
        assertThat(appParams).containsKey(TEST_APP_PREFIX + "/redis/host");
        assertThat(appParams).containsKey(TEST_APP_PREFIX + "/api/max-connections");
    }

    @Test
    @Order(11)
    void getParametersByPath_보안파라미터포함_재귀조회() {
        // When - Get all application parameters including secure ones
        CompletableFuture<Map<String, String>> future = 
            parameterStoreService.getParametersByPath(TEST_APP_PREFIX, true, true);
        
        // Then
        Map<String, String> allParams = future.join();
        assertThat(allParams).hasSizeGreaterThanOrEqualTo(9); // Including secure parameters
        
        // Verify secure parameters are included and decrypted
        assertThat(allParams).containsEntry(TEST_APP_PREFIX + "/database/password", "fake-integration-password");
        assertThat(allParams).containsEntry(TEST_APP_PREFIX + "/redis/password", "redis_secure_pass");
    }

    @Test
    @Order(12)
    void caching_파라미터캐싱_동작확인() throws Exception {
        // Given
        String parameterName = TEST_APP_PREFIX + "/database/host";
        
        // Clear cache first
        parameterStoreService.invalidateCache(parameterName);
        
        // When - First call (should fetch from AWS)
        long start1 = System.currentTimeMillis();
        String value1 = parameterStoreService.getParameter(parameterName).join();
        long duration1 = System.currentTimeMillis() - start1;
        
        // Second call (should be from cache)
        long start2 = System.currentTimeMillis();
        String value2 = parameterStoreService.getParameter(parameterName).join();
        long duration2 = System.currentTimeMillis() - start2;
        
        // Then
        assertThat(value1).isEqualTo(value2);
        assertThat(duration2).isLessThan(duration1); // Cache should be faster
        
        System.out.printf("First parameter call: %d ms, Cached call: %d ms%n", duration1, duration2);
    }

    @Test
    @Order(13)
    void updateParameter_파라미터업데이트_버전증가확인() {
        // Given
        String parameterName = TEST_APP_PREFIX + "/api/timeout-seconds";
        String newValue = "60";

        // When
        CompletableFuture<Long> future = parameterStoreService.putParameter(
            parameterName, newValue, ParameterType.STRING, true);
        
        // Then
        Long newVersion = future.join();
        assertThat(newVersion).isEqualTo(2L); // Should be version 2
        
        // Verify updated value
        String updatedValue = parameterStoreService.getParameter(parameterName).join();
        assertThat(updatedValue).isEqualTo("60");
    }

    @Test
    @Order(14)
    void addTags_파라미터태그추가_성공() {
        // Given
        String parameterName = TEST_APP_PREFIX + "/database/host";
        Map<String, String> tags = Map.of(
            "Environment", "integration-test",
            "Component", "database",
            "Owner", "integration-tests"
        );

        // When
        CompletableFuture<Void> future = parameterStoreService.addTagsToParameter(parameterName, tags);
        
        // Then
        future.join(); // Should complete without exception
        
        // Verify tags were added (using direct AWS client)
        ListTagsForResourceRequest request = ListTagsForResourceRequest.builder()
                .resourceType(ResourceTypeForTagging.PARAMETER)
                .resourceId(parameterName)
                .build();
        
        var response = ssmClient.listTagsForResource(request).join();
        Map<String, String> appliedTags = response.tagList().stream()
                .collect(java.util.stream.Collectors.toMap(
                    software.amazon.awssdk.services.ssm.model.Tag::key, 
                    software.amazon.awssdk.services.ssm.model.Tag::value));
        
        assertThat(appliedTags).containsEntry("Environment", "integration-test");
        assertThat(appliedTags).containsEntry("Component", "database");
        assertThat(appliedTags).containsEntry("Owner", "integration-tests");
    }

    @Test
    @Order(15)
    void getParameterHistory_파라미터이력조회_성공() {
        // Given
        String parameterName = TEST_APP_PREFIX + "/api/timeout-seconds";

        // When
        CompletableFuture<List<ParameterHistory>> future = 
            parameterStoreService.getParameterHistory(parameterName);
        
        // Then
        List<ParameterHistory> history = future.join();
        assertThat(history).hasSizeGreaterThanOrEqualTo(2); // Original + updated version
        
        // Verify history contains both versions
        List<String> values = history.stream()
                .map(ParameterHistory::value)
                .toList();
        assertThat(values).contains("30", "60");
    }

    @Test
    @Order(16)
    void realWorldScenario_애플리케이션구성로드_완전한워크플로우() throws Exception {
        // Given - Simulate application startup configuration loading
        String configBasePath = "/myapp/prod";
        
        // Create production configuration
        Map<String, ParameterConfig> prodConfig = Map.of(
            configBasePath + "/database/host", new ParameterConfig("prod-db.example.com", ParameterType.STRING),
            configBasePath + "/database/port", new ParameterConfig("5432", ParameterType.STRING),
            configBasePath + "/database/name", new ParameterConfig("production", ParameterType.STRING),
            configBasePath + "/database/username", new ParameterConfig("prod_user", ParameterType.STRING),
            configBasePath + "/database/password", new ParameterConfig("prod_secure_password", ParameterType.SECURE_STRING),
            configBasePath + "/cache/redis-url", new ParameterConfig("redis://prod-redis.example.com:6379", ParameterType.STRING),
            configBasePath + "/cache/redis-password", new ParameterConfig("prod_redis_pass", ParameterType.SECURE_STRING),
            configBasePath + "/monitoring/metrics-enabled", new ParameterConfig("true", ParameterType.STRING),
            configBasePath + "/monitoring/log-level", new ParameterConfig("WARN", ParameterType.STRING),
            configBasePath + "/features/new-ui-enabled", new ParameterConfig("true", ParameterType.STRING)
        );
        
        // Create all production parameters
        List<CompletableFuture<Long>> createFutures = prodConfig.entrySet().stream()
                .map(entry -> parameterStoreService.putParameter(
                    entry.getKey(), 
                    entry.getValue().value(), 
                    entry.getValue().type()))
                .toList();
        
        CompletableFuture.allOf(createFutures.toArray(new CompletableFuture[0])).join();
        
        // When - Application startup configuration load
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<Map<String, String>> configFuture = 
            parameterStoreService.getParametersByPath(configBasePath, true, true);
        
        Map<String, String> configuration = configFuture.join();
        long loadTime = System.currentTimeMillis() - startTime;
        
        // Then - Verify complete configuration
        assertThat(configuration).hasSize(prodConfig.size());
        assertThat(configuration).containsEntry(configBasePath + "/database/host", "prod-db.example.com");
        assertThat(configuration).containsEntry(configBasePath + "/database/password", "prod_secure_password");
        assertThat(configuration).containsEntry(configBasePath + "/cache/redis-password", "prod_redis_pass");
        assertThat(configuration).containsEntry(configBasePath + "/features/new-ui-enabled", "true");
        
        System.out.printf("Production config loaded in %d ms (%d parameters)%n", 
                loadTime, configuration.size());
        assertThat(loadTime).isLessThan(3000); // Should load reasonably quickly
        
        // Test cached subsequent load
        long cachedStartTime = System.currentTimeMillis();
        Map<String, String> cachedConfig = 
            parameterStoreService.getParametersByPath(configBasePath, true, true).join();
        long cachedLoadTime = System.currentTimeMillis() - cachedStartTime;
        
        assertThat(cachedConfig).isEqualTo(configuration);
        assertThat(cachedLoadTime).isLessThan(loadTime / 2); // Cache should be much faster
        
        System.out.printf("Cached config loaded in %d ms%n", cachedLoadTime);
    }

    @Test
    @Order(17)
    void errorRecovery_존재하지않는파라미터_예외발생() {
        // Given
        String nonexistentParameter = "/nonexistent/parameter";

        // When & Then
        assertThatThrownBy(() -> parameterStoreService.getParameter(nonexistentParameter).join())
                .hasCauseInstanceOf(ParameterStoreService.ParameterStoreException.class);
    }

    @Test
    @Order(18)
    void performanceTest_대량파라미터_동시조회() throws Exception {
        // Given
        String perfBasePath = "/performance-test";
        int parameterCount = 30;
        
        // Create multiple parameters
        List<CompletableFuture<Long>> createFutures = java.util.stream.IntStream.range(0, parameterCount)
                .mapToObj(i -> parameterStoreService.putParameter(
                    perfBasePath + "/param-" + i, 
                    "value-" + i, 
                    ParameterType.STRING))
                .toList();
        
        CompletableFuture.allOf(createFutures.toArray(new CompletableFuture[0])).join();
        
        // When - Concurrent parameter retrieval
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<String>> futures = java.util.stream.IntStream.range(0, parameterCount)
                .mapToObj(i -> parameterStoreService.getParameter(perfBasePath + "/param-" + i))
                .toList();
        
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        allOf.join();
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Then
        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        assertThat(results).hasSize(parameterCount);
        for (int i = 0; i < parameterCount; i++) {
            assertThat(results.get(i)).isEqualTo("value-" + i);
        }
        
        assertThat(duration).isLessThan(5000); // Should complete within 5 seconds
        
        System.out.printf("Retrieved %d parameters concurrently in %d ms (%.2f req/sec)%n", 
                parameterCount, duration, (parameterCount * 1000.0) / duration);
    }

    @Test
    @Order(19)
    void deleteParameters_배치삭제_성공() {
        // Given
        List<String> parametersToDelete = List.of(
            TEST_APP_PREFIX + "/allowed-hosts",
            TEST_APP_PREFIX + "/api/max-connections",
            TEST_ENV_PREFIX + "/deployment-region"
        );

        // When
        CompletableFuture<DeleteParametersResponse> future = 
            parameterStoreService.deleteParameters(parametersToDelete);
        
        // Then
        DeleteParametersResponse response = future.join();
        assertThat(response.deletedParameters()).hasSize(3);
        assertThat(response.invalidParameters()).isEmpty();
        
        // Verify parameters are deleted
        for (String parameterName : parametersToDelete) {
            assertThatThrownBy(() -> parameterStoreService.getParameter(parameterName).join())
                    .hasCauseInstanceOf(ParameterStoreService.ParameterStoreException.class);
        }
    }

    @Test
    @Order(98)
    void cleanup_테스트파라미터_일괄삭제() {
        // Cleanup all test parameters
        try {
            // Get all parameters under test paths
            Map<String, String> testAppParams = parameterStoreService
                .getParametersByPath(TEST_APP_PREFIX, true, false).join();
            Map<String, String> testEnvParams = parameterStoreService
                .getParametersByPath(TEST_ENV_PREFIX, true, false).join();
            Map<String, String> prodParams = parameterStoreService
                .getParametersByPath("/myapp/prod", true, false).join();
            Map<String, String> perfParams = parameterStoreService
                .getParametersByPath("/performance-test", true, false).join();
            
            List<String> allTestParameters = java.util.stream.Stream.of(
                testAppParams.keySet(),
                testEnvParams.keySet(),
                prodParams.keySet(),
                perfParams.keySet()
            ).flatMap(java.util.Collection::stream).toList();
            
            if (!allTestParameters.isEmpty()) {
                // Delete in batches (AWS limit is 10 parameters per batch)
                int batchSize = 10;
                for (int i = 0; i < allTestParameters.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, allTestParameters.size());
                    List<String> batch = allTestParameters.subList(i, endIndex);
                    
                    try {
                        parameterStoreService.deleteParameters(batch).join();
                        System.out.printf("Deleted parameter batch: %s%n", batch);
                    } catch (Exception e) {
                        System.out.printf("Failed to delete parameter batch %s: %s%n", batch, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            // Ignore cleanup errors
            System.out.printf("Cleanup error: %s%n", e.getMessage());
        }
    }

    // Helper record for parameter configuration
    private record ParameterConfig(String value, ParameterType type) {}
}