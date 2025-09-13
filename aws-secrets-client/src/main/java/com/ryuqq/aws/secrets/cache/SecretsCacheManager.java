package com.ryuqq.aws.secrets.cache;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Cache manager for secrets and parameters with TTL and size-based eviction
 */
public class SecretsCacheManager {

    private static final Logger log = LoggerFactory.getLogger(SecretsCacheManager.class);
    
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
        return new CacheStats(
                stats.hitCount(),
                stats.missCount(),
                stats.loadSuccessCount(),
                stats.loadFailureCount(),
                stats.totalLoadTime(),
                stats.evictionCount(),
                cache.synchronous().estimatedSize(),
                true
        );
    }
    
    /**
     * Cache statistics
     */
    public record CacheStats(
            long hitCount,
            long missCount,
            long loadSuccessCount,
            long loadFailureCount,
            long totalLoadTime,
            long evictionCount,
            long size,
            boolean enabled
    ) {
        
        public double hitRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) hitCount / total;
        }

        public double missRate() {
            long total = hitCount + missCount;
            return total == 0 ? 0.0 : (double) missCount / total;
        }

        public static CacheStats disabled() {
            return new CacheStats(
                    0, 0, 0, 0, 0, 0, 0, false
            );
        }
    }
}