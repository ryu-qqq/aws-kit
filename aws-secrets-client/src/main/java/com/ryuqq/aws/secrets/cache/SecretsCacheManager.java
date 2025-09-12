package com.ryuqq.aws.secrets.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Cache manager for secrets and parameters with TTL and size-based eviction
 */
@Slf4j
public class SecretsCacheManager {
    
    private final AsyncCache<String, String> cache;
    private final boolean cacheEnabled;
    
    public SecretsCacheManager(Duration ttl, long maximumSize, boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
        
        if (cacheEnabled) {
            this.cache = Caffeine.newBuilder()
                    .maximumSize(maximumSize)
                    .expireAfterWrite(ttl)
                    .recordStats()
                    .buildAsync();
            
            log.info("Secrets cache initialized with TTL: {} and max size: {}", ttl, maximumSize);
        } else {
            this.cache = null;
            log.info("Secrets cache is disabled");
        }
    }
    
    /**
     * Get value from cache or load it
     */
    public CompletableFuture<String> get(String key, 
            Function<String, CompletableFuture<String>> loader) {
        
        if (!cacheEnabled) {
            log.trace("Cache disabled, loading value for key: {}", key);
            return loader.apply(key);
        }
        
        return cache.get(key, (cacheKey, executor) -> loader.apply(cacheKey))
                .thenApply(value -> {
                    log.trace("Retrieved value for key: {} from cache", key);
                    return value;
                });
    }
    
    /**
     * Put value in cache
     */
    public void put(String key, String value) {
        if (!cacheEnabled) {
            return;
        }
        
        cache.put(key, CompletableFuture.completedFuture(value));
        log.trace("Cached value for key: {}", key);
    }
    
    /**
     * Invalidate specific key
     */
    public void invalidate(String key) {
        if (!cacheEnabled) {
            return;
        }
        
        cache.synchronous().invalidate(key);
        log.debug("Invalidated cache for key: {}", key);
    }
    
    /**
     * Invalidate all cached values
     */
    public void invalidateAll() {
        if (!cacheEnabled) {
            return;
        }
        
        cache.synchronous().invalidateAll();
        log.info("Invalidated all cached values");
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        if (!cacheEnabled || cache == null) {
            return CacheStats.disabled();
        }
        
        var stats = cache.synchronous().stats();
        return CacheStats.builder()
                .hitCount(stats.hitCount())
                .missCount(stats.missCount())
                .loadSuccessCount(stats.loadSuccessCount())
                .loadFailureCount(stats.loadFailureCount())
                .totalLoadTime(stats.totalLoadTime())
                .evictionCount(stats.evictionCount())
                .size(cache.synchronous().estimatedSize())
                .enabled(true)
                .build();
    }
    
    /**
     * Cache statistics
     */
    @lombok.Builder
    @lombok.Getter
    public static class CacheStats {
        private final long hitCount;
        private final long missCount;
        private final long loadSuccessCount;
        private final long loadFailureCount;
        private final long totalLoadTime;
        private final long evictionCount;
        private final long size;
        private final boolean enabled;
        
        public double hitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }
        
        public double missRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) missCount / total;
        }
        
        public static CacheStats disabled() {
            return CacheStats.builder()
                    .enabled(false)
                    .build();
        }
    }
}