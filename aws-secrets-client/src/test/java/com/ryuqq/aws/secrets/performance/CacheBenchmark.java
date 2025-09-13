package com.ryuqq.aws.secrets.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import com.ryuqq.aws.secrets.service.SecretsService;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JMH Benchmark for Secrets Cache Performance
 * Measures cache hit/miss performance, memory allocation, and concurrent access patterns
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx4g"})
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
public class CacheBenchmark {

    private SecretsService secretsService;
    private SecretsManagerAsyncClient secretsManagerClient;
    private SecretsCacheManager cacheManager;
    private ObjectMapper objectMapper;
    
    private static final int CACHE_SIZE = 1000;
    private static final int HOT_KEYS = 100; // 10% of cache size
    private Map<String, String> testSecrets;
    private String[] hotKeys;
    private String[] coldKeys;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        // Setup mocks
        secretsManagerClient = mock(SecretsManagerAsyncClient.class);
        cacheManager = mock(SecretsCacheManager.class);
        objectMapper = new ObjectMapper();
        secretsService = new SecretsService(secretsManagerClient, cacheManager, objectMapper);
        
        setupMockResponses();
        prepareTestData();
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() {
        System.gc(); // Hint for garbage collection before each iteration
    }
    
    /**
     * Benchmark cache hit performance (frequently accessed secrets)
     */
    @Benchmark
    @Group("cacheHit")
    public void benchmarkCacheHit(Blackhole bh) throws Exception {
        String secretName = getRandomHotKey();
        CompletableFuture<String> future = secretsService.getSecret(secretName);
        String result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark cache miss performance (infrequently accessed secrets)
     */
    @Benchmark
    @Group("cacheMiss")
    public void benchmarkCacheMiss(Blackhole bh) throws Exception {
        String secretName = getRandomColdKey();
        CompletableFuture<String> future = secretsService.getSecret(secretName);
        String result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark mixed cache hit/miss pattern (realistic workload)
     */
    @Benchmark
    @Group("mixedAccess")
    public void benchmarkMixedAccess(Blackhole bh) throws Exception {
        // 80% hot keys, 20% cold keys (Pareto distribution)
        String secretName = ThreadLocalRandom.current().nextDouble() < 0.8 ? 
            getRandomHotKey() : getRandomColdKey();
        
        CompletableFuture<String> future = secretsService.getSecret(secretName);
        String result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark concurrent cache access
     */
    @Benchmark
    @Group("concurrentAccess")
    @GroupThreads(8)
    public void benchmarkConcurrentAccess(Blackhole bh) throws Exception {
        String secretName = getRandomHotKey(); // Focus on contention
        CompletableFuture<String> future = secretsService.getSecret(secretName);
        String result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark JSON parsing performance
     */
    @Benchmark
    @Group("jsonParsing")
    public void benchmarkJsonParsing(Blackhole bh) throws Exception {
        String secretName = getRandomHotKey();
        CompletableFuture<Map<String, Object>> future = secretsService.getSecretAsMap(secretName);
        Map<String, Object> result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark typed secret retrieval
     */
    @Benchmark
    @Group("typedRetrieval")
    public void benchmarkTypedRetrieval(Blackhole bh) throws Exception {
        String secretName = getRandomHotKey();
        CompletableFuture<TestSecretType> future = secretsService.getSecret(secretName, TestSecretType.class);
        TestSecretType result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark cache invalidation performance
     */
    @Benchmark
    @Group("cacheInvalidation")
    public void benchmarkCacheInvalidation(Blackhole bh) {
        String secretName = getRandomHotKey();
        secretsService.invalidateCache(secretName);
        bh.consume(secretName);
    }
    
    /**
     * Benchmark secret creation (write path)
     */
    @Benchmark
    @Group("secretCreation")
    public void benchmarkSecretCreation(Blackhole bh) throws Exception {
        String secretName = "benchmark-secret-" + System.nanoTime();
        String secretValue = "benchmark-value-" + UUID.randomUUID();
        
        CompletableFuture<String> future = secretsService.createSecret(secretName, secretValue);
        String result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark secret update (write path)
     */
    @Benchmark
    @Group("secretUpdate")
    public void benchmarkSecretUpdate(Blackhole bh) throws Exception {
        String secretName = getRandomHotKey();
        String secretValue = "updated-value-" + System.nanoTime();
        
        CompletableFuture<String> future = secretsService.updateSecret(secretName, secretValue);
        String result = future.get();
        bh.consume(result);
    }
    
    // Setup and helper methods
    
    private void setupMockResponses() {
        // Mock cache manager with realistic hit/miss behavior
        when(cacheManager.get(any(String.class), any()))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                
                // Simulate cache hit for hot keys (90% hit rate)
                // Simulate cache miss for cold keys (10% hit rate)
                boolean isHotKey = isHotKey(key);
                boolean cacheHit = isHotKey ? 
                    ThreadLocalRandom.current().nextDouble() < 0.9 :
                    ThreadLocalRandom.current().nextDouble() < 0.1;
                
                if (cacheHit) {
                    // Cache hit - return immediately with cached value
                    return CompletableFuture.completedFuture(testSecrets.get(key));
                } else {
                    // Cache miss - use loader with simulated network delay
                    return loader.apply(key);
                }
            });
        
        // Mock secrets manager responses
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenAnswer(invocation -> {
                GetSecretValueRequest request = invocation.getArgument(0);
                String secretId = request.secretId();
                
                // Simulate variable network latency (20-200ms)
                int delay = ThreadLocalRandom.current().nextInt(20, 200);
                
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(delay);
                        
                        String secretValue = testSecrets.getOrDefault(secretId, 
                            "default-secret-value-" + secretId);
                        
                        return GetSecretValueResponse.builder()
                            .secretString(secretValue)
                            .versionId(UUID.randomUUID().toString())
                            .build();
                            
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            });
        
        // Mock create secret
        when(secretsManagerClient.createSecret(any(CreateSecretRequest.class)))
            .thenAnswer(invocation -> {
                CreateSecretRequest request = invocation.getArgument(0);
                
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(100, 300));
                        
                        return CreateSecretResponse.builder()
                            .arn("arn:aws:secretsmanager:us-east-1:123456789012:secret:" + request.name())
                            .versionId(UUID.randomUUID().toString())
                            .build();
                            
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            });
        
        // Mock update secret
        when(secretsManagerClient.updateSecret(any(UpdateSecretRequest.class)))
            .thenAnswer(invocation -> {
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(ThreadLocalRandom.current().nextInt(50, 150));
                        
                        return UpdateSecretResponse.builder()
                            .versionId(UUID.randomUUID().toString())
                            .build();
                            
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            });
    }
    
    private void prepareTestData() {
        testSecrets = new HashMap<>();
        hotKeys = new String[HOT_KEYS];
        coldKeys = new String[CACHE_SIZE - HOT_KEYS];
        
        // Prepare hot keys (frequently accessed)
        for (int i = 0; i < HOT_KEYS; i++) {
            String secretName = "hot-secret-" + i;
            String secretValue = createTestSecretValue("hot", i);
            
            testSecrets.put(secretName, secretValue);
            hotKeys[i] = secretName;
        }
        
        // Prepare cold keys (infrequently accessed)
        for (int i = 0; i < CACHE_SIZE - HOT_KEYS; i++) {
            String secretName = "cold-secret-" + i;
            String secretValue = createTestSecretValue("cold", i);
            
            testSecrets.put(secretName, secretValue);
            coldKeys[i] = secretName;
        }
    }
    
    private String createTestSecretValue(String type, int index) {
        // Create realistic JSON secret values
        Map<String, Object> secretMap = new HashMap<>();
        secretMap.put("type", type);
        secretMap.put("index", index);
        secretMap.put("username", "user_" + type + "_" + index);
        secretMap.put("password", "password_" + UUID.randomUUID());
        secretMap.put("api_key", "key_" + UUID.randomUUID());
        secretMap.put("created_at", System.currentTimeMillis());
        secretMap.put("metadata", Map.of(
            "environment", "benchmark",
            "service", "cache-test",
            "region", "us-east-1"
        ));
        
        try {
            return objectMapper.writeValueAsString(secretMap);
        } catch (Exception e) {
            return "{\"error\":\"Failed to serialize test data\"}";
        }
    }
    
    private String getRandomHotKey() {
        int index = ThreadLocalRandom.current().nextInt(hotKeys.length);
        return hotKeys[index];
    }
    
    private String getRandomColdKey() {
        int index = ThreadLocalRandom.current().nextInt(coldKeys.length);
        return coldKeys[index];
    }
    
    private boolean isHotKey(String key) {
        for (String hotKey : hotKeys) {
            if (hotKey.equals(key)) {
                return true;
            }
        }
        return false;
    }
    
    // Test data classes
    
    public static class TestSecretType {
        public String type;
        public int index;
        public String username;
        public String password;
        public String api_key;
        public long created_at;
        public Map<String, String> metadata;
        
        // Default constructor for Jackson
        public TestSecretType() {}
    }
    
    /**
     * Main method to run cache benchmarks
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(CacheBenchmark.class.getSimpleName())
            .jvmArgs("-Xms2g", "-Xmx4g")
            .shouldDoGC(true)
            .shouldFailOnError(true)
            .result("cache-benchmark-results.json")
            .resultFormat(org.openjdk.jmh.results.format.ResultFormatType.JSON)
            .build();
        
        new Runner(opt).run();
    }
}