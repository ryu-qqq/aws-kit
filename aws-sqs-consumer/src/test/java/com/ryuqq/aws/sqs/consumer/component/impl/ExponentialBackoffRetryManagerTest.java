package com.ryuqq.aws.sqs.consumer.component.impl;

import com.ryuqq.aws.sqs.consumer.component.RetryManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for ExponentialBackoffRetryManager.
 */
class ExponentialBackoffRetryManagerTest {
    
    private ExponentialBackoffRetryManager retryManager;
    
    @BeforeEach
    void setUp() {
        retryManager = new ExponentialBackoffRetryManager();
    }
    
    @Test
    void shouldReturnResultOnFirstSuccess() throws Exception {
        // Given
        String expectedResult = "success";
        AtomicInteger callCount = new AtomicInteger(0);
        
        RetryManager.RetryConfig config = createRetryConfig(3, 100);
        
        // When
        String result = retryManager.executeWithRetry(() -> {
            callCount.incrementAndGet();
            return expectedResult;
        }, config);
        
        // Then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(callCount.get()).isEqualTo(1);
    }
    
    @Test
    void shouldRetryOnFailureAndSucceedEventually() throws Exception {
        // Given
        String expectedResult = "success";
        AtomicInteger callCount = new AtomicInteger(0);
        
        RetryManager.RetryConfig config = createRetryConfig(3, 10); // Short delay for test
        
        // When
        String result = retryManager.executeWithRetry(() -> {
            int count = callCount.incrementAndGet();
            if (count < 3) {
                throw new RuntimeException("Attempt " + count + " failed");
            }
            return expectedResult;
        }, config);
        
        // Then
        assertThat(result).isEqualTo(expectedResult);
        assertThat(callCount.get()).isEqualTo(3);
    }
    
    @Test
    void shouldThrowRetryExhaustedExceptionWhenAllRetriesFail() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        RuntimeException originalException = new RuntimeException("All attempts fail");
        
        RetryManager.RetryConfig config = createRetryConfig(2, 10);
        
        // When & Then
        assertThatThrownBy(() -> retryManager.executeWithRetry(() -> {
            callCount.incrementAndGet();
            throw originalException;
        }, config))
                .isInstanceOf(RetryManager.RetryExhaustedException.class)
                .hasCause(originalException)
                .hasMessageContaining("Operation failed after 3 attempts");
        
        assertThat(callCount.get()).isEqualTo(3); // Initial + 2 retries
    }
    
    @Test
    void shouldExecuteVoidOperationWithRetry() throws Exception {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        
        RetryManager.RetryConfig config = createRetryConfig(2, 10);
        
        // When
        retryManager.executeWithRetry(() -> {
            int count = callCount.incrementAndGet();
            if (count < 2) {
                throw new RuntimeException("Attempt " + count + " failed");
            }
            successCount.incrementAndGet();
        }, config);
        
        // Then
        assertThat(callCount.get()).isEqualTo(2);
        assertThat(successCount.get()).isEqualTo(1);
    }
    
    @Test
    void shouldCalculateExponentialBackoffDelay() {
        // When & Then
        assertThat(retryManager.calculateRetryDelay(0, 1000, 30000)).isEqualTo(1000);
        
        long delay1 = retryManager.calculateRetryDelay(1, 1000, 30000);
        assertThat(delay1).isBetween(1800L, 2200L); // 2000 ± 10% jitter
        
        long delay2 = retryManager.calculateRetryDelay(2, 1000, 30000);
        assertThat(delay2).isBetween(3600L, 4400L); // 4000 ± 10% jitter
        
        long delay3 = retryManager.calculateRetryDelay(3, 1000, 30000);
        assertThat(delay3).isBetween(7200L, 8800L); // 8000 ± 10% jitter
    }
    
    @Test
    void shouldCapDelayAtMaximum() {
        // When
        long delay = retryManager.calculateRetryDelay(10, 1000, 5000);
        
        // Then
        assertThat(delay).isLessThanOrEqualTo(5000);
    }
    
    @Test
    void shouldHandleInterruptedExceptionDuringRetry() {
        // Given
        AtomicInteger callCount = new AtomicInteger(0);
        RetryManager.RetryConfig config = createRetryConfig(2, 100);
        
        // When & Then
        assertThatThrownBy(() -> {
            Thread.currentThread().interrupt(); // Set interrupt flag
            
            retryManager.executeWithRetry(() -> {
                callCount.incrementAndGet();
                throw new RuntimeException("Test exception");
            }, config);
        })
                .isInstanceOf(RetryManager.RetryExhaustedException.class)
                .hasCauseInstanceOf(InterruptedException.class);
        
        assertThat(Thread.interrupted()).isTrue(); // Clear interrupt flag
        assertThat(callCount.get()).isEqualTo(1); // Should stop on first interrupt
    }
    
    private RetryManager.RetryConfig createRetryConfig(int maxRetries, long delayMillis) {
        return new RetryManager.RetryConfig() {
            @Override
            public int getMaxRetryAttempts() {
                return maxRetries;
            }
            
            @Override
            public long getRetryDelayMillis() {
                return delayMillis;
            }
        };
    }
}