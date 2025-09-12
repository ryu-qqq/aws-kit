package com.ryuqq.aws.sns.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * SNS configuration properties
 */
@Data
@ConfigurationProperties(prefix = "aws.sns")
public class SnsProperties {
    
    /**
     * AWS region for SNS
     */
    private String region;
    
    /**
     * SNS endpoint override (for LocalStack)
     */
    private String endpoint;
    
    /**
     * Default topic ARN for publishing
     */
    private String defaultTopicArn;
    
    /**
     * Connection configuration
     */
    private ConnectionConfig connectionConfig = new ConnectionConfig();
    
    /**
     * Retry configuration
     */
    private RetryConfig retryConfig = new RetryConfig();
    
    /**
     * SMS configuration
     */
    private SmsConfig smsConfig = new SmsConfig();
    
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
    public static class SmsConfig {
        /**
         * SMS sender ID
         */
        private String senderId;
        
        /**
         * SMS type (Promotional or Transactional)
         */
        private SmsType smsType = SmsType.TRANSACTIONAL;
        
        /**
         * Maximum price for SMS
         */
        private Double maxPrice = 0.50;
        
        public enum SmsType {
            PROMOTIONAL,
            TRANSACTIONAL
        }
    }
}