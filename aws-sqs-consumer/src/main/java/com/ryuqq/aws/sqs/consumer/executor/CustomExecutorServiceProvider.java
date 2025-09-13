package com.ryuqq.aws.sqs.consumer.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.function.Function;

/**
 * ExecutorServiceProvider implementation that allows users to provide their own
 * ExecutorService instances through factory functions.
 * 
 * <p>This implementation is useful when users need:</p>
 * <ul>
 *   <li>Custom thread pool configurations</li>
 *   <li>Integration with existing thread management systems</li>
 *   <li>Specialized threading models (e.g., ForkJoinPool)</li>
 *   <li>External monitoring and management of executors</li>
 * </ul>
 * 
 * <p>Lifecycle management is delegated to the user-provided executors,
 * allowing integration with frameworks like Spring's TaskExecutor.</p>
 * 
 * @since 1.0.0
 */
public class CustomExecutorServiceProvider implements ExecutorServiceProvider {

    private static final Logger log = LoggerFactory.getLogger(CustomExecutorServiceProvider.class);

    private final Function<String, ExecutorService> messageProcessingExecutorFactory;
    private final Function<String, ExecutorService> pollingExecutorFactory;
    private final boolean shouldManageLifecycle;
    
    /**
     * Creates a CustomExecutorServiceProvider with separate factories for different executor types.
     * 
     * @param messageProcessingExecutorFactory factory function for message processing executors
     * @param pollingExecutorFactory factory function for polling executors
     * @param shouldManageLifecycle whether this provider should manage executor lifecycle
     */
    public CustomExecutorServiceProvider(
            Function<String, ExecutorService> messageProcessingExecutorFactory,
            Function<String, ExecutorService> pollingExecutorFactory,
            boolean shouldManageLifecycle) {
        this.messageProcessingExecutorFactory = messageProcessingExecutorFactory;
        this.pollingExecutorFactory = pollingExecutorFactory;
        this.shouldManageLifecycle = shouldManageLifecycle;
        
        log.info("Initialized CustomExecutorServiceProvider with lifecycle management: {}", shouldManageLifecycle);
    }
    
    /**
     * Creates a CustomExecutorServiceProvider that uses the same factory for both executor types.
     * 
     * @param executorFactory factory function for both message processing and polling executors
     * @param shouldManageLifecycle whether this provider should manage executor lifecycle
     */
    public CustomExecutorServiceProvider(
            Function<String, ExecutorService> executorFactory,
            boolean shouldManageLifecycle) {
        this(executorFactory, executorFactory, shouldManageLifecycle);
    }
    
    /**
     * Creates a CustomExecutorServiceProvider that uses the same executor instance for all consumers.
     * Lifecycle management is disabled as the user retains control of the shared executor.
     * 
     * @param sharedExecutor the executor service to use for all operations
     */
    public CustomExecutorServiceProvider(ExecutorService sharedExecutor) {
        this(consumerName -> sharedExecutor, false);
        log.info("Initialized CustomExecutorServiceProvider with shared executor: {}", 
                sharedExecutor.getClass().getSimpleName());
    }
    
    @Override
    public ExecutorService createMessageProcessingExecutor(String consumerName) {
        try {
            ExecutorService executor = messageProcessingExecutorFactory.apply(consumerName);
            if (executor == null) {
                throw new IllegalStateException("Message processing executor factory returned null for consumer: " + consumerName);
            }
            
            log.debug("Created custom message processing executor for consumer '{}': {}", 
                    consumerName, executor.getClass().getSimpleName());
            
            return executor;
        } catch (Exception e) {
            log.error("Failed to create custom message processing executor for consumer '{}': {}", 
                    consumerName, e.getMessage());
            throw new RuntimeException("Failed to create custom message processing executor", e);
        }
    }
    
    @Override
    public ExecutorService createPollingExecutor(String consumerName) {
        try {
            ExecutorService executor = pollingExecutorFactory.apply(consumerName);
            if (executor == null) {
                throw new IllegalStateException("Polling executor factory returned null for consumer: " + consumerName);
            }
            
            log.debug("Created custom polling executor for consumer '{}': {}", 
                    consumerName, executor.getClass().getSimpleName());
            
            return executor;
        } catch (Exception e) {
            log.error("Failed to create custom polling executor for consumer '{}': {}", 
                    consumerName, e.getMessage());
            throw new RuntimeException("Failed to create custom polling executor", e);
        }
    }
    
    @Override
    public boolean supportsGracefulShutdown() {
        return shouldManageLifecycle;
    }
    
    @Override
    public void shutdown(long timeoutMillis) {
        if (!shouldManageLifecycle) {
            log.debug("CustomExecutorServiceProvider configured not to manage lifecycle, skipping shutdown");
            return;
        }
        
        log.info("CustomExecutorServiceProvider shutdown requested, but lifecycle management " +
                "is implemented by the user-provided executors");
        
        // Note: In a custom provider with lifecycle management enabled,
        // users would typically override this method to implement proper shutdown logic
        // for their custom executors. This base implementation assumes the user
        // will handle shutdown externally.
    }
    
    @Override
    public ThreadingModel getThreadingModel() {
        return ThreadingModel.CUSTOM;
    }
    
    /**
     * Builder for creating CustomExecutorServiceProvider instances with fluent configuration.
     */
    public static class Builder {
        private Function<String, ExecutorService> messageProcessingFactory;
        private Function<String, ExecutorService> pollingFactory;
        private boolean manageLifecycle = false;
        
        /**
         * Set the factory for message processing executors.
         */
        public Builder messageProcessingExecutorFactory(Function<String, ExecutorService> factory) {
            this.messageProcessingFactory = factory;
            return this;
        }
        
        /**
         * Set the factory for polling executors.
         */
        public Builder pollingExecutorFactory(Function<String, ExecutorService> factory) {
            this.pollingFactory = factory;
            return this;
        }
        
        /**
         * Use the same factory for both message processing and polling executors.
         */
        public Builder executorFactory(Function<String, ExecutorService> factory) {
            this.messageProcessingFactory = factory;
            this.pollingFactory = factory;
            return this;
        }
        
        /**
         * Use the same shared executor for all operations.
         */
        public Builder sharedExecutor(ExecutorService executor) {
            Function<String, ExecutorService> factory = consumerName -> executor;
            this.messageProcessingFactory = factory;
            this.pollingFactory = factory;
            this.manageLifecycle = false; // Don't manage lifecycle for shared executors
            return this;
        }
        
        /**
         * Enable lifecycle management by this provider.
         */
        public Builder withLifecycleManagement() {
            this.manageLifecycle = true;
            return this;
        }
        
        /**
         * Build the CustomExecutorServiceProvider instance.
         */
        public CustomExecutorServiceProvider build() {
            if (messageProcessingFactory == null) {
                throw new IllegalStateException("Message processing executor factory must be set");
            }
            if (pollingFactory == null) {
                pollingFactory = messageProcessingFactory;
            }
            
            return new CustomExecutorServiceProvider(
                messageProcessingFactory, 
                pollingFactory, 
                manageLifecycle);
        }
    }
    
    /**
     * Create a new builder for fluent configuration.
     */
    public static Builder builder() {
        return new Builder();
    }
}