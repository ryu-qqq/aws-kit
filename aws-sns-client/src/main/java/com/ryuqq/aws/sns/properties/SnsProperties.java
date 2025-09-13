package com.ryuqq.aws.sns.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SNS configuration properties
 */
@ConfigurationProperties(prefix = "aws.sns")
public record SnsProperties(
    /**
     * AWS region for SNS
     */
    String region,
    
    /**
     * SNS endpoint override (for LocalStack)
     */
    String endpoint,
    
    /**
     * Default topic ARN for publishing
     */
    String defaultTopicArn,
    
    /**
     * Connection configuration
     */
    ConnectionConfig connectionConfig,
    
    /**
     * Retry configuration
     */
    RetryConfig retryConfig,
    
    /**
     * SMS configuration
     */
    SmsConfig smsConfig
) {
    
    public SnsProperties {
        connectionConfig = connectionConfig != null ? connectionConfig : new ConnectionConfig(50, Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(10));
        retryConfig = retryConfig != null ? retryConfig : new RetryConfig(3, Duration.ofMillis(100), Duration.ofSeconds(20), true);
        smsConfig = smsConfig != null ? smsConfig : new SmsConfig(null, SmsConfig.SmsType.TRANSACTIONAL, 0.50);
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
    
    public record SmsConfig(
        /**
         * SMS sender ID
         */
        String senderId,
        
        /**
         * SMS type (Promotional or Transactional)
         */
        SmsType smsType,
        
        /**
         * Maximum price for SMS
         */
        Double maxPrice
    ) {
        public SmsConfig {
            smsType = smsType != null ? smsType : SmsType.TRANSACTIONAL;
            maxPrice = maxPrice != null ? maxPrice : 0.50;
        }
        
        public enum SmsType {
            PROMOTIONAL,
            TRANSACTIONAL
        }
    }
}