package com.ryuqq.aws.sqs.consumer.component;

import java.util.function.Supplier;

/**
 * Interface for managing retry logic with exponential backoff.
 * Provides retry mechanisms for failed operations.
 */
public interface RetryManager {
    
    /**
     * Execute an operation with retry logic.
     *
     * @param operation the operation to execute
     * @param config retry configuration
     * @param <T> the return type of the operation
     * @return the result of the operation
     * @throws Exception if all retry attempts fail
     */
    <T> T executeWithRetry(Supplier<T> operation, RetryConfig config) throws Exception;
    
    /**
     * Execute a void operation with retry logic.
     *
     * @param operation the operation to execute
     * @param config retry configuration
     * @throws Exception if all retry attempts fail
     */
    void executeWithRetry(Runnable operation, RetryConfig config) throws Exception;
    
    /**
     * Calculate the next retry delay using exponential backoff.
     *
     * @param attempt the current attempt number (starting from 0)
     * @param baseDelayMillis the base delay in milliseconds
     * @param maxDelayMillis the maximum delay in milliseconds
     * @return the delay for the next attempt
     */
    long calculateRetryDelay(int attempt, long baseDelayMillis, long maxDelayMillis);
    
    /**
     * Configuration for retry behavior.
     */
    interface RetryConfig {
        /**
         * Maximum number of retry attempts.
         */
        int getMaxRetryAttempts();
        
        /**
         * Base delay between retry attempts in milliseconds.
         */
        long getRetryDelayMillis();
        
        /**
         * Maximum delay between retry attempts in milliseconds.
         */
        default long getMaxRetryDelayMillis() {
            return getRetryDelayMillis() * 32; // Default to 32x base delay
        }
        
        /**
         * Exponential backoff multiplier.
         */
        default double getBackoffMultiplier() {
            return 2.0;
        }
        
        /**
         * Check if exponential backoff is enabled.
         */
        default boolean isExponentialBackoffEnabled() {
            return true;
        }
    }
    
    /**
     * Exception thrown when all retry attempts are exhausted.
     */
    class RetryExhaustedException extends Exception {
        private static final long serialVersionUID = 1L;
        private final int attemptsMade;
        
        public RetryExhaustedException(String message, int attemptsMade, Throwable cause) {
            super(message, cause);
            this.attemptsMade = attemptsMade;
        }
        
        public int getAttemptsMade() {
            return attemptsMade;
        }
    }
}