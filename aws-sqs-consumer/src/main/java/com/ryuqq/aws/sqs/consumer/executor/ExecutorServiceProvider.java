package com.ryuqq.aws.sqs.consumer.executor;

import java.util.concurrent.ExecutorService;

/**
 * Provider interface for supplying ExecutorService instances to SQS consumer components.
 * Allows for flexible thread pool configurations including platform threads, virtual threads,
 * and custom executor implementations.
 * 
 * <p>Implementations are responsible for:</p>
 * <ul>
 *   <li>Creating appropriately configured ExecutorService instances</li>
 *   <li>Providing lifecycle management capabilities</li>
 *   <li>Supporting different threading models (platform/virtual)</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public interface ExecutorServiceProvider {
    
    /**
     * Creates a new ExecutorService instance for message processing.
     * 
     * @param consumerName the name identifier for this consumer instance
     * @return a configured ExecutorService for message processing
     */
    ExecutorService createMessageProcessingExecutor(String consumerName);
    
    /**
     * Creates a new ExecutorService instance for polling operations.
     * This executor is typically single-threaded per listener.
     * 
     * @param consumerName the name identifier for this consumer instance
     * @return a configured ExecutorService for polling operations
     */
    ExecutorService createPollingExecutor(String consumerName);
    
    /**
     * Determines if this provider supports graceful shutdown.
     * When true, the provider will handle executor shutdown during application shutdown.
     * 
     * @return true if the provider manages executor lifecycle
     */
    default boolean supportsGracefulShutdown() {
        return true;
    }
    
    /**
     * Shutdown all executors created by this provider.
     * Called during application shutdown to ensure clean resource cleanup.
     * 
     * <p>Implementations should:</p>
     * <ul>
     *   <li>Shutdown all created executors gracefully</li>
     *   <li>Wait for active tasks to complete (with timeout)</li>
     *   <li>Force shutdown if graceful shutdown times out</li>
     * </ul>
     * 
     * @param timeoutMillis maximum time to wait for graceful shutdown
     */
    default void shutdown(long timeoutMillis) {
        // Default implementation - no-op for custom executors that handle their own lifecycle
    }
    
    /**
     * Get the threading model used by this provider.
     * 
     * @return the threading model enum
     */
    ThreadingModel getThreadingModel();
    
    /**
     * Enumeration of supported threading models.
     */
    enum ThreadingModel {
        /** Traditional platform threads (Java 8+) */
        PLATFORM_THREADS,
        
        /** Virtual threads (Java 21+) */
        VIRTUAL_THREADS,
        
        /** Custom user-provided executor */
        CUSTOM
    }
}