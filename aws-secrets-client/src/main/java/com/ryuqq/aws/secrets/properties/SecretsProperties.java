package com.ryuqq.aws.secrets.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for Secrets Manager and Parameter Store
 */
@Data
@ConfigurationProperties(prefix = "aws.secrets")
public class SecretsProperties {
    
    /**
     * AWS region for Secrets Manager
     */
    private String region;
    
    /**
     * Secrets Manager endpoint override (for LocalStack)
     */
    private String secretsManagerEndpoint;
    
    /**
     * SSM (Parameter Store) endpoint override
     */
    private String ssmEndpoint;
    
    /**
     * Cache configuration
     */
    private CacheConfig cache = new CacheConfig();
    
    /**
     * Connection configuration
     */
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    
    /**
     * Retry configuration
     */
    private RetryConfig retryConfig = new RetryConfig();
    
    /**
     * Parameter Store specific configuration
     */
    private ParameterStoreConfig parameterStore = new ParameterStoreConfig();
    
    @Data
    public static class CacheConfig {
        /**
         * Enable caching for secrets and parameters
         */
        private boolean enabled = true;
        
        /**
         * Time to live for cached values
         */
        private Duration ttl = Duration.ofMinutes(5);
        
        /**
         * Maximum number of cached entries
         */
        private long maximumSize = 1000;
        
        /**
         * Enable cache statistics
         */
        private boolean enableStatistics = true;
    }
    
    @Data
    public static class ConnectionConfig {
        /**
         * Maximum number of connections
         */
        private int maxConnections = 50;
        
        /**
         * Connection timeout
         */
        private Duration connectionTimeout = Duration.ofSeconds(10);
        
        /**
         * Socket timeout
         */
        private Duration socketTimeout = Duration.ofSeconds(30);
        
        /**
         * Connection acquisition timeout
         */
        private Duration connectionAcquisitionTimeout = Duration.ofSeconds(10);
    }
    
    @Data
    public static class RetryConfig {
        /**
         * Maximum number of retry attempts
         */
        private int maxRetries = 3;
        
        /**
         * Base delay for exponential backoff
         */
        private Duration baseDelay = Duration.ofMillis(100);
        
        /**
         * Maximum backoff time
         */
        private Duration maxBackoff = Duration.ofSeconds(20);
        
        /**
         * Enable adaptive retry
         */
        private boolean enableAdaptiveRetry = true;
    }
    
    @Data
    public static class ParameterStoreConfig {
        /**
         * Default parameter path prefix
         */
        private String pathPrefix = "/application";
        
        /**
         * Enable recursive parameter fetching by default
         */
        private boolean recursiveByDefault = true;
        
        /**
         * Enable decryption by default for secure strings
         */
        private boolean withDecryptionByDefault = true;
    }
}