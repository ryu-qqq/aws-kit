package com.ryuqq.aws.secrets.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@DisplayName("SecretsCacheManager Tests")
class SecretsCacheManagerTest {

    private SecretsCacheManager enabledCacheManager;
    private SecretsCacheManager disabledCacheManager;

    private static final String CACHE_KEY = "test-key";
    private static final String CACHE_VALUE = "test-value";
    private static final Duration SHORT_TTL = Duration.ofMillis(100);
    private static final Duration LONG_TTL = Duration.ofMinutes(5);

    @BeforeEach
    void setUp() {
        enabledCacheManager = new SecretsCacheManager(LONG_TTL, 100, true);
        disabledCacheManager = new SecretsCacheManager(LONG_TTL, 100, false);
    }

    @Test
    @DisplayName("Should cache and retrieve value when cache is enabled")
    void shouldCacheAndRetrieveValueWhenCacheEnabled() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<String, CompletableFuture<String>> loader = key -> {
            loaderCallCount.incrementAndGet();
            return CompletableFuture.completedFuture(CACHE_VALUE);
        };

        // When - First call should trigger loader
        CompletableFuture<String> result1 = enabledCacheManager.get(CACHE_KEY, loader);
        
        // Then
        assertThat(result1).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE);
        assertThat(loaderCallCount.get()).isEqualTo(1);

        // When - Second call should use cache
        CompletableFuture<String> result2 = enabledCacheManager.get(CACHE_KEY, loader);
        
        // Then
        assertThat(result2).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE);
        assertThat(loaderCallCount.get()).isEqualTo(1); // No additional loader calls
    }

    @Test
    @DisplayName("Should always call loader when cache is disabled")
    void shouldAlwaysCallLoaderWhenCacheDisabled() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<String, CompletableFuture<String>> loader = key -> {
            loaderCallCount.incrementAndGet();
            return CompletableFuture.completedFuture(CACHE_VALUE);
        };

        // When - First call
        CompletableFuture<String> result1 = disabledCacheManager.get(CACHE_KEY, loader);
        
        // Then
        assertThat(result1).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE);
        assertThat(loaderCallCount.get()).isEqualTo(1);

        // When - Second call
        CompletableFuture<String> result2 = disabledCacheManager.get(CACHE_KEY, loader);
        
        // Then
        assertThat(result2).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE);
        assertThat(loaderCallCount.get()).isEqualTo(2); // Should call loader again
    }

    @Test
    @DisplayName("Should put value directly in cache")
    void shouldPutValueDirectlyInCache() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<String, CompletableFuture<String>> loader = key -> {
            loaderCallCount.incrementAndGet();
            return CompletableFuture.completedFuture("loader-value");
        };

        // When - Put value in cache first
        enabledCacheManager.put(CACHE_KEY, CACHE_VALUE);
        
        // Then - Should return cached value without calling loader
        CompletableFuture<String> result = enabledCacheManager.get(CACHE_KEY, loader);
        
        assertThat(result).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE);
        assertThat(loaderCallCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle cache expiration")
    void shouldHandleCacheExpiration() {
        // Given
        SecretsCacheManager shortTtlCache = new SecretsCacheManager(SHORT_TTL, 100, true);
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<String, CompletableFuture<String>> loader = key -> {
            loaderCallCount.incrementAndGet();
            return CompletableFuture.completedFuture(CACHE_VALUE + "-" + loaderCallCount.get());
        };

        // When - First call
        CompletableFuture<String> result1 = shortTtlCache.get(CACHE_KEY, loader);
        assertThat(result1).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE + "-1");

        // Wait for TTL expiration
        await().atMost(Duration.ofMillis(200)).untilAsserted(() -> {
            // When - Call after expiration
            CompletableFuture<String> result2 = shortTtlCache.get(CACHE_KEY, loader);
            assertThat(result2).succeedsWithin(Duration.ofSeconds(1))
                    .isEqualTo(CACHE_VALUE + "-2");
            assertThat(loaderCallCount.get()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("Should invalidate specific key")
    void shouldInvalidateSpecificKey() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<String, CompletableFuture<String>> loader = key -> {
            loaderCallCount.incrementAndGet();
            return CompletableFuture.completedFuture(CACHE_VALUE + "-" + loaderCallCount.get());
        };

        // When - Load initial value
        CompletableFuture<String> result1 = enabledCacheManager.get(CACHE_KEY, loader);
        assertThat(result1).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE + "-1");

        // Invalidate the key
        enabledCacheManager.invalidate(CACHE_KEY);

        // When - Call again after invalidation
        CompletableFuture<String> result2 = enabledCacheManager.get(CACHE_KEY, loader);
        
        // Then - Should call loader again
        assertThat(result2).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE + "-2");
        assertThat(loaderCallCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should invalidate all cached values")
    void shouldInvalidateAllCachedValues() {
        // Given
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        Function<String, CompletableFuture<String>> loader = key -> {
            loaderCallCount.incrementAndGet();
            return CompletableFuture.completedFuture(key + "-" + loaderCallCount.get());
        };

        String key1 = "key1";
        String key2 = "key2";

        // When - Load values for multiple keys
        CompletableFuture<String> result1 = enabledCacheManager.get(key1, loader);
        CompletableFuture<String> result2 = enabledCacheManager.get(key2, loader);
        
        assertThat(result1).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(key1 + "-1");
        assertThat(result2).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(key2 + "-2");

        // Invalidate all
        enabledCacheManager.invalidateAll();

        // When - Call again after invalidation
        CompletableFuture<String> result3 = enabledCacheManager.get(key1, loader);
        CompletableFuture<String> result4 = enabledCacheManager.get(key2, loader);
        
        // Then - Should call loader again for both keys
        assertThat(result3).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(key1 + "-3");
        assertThat(result4).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(key2 + "-4");
        assertThat(loaderCallCount.get()).isEqualTo(4);
    }

    @Test
    @DisplayName("Should get cache statistics when enabled")
    void shouldGetCacheStatisticsWhenEnabled() {
        // Given
        Function<String, CompletableFuture<String>> loader = key -> 
                CompletableFuture.completedFuture(CACHE_VALUE);

        // When - Perform cache operations
        enabledCacheManager.get(CACHE_KEY, loader).join(); // Cache miss
        enabledCacheManager.get(CACHE_KEY, loader).join(); // Cache hit
        enabledCacheManager.get("another-key", loader).join(); // Cache miss

        // Then
        SecretsCacheManager.CacheStats stats = enabledCacheManager.getStats();
        
        assertThat(stats.isEnabled()).isTrue();
        assertThat(stats.getHitCount()).isEqualTo(1);
        assertThat(stats.getMissCount()).isEqualTo(2);
        assertThat(stats.getLoadSuccessCount()).isEqualTo(2);
        assertThat(stats.getSize()).isEqualTo(2);
        assertThat(stats.hitRate()).isCloseTo(0.33, offset(0.01));
        assertThat(stats.missRate()).isCloseTo(0.67, offset(0.01));
    }

    @Test
    @DisplayName("Should return disabled stats when cache is disabled")
    void shouldReturnDisabledStatsWhenCacheDisabled() {
        // When
        SecretsCacheManager.CacheStats stats = disabledCacheManager.getStats();

        // Then
        assertThat(stats.isEnabled()).isFalse();
        assertThat(stats.getHitCount()).isEqualTo(0);
        assertThat(stats.getMissCount()).isEqualTo(0);
        assertThat(stats.hitRate()).isEqualTo(0.0);
        assertThat(stats.missRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Should handle concurrent cache operations")
    void shouldHandleConcurrentCacheOperations() throws InterruptedException {
        // Given
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger loaderCallCount = new AtomicInteger(0);
        
        Function<String, CompletableFuture<String>> loader = key -> {
            loaderCallCount.incrementAndGet();
            return CompletableFuture.completedFuture(CACHE_VALUE);
        };

        // When - Execute concurrent operations
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        enabledCacheManager.get(CACHE_KEY, loader).join();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        
        // Should have only called the loader once due to caching
        assertThat(loaderCallCount.get()).isEqualTo(1);
        
        SecretsCacheManager.CacheStats stats = enabledCacheManager.getStats();
        assertThat(stats.getHitCount()).isEqualTo(threadCount * operationsPerThread - 1);
        assertThat(stats.getMissCount()).isEqualTo(1);
        
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("Should handle cache size eviction")
    void shouldHandleCacheSizeEviction() {
        // Given
        SecretsCacheManager smallCache = new SecretsCacheManager(LONG_TTL, 2, true);
        Function<String, CompletableFuture<String>> loader = key -> 
                CompletableFuture.completedFuture("value-" + key);

        // When - Add more items than cache capacity
        smallCache.get("key1", loader).join();
        smallCache.get("key2", loader).join();
        smallCache.get("key3", loader).join(); // Should evict key1

        // Give some time for eviction to process
        await().atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            SecretsCacheManager.CacheStats stats = smallCache.getStats();
            assertThat(stats.getSize()).isLessThanOrEqualTo(2);
        });
    }

    @Test
    @DisplayName("Should handle failed loader operations")
    void shouldHandleFailedLoaderOperations() {
        // Given
        RuntimeException loaderException = new RuntimeException("Loader failed");
        Function<String, CompletableFuture<String>> failingLoader = key -> 
                CompletableFuture.failedFuture(loaderException);

        // When & Then
        assertThatThrownBy(() -> enabledCacheManager.get(CACHE_KEY, failingLoader).join())
                .hasCause(loaderException);

        // Verify failure is recorded in stats
        SecretsCacheManager.CacheStats stats = enabledCacheManager.getStats();
        assertThat(stats.getLoadFailureCount()).isEqualTo(1);
        assertThat(stats.getLoadSuccessCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle cache operations when disabled")
    void shouldHandleCacheOperationsWhenDisabled() {
        // When - Operations on disabled cache should not throw exceptions
        disabledCacheManager.put(CACHE_KEY, CACHE_VALUE); // Should be no-op
        disabledCacheManager.invalidate(CACHE_KEY); // Should be no-op
        disabledCacheManager.invalidateAll(); // Should be no-op

        // Then - No exceptions should be thrown
        // Operations complete without error
    }

    @Test
    @DisplayName("Should calculate hit and miss rates correctly")
    void shouldCalculateHitAndMissRatesCorrectly() {
        // Given - Empty cache stats
        SecretsCacheManager.CacheStats emptyStats = SecretsCacheManager.CacheStats.builder()
                .hitCount(0)
                .missCount(0)
                .enabled(true)
                .build();

        // When & Then - Empty stats should return 0 rates
        assertThat(emptyStats.hitRate()).isEqualTo(0.0);
        assertThat(emptyStats.missRate()).isEqualTo(0.0);

        // Given - Stats with hits and misses
        SecretsCacheManager.CacheStats activeStats = SecretsCacheManager.CacheStats.builder()
                .hitCount(7)
                .missCount(3)
                .enabled(true)
                .build();

        // When & Then - Should calculate rates correctly
        assertThat(activeStats.hitRate()).isCloseTo(0.7, offset(0.01));
        assertThat(activeStats.missRate()).isCloseTo(0.3, offset(0.01));
    }

    @Test
    @DisplayName("Should handle async loader completion correctly")
    void shouldHandleAsyncLoaderCompletionCorrectly() {
        // Given
        CompletableFuture<String> asyncResult = new CompletableFuture<>();
        Function<String, CompletableFuture<String>> asyncLoader = key -> asyncResult;

        // When - Start async operation
        CompletableFuture<String> result = enabledCacheManager.get(CACHE_KEY, asyncLoader);

        // Complete the async operation later
        asyncResult.complete(CACHE_VALUE);

        // Then
        assertThat(result).succeedsWithin(Duration.ofSeconds(1))
                .isEqualTo(CACHE_VALUE);
    }

    @Test
    @DisplayName("Should handle multiple cache instances independently")
    void shouldHandleMultipleCacheInstancesIndependently() {
        // Given
        SecretsCacheManager cache1 = new SecretsCacheManager(LONG_TTL, 100, true);
        SecretsCacheManager cache2 = new SecretsCacheManager(LONG_TTL, 100, true);
        
        Function<String, CompletableFuture<String>> loader = key -> 
                CompletableFuture.completedFuture("value-" + System.nanoTime());

        // When - Load same key in different caches
        CompletableFuture<String> result1 = cache1.get(CACHE_KEY, loader);
        CompletableFuture<String> result2 = cache2.get(CACHE_KEY, loader);

        // Then - Should have different values (different loader calls)
        assertThat(result1.join()).isNotEqualTo(result2.join());
        
        // And independent stats
        assertThat(cache1.getStats().getMissCount()).isEqualTo(1);
        assertThat(cache2.getStats().getMissCount()).isEqualTo(1);
    }
}