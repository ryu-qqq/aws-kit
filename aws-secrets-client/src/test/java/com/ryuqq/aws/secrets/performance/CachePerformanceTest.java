package com.ryuqq.aws.secrets.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import com.ryuqq.aws.secrets.service.SecretsService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Comprehensive cache performance tests for Secrets Manager operations
 * Tests cache hit/miss ratios, concurrent access, memory usage, and cache eviction
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CachePerformanceTest {

    @Mock
    private SecretsManagerAsyncClient secretsManagerClient;
    
    @Mock
    private SecretsCacheManager cacheManager;
    
    private SecretsService secretsService;
    private ObjectMapper objectMapper;
    private ExecutorService executorService;
    private MemoryMXBean memoryBean;
    
    // Performance test constants
    private static final int CONCURRENT_THREADS = 50;
    private static final int OPERATIONS_PER_THREAD = 100;
    private static final int CACHE_SIZE = 1000;
    private static final int UNIQUE_SECRETS = 200; // 20% of cache size for cache hit testing
    
    // Performance thresholds
    private static final long MAX_CACHE_HIT_LATENCY_MS = 10;
    private static final long MAX_CACHE_MISS_LATENCY_MS = 500;
    private static final double MIN_CACHE_HIT_RATIO = 0.75; // 75% cache hit ratio expected
    private static final double MIN_THROUGHPUT_OPS_PER_SEC = 2000;
    private static final long MAX_MEMORY_INCREASE_MB = 200;
    
    // Cache performance metrics
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalCacheOps = new AtomicLong(0);
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        secretsService = new SecretsService(secretsManagerClient, cacheManager, objectMapper);
        executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        memoryBean = ManagementFactory.getMemoryMXBean();
        
        setupMockResponses();
        resetCounters();
    }
    
    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
    }

    /**
     * Test cache performance under high concurrent load
     */
    @Test
    @Order(1)
    @DisplayName("Cache Performance Under Concurrent Load")
    void testCachePerformanceUnderLoad() throws InterruptedException, ExecutionException {
        MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
        
        List<Long> cacheHitLatencies = new CopyOnWriteArrayList<>();
        List<Long> cacheMissLatencies = new CopyOnWriteArrayList<>();
        AtomicLong successfulOperations = new AtomicLong(0);
        AtomicLong failedOperations = new AtomicLong(0);
        
        Instant startTime = Instant.now();
        
        // Create concurrent secret retrieval tasks
        List<CompletableFuture<Void>> tasks = IntStream.range(0, CONCURRENT_THREADS)
            .mapToObj(threadId -> CompletableFuture.runAsync(() -> {
                Random random = new Random(threadId);
                
                for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                    // Use Zipf distribution to simulate realistic cache access patterns
                    String secretName = generateSecretName(generateZipfIndex(random, UNIQUE_SECRETS));
                    
                    long operationStart = System.nanoTime();
                    
                    try {
                        CompletableFuture<String> secretFuture = secretsService.getSecret(secretName);
                        String result = secretFuture.get(10, TimeUnit.SECONDS);
                        
                        long operationEnd = System.nanoTime();
                        long latencyMs = (operationEnd - operationStart) / 1_000_000;
                        
                        // Track cache hit/miss latencies separately
                        if (isCacheHit(secretName)) {
                            cacheHitLatencies.add(latencyMs);
                        } else {
                            cacheMissLatencies.add(latencyMs);
                        }
                        
                        successfulOperations.incrementAndGet();
                        assertThat(result).isNotNull();
                        
                    } catch (Exception e) {
                        failedOperations.incrementAndGet();
                        log.error("Cache operation failed for secret {}: {}", secretName, e.getMessage());
                    }
                }
            }, executorService))
            .toList();
        
        // Wait for all tasks to complete
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
            tasks.toArray(new CompletableFuture[0])
        );
        
        assertThatCode(() -> allTasks.get(120, TimeUnit.SECONDS))
            .doesNotThrowAnyException();
        
        Instant endTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, endTime);
        
        // Memory after operations
        System.gc();
        Thread.sleep(1000);
        MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
        
        // Calculate cache performance metrics
        CachePerformanceMetrics metrics = calculateCacheMetrics(
            cacheHitLatencies, cacheMissLatencies, totalDuration,
            beforeMemory, afterMemory, successfulOperations.get(), failedOperations.get()
        );
        
        // Log performance results
        logCachePerformanceResults(metrics);
        
        // Performance assertions
        assertCachePerformanceThresholds(metrics);
    }

    /**
     * Test cache hit/miss ratios with different access patterns
     */
    @Test
    @Order(2)
    @DisplayName("Cache Hit/Miss Ratio Performance Test")
    void testCacheHitMissRatios() throws InterruptedException, ExecutionException {
        // Test different cache access patterns
        Map<String, CachePerformanceMetrics> patternResults = new HashMap<>();
        
        // Pattern 1: High locality (80% of operations on 20% of keys)
        patternResults.put("High Locality", testCachePattern(0.8, 0.2));
        
        // Pattern 2: Medium locality (60% of operations on 40% of keys)  
        patternResults.put("Medium Locality", testCachePattern(0.6, 0.4));
        
        // Pattern 3: Low locality (uniform distribution)
        patternResults.put("Low Locality", testCachePattern(1.0, 1.0));
        
        // Pattern 4: Hot keys (50% of operations on 5% of keys)
        patternResults.put("Hot Keys", testCachePattern(0.5, 0.05));
        
        // Analyze cache performance across patterns
        log.info("=== Cache Pattern Analysis ===");
        patternResults.forEach((pattern, metrics) -> {
            log.info("Pattern: {} - Hit Ratio: {:.2%}, Avg Hit Latency: {} ms, Avg Miss Latency: {} ms",
                    pattern, metrics.getCacheHitRatio(), 
                    metrics.getAverageCacheHitLatencyMs(), metrics.getAverageCacheMissLatencyMs());
        });
        
        // Verify that high locality patterns have better cache performance
        CachePerformanceMetrics highLocality = patternResults.get("High Locality");
        CachePerformanceMetrics lowLocality = patternResults.get("Low Locality");
        
        assertThat(highLocality.getCacheHitRatio())
            .isGreaterThan(lowLocality.getCacheHitRatio());
        
        assertThat(highLocality.getOverallThroughputOpsPerSec())
            .isGreaterThan(lowLocality.getOverallThroughputOpsPerSec());
    }

    /**
     * Test cache eviction performance and memory management
     */
    @Test
    @Order(3)
    @DisplayName("Cache Eviction and Memory Management Test")
    void testCacheEvictionPerformance() throws InterruptedException, ExecutionException {
        List<MemoryUsage> memorySnapshots = new ArrayList<>();
        List<CacheEvictionMetrics> evictionMetrics = new ArrayList<>();
        
        // Take initial memory snapshot
        System.gc();
        Thread.sleep(1000);
        memorySnapshots.add(memoryBean.getHeapMemoryUsage());
        
        // Phase 1: Fill cache to capacity
        log.info("Phase 1: Filling cache to capacity");
        fillCacheToCapacity();
        
        System.gc();
        Thread.sleep(1000);
        memorySnapshots.add(memoryBean.getHeapMemoryUsage());
        
        // Phase 2: Trigger cache eviction with new entries
        for (int phase = 0; phase < 5; phase++) {
            log.info("Phase {}: Triggering cache eviction", phase + 2);
            
            Instant phaseStart = Instant.now();
            AtomicInteger evictions = new AtomicInteger(0);
            AtomicLong evictionLatency = new AtomicLong(0);
            
            // Add entries that will trigger eviction
            List<CompletableFuture<Void>> evictionTasks = IntStream.range(0, CACHE_SIZE / 2)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    String newSecretName = "eviction-test-secret-" + (phase * 1000 + i);
                    
                    long evictionStart = System.nanoTime();
                    
                    try {
                        // This should trigger cache eviction
                        secretsService.getSecret(newSecretName).get(10, TimeUnit.SECONDS);
                        
                        long evictionEnd = System.nanoTime();
                        long latencyMs = (evictionEnd - evictionStart) / 1_000_000;
                        
                        evictionLatency.addAndGet(latencyMs);
                        evictions.incrementAndGet();
                        
                    } catch (Exception e) {
                        log.error("Eviction test operation failed: {}", e.getMessage());
                    }
                }, executorService))
                .toList();
            
            CompletableFuture.allOf(evictionTasks.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
            
            Instant phaseEnd = Instant.now();
            Duration phaseDuration = Duration.between(phaseStart, phaseEnd);
            
            // Record eviction metrics
            double avgEvictionLatency = evictions.get() > 0 ? 
                (double) evictionLatency.get() / evictions.get() : 0;
            
            evictionMetrics.add(new CacheEvictionMetrics(
                evictions.get(), avgEvictionLatency, phaseDuration.toMillis()
            ));
            
            // Take memory snapshot
            System.gc();
            Thread.sleep(1000);
            memorySnapshots.add(memoryBean.getHeapMemoryUsage());
        }
        
        // Analyze eviction performance
        analyzeEvictionPerformance(evictionMetrics, memorySnapshots);
    }

    /**
     * Test cache performance during cache invalidation scenarios
     */
    @Test
    @Order(4)
    @DisplayName("Cache Invalidation Performance Test")
    void testCacheInvalidationPerformance() throws InterruptedException, ExecutionException {
        // Pre-populate cache with secrets
        Set<String> cachedSecrets = prepopulateCache(UNIQUE_SECRETS);
        
        AtomicLong invalidationLatencies = new AtomicLong(0);
        AtomicLong reloadLatencies = new AtomicLong(0);
        AtomicInteger invalidationCount = new AtomicInteger(0);
        AtomicInteger reloadCount = new AtomicInteger(0);
        
        Instant testStart = Instant.now();
        
        // Simulate cache invalidation scenarios
        List<CompletableFuture<Void>> invalidationTasks = cachedSecrets.parallelStream()
            .map(secretName -> CompletableFuture.runAsync(() -> {
                try {
                    // Phase 1: Invalidate cache entry
                    long invalidationStart = System.nanoTime();
                    secretsService.invalidateCache(secretName);
                    long invalidationEnd = System.nanoTime();
                    
                    long invalidationLatency = (invalidationEnd - invalidationStart) / 1_000_000;
                    invalidationLatencies.addAndGet(invalidationLatency);
                    invalidationCount.incrementAndGet();
                    
                    // Phase 2: Reload from source (cache miss)
                    long reloadStart = System.nanoTime();
                    String reloadedSecret = secretsService.getSecret(secretName).get(10, TimeUnit.SECONDS);
                    long reloadEnd = System.nanoTime();
                    
                    long reloadLatency = (reloadEnd - reloadStart) / 1_000_000;
                    reloadLatencies.addAndGet(reloadLatency);
                    reloadCount.incrementAndGet();
                    
                    assertThat(reloadedSecret).isNotNull();
                    
                } catch (Exception e) {
                    log.error("Cache invalidation test failed for secret {}: {}", secretName, e.getMessage());
                }
            }, executorService))
            .toList();
        
        CompletableFuture.allOf(invalidationTasks.toArray(new CompletableFuture[0]))
            .get(120, TimeUnit.SECONDS);
        
        Instant testEnd = Instant.now();
        Duration testDuration = Duration.between(testStart, testEnd);
        
        // Calculate invalidation performance metrics
        double avgInvalidationLatency = invalidationCount.get() > 0 ? 
            (double) invalidationLatencies.get() / invalidationCount.get() : 0;
        double avgReloadLatency = reloadCount.get() > 0 ? 
            (double) reloadLatencies.get() / reloadCount.get() : 0;
        
        double invalidationThroughput = (double) invalidationCount.get() / testDuration.toMillis() * 1000;
        
        log.info("=== Cache Invalidation Performance Results ===");
        log.info("Invalidation operations: {}", invalidationCount.get());
        log.info("Reload operations: {}", reloadCount.get());
        log.info("Average invalidation latency: {:.2f} ms", avgInvalidationLatency);
        log.info("Average reload latency: {:.2f} ms", avgReloadLatency);
        log.info("Invalidation throughput: {:.2f} ops/sec", invalidationThroughput);
        log.info("Total test duration: {} ms", testDuration.toMillis());
        
        // Performance assertions
        assertThat(avgInvalidationLatency).isLessThan(50); // Invalidation should be fast
        assertThat(avgReloadLatency).isLessThan(MAX_CACHE_MISS_LATENCY_MS);
        assertThat(invalidationThroughput).isGreaterThan(100); // Reasonable invalidation throughput
    }

    /**
     * Test cache performance under memory pressure
     */
    @Test
    @Order(5)
    @DisplayName("Cache Performance Under Memory Pressure Test")
    void testCachePerformanceUnderMemoryPressure() throws InterruptedException, ExecutionException {
        // Create memory pressure by allocating large objects
        List<byte[]> memoryPressure = new ArrayList<>();
        
        try {
            // Allocate memory to create pressure
            for (int i = 0; i < 100; i++) {
                memoryPressure.add(new byte[1024 * 1024]); // 1MB each
            }
            
            MemoryUsage pressureMemory = memoryBean.getHeapMemoryUsage();
            double memoryUsagePercent = (double) pressureMemory.getUsed() / pressureMemory.getMax();
            
            log.info("Created memory pressure: {:.1%} of heap used", memoryUsagePercent);
            
            // Test cache performance under memory pressure
            List<Long> pressureLatencies = new CopyOnWriteArrayList<>();
            AtomicLong pressureOperations = new AtomicLong(0);
            
            Instant pressureStart = Instant.now();
            
            List<CompletableFuture<Void>> pressureTasks = IntStream.range(0, CONCURRENT_THREADS / 2)
                .mapToObj(threadId -> CompletableFuture.runAsync(() -> {
                    Random random = new Random(threadId);
                    
                    for (int i = 0; i < OPERATIONS_PER_THREAD / 2; i++) {
                        String secretName = generateSecretName(random.nextInt(UNIQUE_SECRETS / 2));
                        
                        long operationStart = System.nanoTime();
                        
                        try {
                            String result = secretsService.getSecret(secretName).get(15, TimeUnit.SECONDS);
                            
                            long operationEnd = System.nanoTime();
                            long latencyMs = (operationEnd - operationStart) / 1_000_000;
                            
                            pressureLatencies.add(latencyMs);
                            pressureOperations.incrementAndGet();
                            
                            assertThat(result).isNotNull();
                            
                        } catch (Exception e) {
                            log.debug("Operation failed under memory pressure: {}", e.getMessage());
                        }
                    }
                }, executorService))
                .toList();
            
            CompletableFuture.allOf(pressureTasks.toArray(new CompletableFuture[0]))
                .get(180, TimeUnit.SECONDS);
            
            Instant pressureEnd = Instant.now();
            Duration pressureDuration = Duration.between(pressureStart, pressureEnd);
            
            // Analyze performance under memory pressure
            double avgPressureLatency = pressureLatencies.stream()
                .mapToLong(Long::longValue).average().orElse(0);
            double pressureThroughput = (double) pressureOperations.get() / pressureDuration.toMillis() * 1000;
            
            log.info("=== Performance Under Memory Pressure ===");
            log.info("Operations completed: {}", pressureOperations.get());
            log.info("Average latency: {:.2f} ms", avgPressureLatency);
            log.info("Throughput: {:.2f} ops/sec", pressureThroughput);
            log.info("Memory usage: {:.1%}", memoryUsagePercent);
            
            // Even under memory pressure, cache should maintain reasonable performance
            assertThat(avgPressureLatency).isLessThan(MAX_CACHE_MISS_LATENCY_MS * 2); // Allow 2x latency under pressure
            assertThat(pressureThroughput).isGreaterThan(MIN_THROUGHPUT_OPS_PER_SEC / 4); // Allow 4x throughput reduction
            
        } finally {
            // Clean up memory pressure
            memoryPressure.clear();
            System.gc();
            Thread.sleep(2000);
        }
    }

    // Helper methods
    
    private void setupMockResponses() {
        // Setup cache manager mock to track hits/misses
        when(cacheManager.get(any(String.class), any()))
            .thenAnswer(invocation -> {
                String key = invocation.getArgument(0);
                Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                
                totalCacheOps.incrementAndGet();
                
                // Simulate cache behavior: 70% hit rate for repeated keys
                if (shouldSimulateCacheHit(key)) {
                    cacheHits.incrementAndGet();
                    // Cache hit - return quickly
                    return CompletableFuture.completedFuture("cached-value-" + key);
                } else {
                    cacheMisses.incrementAndGet();
                    // Cache miss - use loader (slower)
                    return loader.apply(key);
                }
            });
        
        // Mock secrets manager responses
        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
            .thenAnswer(invocation -> {
                GetSecretValueRequest request = invocation.getArgument(0);
                
                // Simulate network delay for cache misses
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(50 + new Random().nextInt(100)); // 50-150ms delay
                        
                        return GetSecretValueResponse.builder()
                            .secretString("secret-value-" + request.secretId())
                            .versionId(UUID.randomUUID().toString())
                            .build();
                            
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            });
        
        doNothing().when(cacheManager).invalidate(any(String.class));
        doNothing().when(cacheManager).invalidateAll();
    }
    
    private void resetCounters() {
        cacheHits.set(0);
        cacheMisses.set(0);
        totalCacheOps.set(0);
    }
    
    private String generateSecretName(int index) {
        return "performance-test-secret-" + index;
    }
    
    private int generateZipfIndex(Random random, int maxIndex) {
        // Simple Zipf distribution approximation
        double alpha = 1.0; // Zipf parameter
        double c = 0.0;
        
        for (int i = 1; i <= maxIndex; i++) {
            c += 1.0 / Math.pow(i, alpha);
        }
        c = 1.0 / c;
        
        double z = random.nextDouble();
        double sum = 0.0;
        
        for (int i = 1; i <= maxIndex; i++) {
            sum += c / Math.pow(i, alpha);
            if (sum >= z) {
                return i - 1; // Convert to 0-based index
            }
        }
        
        return maxIndex - 1;
    }
    
    private boolean shouldSimulateCacheHit(String key) {
        // Use key hash to determine consistent cache hit/miss behavior
        int hash = key.hashCode();
        return (hash % 10) < 7; // 70% cache hit rate
    }
    
    private boolean isCacheHit(String secretName) {
        return shouldSimulateCacheHit(secretName);
    }
    
    private CachePerformanceMetrics testCachePattern(double operationRatio, double keyRatio) 
            throws InterruptedException, ExecutionException {
        
        resetCounters();
        
        int targetKeys = (int) (UNIQUE_SECRETS * keyRatio);
        int targetOperations = (int) (OPERATIONS_PER_THREAD * operationRatio);
        
        List<Long> hitLatencies = new CopyOnWriteArrayList<>();
        List<Long> missLatencies = new CopyOnWriteArrayList<>();
        AtomicLong successfulOps = new AtomicLong(0);
        AtomicLong failedOps = new AtomicLong(0);
        
        Instant patternStart = Instant.now();
        
        List<CompletableFuture<Void>> patternTasks = IntStream.range(0, CONCURRENT_THREADS / 4)
            .mapToObj(threadId -> CompletableFuture.runAsync(() -> {
                Random random = new Random(threadId);
                
                for (int i = 0; i < targetOperations; i++) {
                    String secretName = generateSecretName(random.nextInt(targetKeys));
                    
                    long operationStart = System.nanoTime();
                    
                    try {
                        String result = secretsService.getSecret(secretName).get(10, TimeUnit.SECONDS);
                        
                        long operationEnd = System.nanoTime();
                        long latencyMs = (operationEnd - operationStart) / 1_000_000;
                        
                        if (isCacheHit(secretName)) {
                            hitLatencies.add(latencyMs);
                        } else {
                            missLatencies.add(latencyMs);
                        }
                        
                        successfulOps.incrementAndGet();
                        assertThat(result).isNotNull();
                        
                    } catch (Exception e) {
                        failedOps.incrementAndGet();
                    }
                }
            }, executorService))
            .toList();
        
        CompletableFuture.allOf(patternTasks.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);
        
        Instant patternEnd = Instant.now();
        Duration patternDuration = Duration.between(patternStart, patternEnd);
        
        return new CachePerformanceMetrics(
            hitLatencies, missLatencies, patternDuration,
            successfulOps.get(), failedOps.get(),
            cacheHits.get(), cacheMisses.get()
        );
    }
    
    private void fillCacheToCapacity() throws InterruptedException, ExecutionException {
        List<CompletableFuture<Void>> fillTasks = IntStream.range(0, CACHE_SIZE)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                try {
                    String secretName = "cache-fill-secret-" + i;
                    secretsService.getSecret(secretName).get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.debug("Cache fill operation failed: {}", e.getMessage());
                }
            }, executorService))
            .toList();
        
        CompletableFuture.allOf(fillTasks.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);
    }
    
    private Set<String> prepopulateCache(int count) throws InterruptedException, ExecutionException {
        Set<String> secretNames = new HashSet<>();
        
        List<CompletableFuture<String>> prepopulateTasks = IntStream.range(0, count)
            .mapToObj(i -> {
                String secretName = generateSecretName(i);
                secretNames.add(secretName);
                
                return secretsService.getSecret(secretName);
            })
            .toList();
        
        // Wait for all secrets to be cached
        CompletableFuture.allOf(prepopulateTasks.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);
        
        return secretNames;
    }
    
    private CachePerformanceMetrics calculateCacheMetrics(
            List<Long> hitLatencies, List<Long> missLatencies, Duration totalDuration,
            MemoryUsage beforeMemory, MemoryUsage afterMemory,
            long successfulOps, long failedOps) {
        
        return new CachePerformanceMetrics(
            hitLatencies, missLatencies, totalDuration,
            successfulOps, failedOps, cacheHits.get(), cacheMisses.get()
        );
    }
    
    private void logCachePerformanceResults(CachePerformanceMetrics metrics) {
        log.info("=== Cache Performance Test Results ===");
        log.info("Total operations: {}", metrics.getTotalOperations());
        log.info("Successful operations: {}", metrics.getSuccessfulOperations());
        log.info("Failed operations: {}", metrics.getFailedOperations());
        log.info("Cache hits: {}", metrics.getCacheHits());
        log.info("Cache misses: {}", metrics.getCacheMisses());
        log.info("Cache hit ratio: {:.2%}", metrics.getCacheHitRatio());
        log.info("Average cache hit latency: {:.2f} ms", metrics.getAverageCacheHitLatencyMs());
        log.info("Average cache miss latency: {:.2f} ms", metrics.getAverageCacheMissLatencyMs());
        log.info("Overall throughput: {:.2f} ops/sec", metrics.getOverallThroughputOpsPerSec());
        log.info("=======================================");
    }
    
    private void assertCachePerformanceThresholds(CachePerformanceMetrics metrics) {
        // Cache hit ratio should be reasonable
        assertThat(metrics.getCacheHitRatio())
            .as("Cache hit ratio should be at least %.2f", MIN_CACHE_HIT_RATIO)
            .isGreaterThanOrEqualTo(MIN_CACHE_HIT_RATIO);
        
        // Cache hit latency should be very low
        assertThat(metrics.getAverageCacheHitLatencyMs())
            .as("Cache hit latency should be under %d ms", MAX_CACHE_HIT_LATENCY_MS)
            .isLessThan(MAX_CACHE_HIT_LATENCY_MS);
        
        // Cache miss latency should be reasonable
        assertThat(metrics.getAverageCacheMissLatencyMs())
            .as("Cache miss latency should be under %d ms", MAX_CACHE_MISS_LATENCY_MS)
            .isLessThan(MAX_CACHE_MISS_LATENCY_MS);
        
        // Overall throughput should meet minimum requirements
        assertThat(metrics.getOverallThroughputOpsPerSec())
            .as("Overall throughput should be at least %.2f ops/sec", MIN_THROUGHPUT_OPS_PER_SEC)
            .isGreaterThan(MIN_THROUGHPUT_OPS_PER_SEC);
        
        // Success rate should be high
        double successRate = (double) metrics.getSuccessfulOperations() / metrics.getTotalOperations();
        assertThat(successRate)
            .as("Success rate should be at least 95%")
            .isGreaterThan(0.95);
    }
    
    private void analyzeEvictionPerformance(List<CacheEvictionMetrics> evictionMetrics,
                                          List<MemoryUsage> memorySnapshots) {
        log.info("=== Cache Eviction Performance Analysis ===");
        
        for (int i = 0; i < evictionMetrics.size(); i++) {
            CacheEvictionMetrics metrics = evictionMetrics.get(i);
            log.info("Phase {}: {} evictions, {:.2f} ms avg latency, {} ms total duration",
                    i + 1, metrics.getEvictionCount(), metrics.getAverageEvictionLatency(),
                    metrics.getTotalDuration());
        }
        
        // Analyze memory usage pattern
        log.info("Memory usage progression:");
        for (int i = 0; i < memorySnapshots.size(); i++) {
            MemoryUsage usage = memorySnapshots.get(i);
            log.info("Snapshot {}: {} MB used, {} MB max",
                    i, usage.getUsed() / (1024 * 1024), usage.getMax() / (1024 * 1024));
        }
        
        // Verify eviction performance is consistent
        double maxEvictionLatency = evictionMetrics.stream()
            .mapToDouble(CacheEvictionMetrics::getAverageEvictionLatency)
            .max().orElse(0);
        
        assertThat(maxEvictionLatency)
            .as("Maximum eviction latency should be reasonable")
            .isLessThan(1000); // 1 second max
            
        // Memory should not grow indefinitely
        long initialMemory = memorySnapshots.get(0).getUsed();
        long finalMemory = memorySnapshots.get(memorySnapshots.size() - 1).getUsed();
        long memoryGrowthMB = (finalMemory - initialMemory) / (1024 * 1024);
        
        assertThat(memoryGrowthMB)
            .as("Memory growth should be controlled during eviction")
            .isLessThan(MAX_MEMORY_INCREASE_MB);
    }
    
    // Performance metrics classes
    
    private static class CachePerformanceMetrics {
        private final double averageCacheHitLatencyMs;
        private final double averageCacheMissLatencyMs;
        private final double cacheHitRatio;
        private final double overallThroughputOpsPerSec;
        private final long successfulOperations;
        private final long failedOperations;
        private final long cacheHits;
        private final long cacheMisses;
        
        public CachePerformanceMetrics(List<Long> hitLatencies, List<Long> missLatencies,
                                     Duration totalDuration, long successfulOps, long failedOps,
                                     long cacheHits, long cacheMisses) {
            this.averageCacheHitLatencyMs = hitLatencies.stream()
                .mapToLong(Long::longValue).average().orElse(0);
            this.averageCacheMissLatencyMs = missLatencies.stream()
                .mapToLong(Long::longValue).average().orElse(0);
            this.cacheHitRatio = (double) cacheHits / (cacheHits + cacheMisses);
            this.overallThroughputOpsPerSec = (double) successfulOps / totalDuration.toMillis() * 1000;
            this.successfulOperations = successfulOps;
            this.failedOperations = failedOps;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
        }
        
        // Getters
        public double getAverageCacheHitLatencyMs() { return averageCacheHitLatencyMs; }
        public double getAverageCacheMissLatencyMs() { return averageCacheMissLatencyMs; }
        public double getCacheHitRatio() { return cacheHitRatio; }
        public double getOverallThroughputOpsPerSec() { return overallThroughputOpsPerSec; }
        public long getSuccessfulOperations() { return successfulOperations; }
        public long getFailedOperations() { return failedOperations; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getTotalOperations() { return successfulOperations + failedOperations; }
    }
    
    private static class CacheEvictionMetrics {
        private final int evictionCount;
        private final double averageEvictionLatency;
        private final long totalDuration;
        
        public CacheEvictionMetrics(int evictionCount, double averageEvictionLatency, long totalDuration) {
            this.evictionCount = evictionCount;
            this.averageEvictionLatency = averageEvictionLatency;
            this.totalDuration = totalDuration;
        }
        
        // Getters
        public int getEvictionCount() { return evictionCount; }
        public double getAverageEvictionLatency() { return averageEvictionLatency; }
        public long getTotalDuration() { return totalDuration; }
    }
}