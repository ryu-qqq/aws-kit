package com.ryuqq.aws.secrets.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for Secrets Manager and Parameter Store
 */
@ConfigurationProperties(prefix = "aws.secrets")
public record SecretsProperties(
    /**
     * AWS region for Secrets Manager
     */
    String region,
    
    /**
     * Secrets Manager endpoint override (for LocalStack)
     */
    String secretsManagerEndpoint,
    
    /**
     * SSM (Parameter Store) endpoint override
     */
    String ssmEndpoint,
    
    /**
     * Cache configuration
     */
    CacheConfig cache,
    
    /**
     * Connection configuration
     */
    ConnectionConfig connectionConfig,
    
    /**
     * Retry configuration
     */
    RetryConfig retryConfig,
    
    /**
     * Parameter Store specific configuration
     */
    ParameterStoreConfig parameterStore
) {
    
    public SecretsProperties {
        cache = cache != null ? cache : new CacheConfig(true, Duration.ofMinutes(5), 1000L, true);
        connectionConfig = connectionConfig != null ? connectionConfig : new ConnectionConfig(50, Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(10));
        retryConfig = retryConfig != null ? retryConfig : new RetryConfig(3, Duration.ofMillis(100), Duration.ofSeconds(20), true);
        parameterStore = parameterStore != null ? parameterStore : new ParameterStoreConfig("/application", true, true);
    }
    
    public record CacheConfig(
        /**
         * Enable caching for secrets and parameters
         */
        boolean enabled,
        
        /**
         * Time to live for cached values
         */
        Duration ttl,
        
        /**
         * Maximum number of cached entries
         */
        long maximumSize,
        
        /**
         * Enable cache statistics
         */
        boolean enableStatistics
    ) {
        public CacheConfig {
            ttl = ttl != null ? ttl : Duration.ofMinutes(5);
            maximumSize = maximumSize == 0 ? 1000L : maximumSize;
        }
    }
    
    public record ConnectionConfig(
        /**
         * Maximum number of connections
         */
        int maxConnections,
        
        /**
         * Connection timeout
         */
        Duration connectionTimeout,
        
        /**
         * Socket timeout
         */
        Duration socketTimeout,
        
        /**
         * Connection acquisition timeout
         */
        Duration connectionAcquisitionTimeout
    ) {
        public ConnectionConfig {
            maxConnections = maxConnections == 0 ? 50 : maxConnections;
            connectionTimeout = connectionTimeout != null ? connectionTimeout : Duration.ofSeconds(10);
            socketTimeout = socketTimeout != null ? socketTimeout : Duration.ofSeconds(30);
            connectionAcquisitionTimeout = connectionAcquisitionTimeout != null ? connectionAcquisitionTimeout : Duration.ofSeconds(10);
        }
    }
    
    public record RetryConfig(
        /**
         * Maximum number of retry attempts
         */
        int maxRetries,
        
        /**
         * Base delay for exponential backoff
         */
        Duration baseDelay,
        
        /**
         * Maximum backoff time
         */
        Duration maxBackoff,
        
        /**
         * Enable adaptive retry
         */
        boolean enableAdaptiveRetry
    ) {
        public RetryConfig {
            maxRetries = maxRetries == 0 ? 3 : maxRetries;
            baseDelay = baseDelay != null ? baseDelay : Duration.ofMillis(100);
            maxBackoff = maxBackoff != null ? maxBackoff : Duration.ofSeconds(20);
        }
    }
    
    public record ParameterStoreConfig(
        /**
         * Default parameter path prefix
         */
        String pathPrefix,
        
        /**
         * Enable recursive parameter fetching by default
         */
        boolean recursiveByDefault,
        
        /**
         * Enable decryption by default for secure strings
         */
        boolean withDecryptionByDefault
    ) {
        public ParameterStoreConfig {
            pathPrefix = pathPrefix != null ? pathPrefix : "/application";
        }
    }
}