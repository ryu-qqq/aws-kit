package com.ryuqq.aws.sqs.consumer.config;

import com.ryuqq.aws.sqs.consumer.component.*;
import com.ryuqq.aws.sqs.consumer.component.impl.*;
import com.ryuqq.aws.sqs.service.SqsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Spring configuration for SQS consumer components.
 * Provides default implementations for all component interfaces.
 */
@Configuration
public class SqsComponentConfiguration {
    
    /**
     * Default message poller implementation.
     * Uses SqsService for polling operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public MessagePoller messagePoller(SqsService sqsService) {
        return new DefaultMessagePoller(sqsService);
    }
    
    /**
     * Default message processor implementation.
     * Handles single messages and batches with auto-delete support.
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageProcessor messageProcessor(SqsService sqsService) {
        return new DefaultMessageProcessor(sqsService);
    }
    
    /**
     * Default retry manager implementation.
     * Uses exponential backoff strategy with jitter.
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryManager retryManager() {
        return new ExponentialBackoffRetryManager();
    }
    
    /**
     * Default dead letter queue handler implementation.
     * Creates structured DLQ messages with metadata.
     */
    @Bean
    @ConditionalOnMissingBean
    public DeadLetterQueueHandler deadLetterQueueHandler(SqsService sqsService, Environment environment) {
        return new DefaultDeadLetterQueueHandler(sqsService, environment);
    }
    
    /**
     * Default metrics collector implementation.
     * Stores metrics in memory for runtime monitoring.
     */
    @Bean
    @ConditionalOnMissingBean
    public MetricsCollector metricsCollector() {
        return new InMemoryMetricsCollector();
    }
}