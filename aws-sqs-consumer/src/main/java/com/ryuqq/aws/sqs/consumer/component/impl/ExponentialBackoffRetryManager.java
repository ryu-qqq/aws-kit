package com.ryuqq.aws.sqs.consumer.component.impl;

import com.ryuqq.aws.sqs.consumer.component.RetryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Implementation of RetryManager with exponential backoff strategy.
 * Provides retry logic with configurable backoff parameters.
 */
@Slf4j
@Component
public class ExponentialBackoffRetryManager implements RetryManager {
    
    @Override
    public <T> T executeWithRetry(Supplier<T> operation, RetryConfig config) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= config.getMaxRetryAttempts(); attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < config.getMaxRetryAttempts()) {
                    long delay = calculateRetryDelay(attempt, config.getRetryDelayMillis(), 
                                                   config.getMaxRetryDelayMillis());
                    
                    log.warn("Operation attempt {} failed, retrying in {}ms: {}", 
                            attempt + 1, delay, e.getMessage());
                    
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RetryExhaustedException("Retry interrupted", attempt + 1, ie);
                    }
                } else {
                    log.error("All retry attempts exhausted after {} attempts", attempt + 1);
                }
            }
        }
        
        throw new RetryExhaustedException(
                "Operation failed after " + (config.getMaxRetryAttempts() + 1) + " attempts", 
                config.getMaxRetryAttempts() + 1, 
                lastException
        );
    }
    
    @Override
    public void executeWithRetry(Runnable operation, RetryConfig config) throws Exception {
        executeWithRetry(() -> {
            operation.run();
            return null;
        }, config);
    }
    
    @Override
    public long calculateRetryDelay(int attempt, long baseDelayMillis, long maxDelayMillis) {
        if (attempt <= 0) {
            return baseDelayMillis;
        }
        
        // Exponential backoff: delay = baseDelay * (2 ^ attempt)
        long exponentialDelay = baseDelayMillis * (long) Math.pow(2, attempt);
        
        // Add some jitter to prevent thundering herd (±10%)
        double jitterFactor = 0.9 + (Math.random() * 0.2); // 0.9 to 1.1
        long delayWithJitter = (long) (exponentialDelay * jitterFactor);
        
        // Cap at maximum delay
        return Math.min(delayWithJitter, maxDelayMillis);
    }
}