package com.ryuqq.aws.sqs.consumer.container;

import com.ryuqq.aws.sqs.consumer.annotation.SqsListener;
import com.ryuqq.aws.sqs.consumer.component.*;
import com.ryuqq.aws.sqs.consumer.component.MessageProcessor.ProcessingConfig;
import com.ryuqq.aws.sqs.consumer.component.RetryManager.RetryConfig;
import com.ryuqq.aws.sqs.consumer.component.DeadLetterQueueHandler.DlqConfig;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refactored SQS listener container following SOLID principles.
 * Delegates responsibilities to specialized components for better maintainability and testability.
 */
public class RefactoredSqsListenerContainer {

    private static final Logger log = LoggerFactory.getLogger(RefactoredSqsListenerContainer.class);

    private final String containerId;
    private final Object targetBean;
    private final Method targetMethod;
    private final SqsListener listenerAnnotation;
    private final SqsService sqsService;
    private final Environment environment;
    private final ExecutorService executorService;
    
    // Injected components following SOLID principles
    private final MessagePoller messagePoller;
    private final MessageProcessor messageProcessor;
    private final RetryManager retryManager;
    private final DeadLetterQueueHandler dlqHandler;
    private final MetricsCollector metricsCollector;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String resolvedQueueUrl;
    
    // Configuration objects implementing interfaces
    private final ProcessingConfigImpl processingConfig;
    private final RetryConfigImpl retryConfig;
    private final DlqConfigImpl dlqConfig;
    
    public RefactoredSqsListenerContainer(String containerId,
                                        Object targetBean,
                                        Method targetMethod,
                                        SqsListener listenerAnnotation,
                                        SqsService sqsService,
                                        Environment environment,
                                        ExecutorService executorService,
                                        MessagePoller messagePoller,
                                        MessageProcessor messageProcessor,
                                        RetryManager retryManager,
                                        DeadLetterQueueHandler dlqHandler,
                                        MetricsCollector metricsCollector) {
        
        this.containerId = containerId;
        this.targetBean = targetBean;
        this.targetMethod = targetMethod;
        this.listenerAnnotation = listenerAnnotation;
        this.sqsService = sqsService;
        this.environment = environment;
        this.executorService = executorService;
        
        this.messagePoller = messagePoller;
        this.messageProcessor = messageProcessor;
        this.retryManager = retryManager;
        this.dlqHandler = dlqHandler;
        this.metricsCollector = metricsCollector;
        
        // Initialize configuration objects
        this.processingConfig = new ProcessingConfigImpl();
        this.retryConfig = new RetryConfigImpl();
        this.dlqConfig = new DlqConfigImpl();
        
        this.targetMethod.setAccessible(true);
    }
    
    /**
     * Start the listener container.
     */
    public synchronized void start() {
        if (running.getAndSet(true)) {
            log.warn("Container {} is already running", containerId);
            return;
        }
        
        try {
            resolveQueueUrl();
            startMessagePolling();
            
            log.info("Started SQS listener container: {} for queue: {}", containerId, resolvedQueueUrl);
        } catch (Exception e) {
            running.set(false);
            log.error("Failed to start container {}: {}", containerId, e.getMessage(), e);
            throw new RuntimeException("Failed to start SQS listener container", e);
        }
    }
    
    /**
     * Stop the listener container.
     */
    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        
        log.info("Stopping SQS listener container: {}", containerId);
        
        try {
            messagePoller.stopPolling();
            log.info("Stopped SQS listener container: {}", containerId);
        } catch (Exception e) {
            log.error("Error stopping container {}: {}", containerId, e.getMessage(), e);
        }
    }
    
    /**
     * Check if the container is running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Get container statistics.
     */
    public ContainerStats getStats() {
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(containerId);
        return new ContainerStats(
                containerId,
                running.get(),
                metrics.getProcessedCount(),
                metrics.getFailedCount()
        );
    }
    
    private void resolveQueueUrl() throws Exception {
        String queueName = resolveProperty(listenerAnnotation.queueName());
        String queueUrl = resolveProperty(listenerAnnotation.queueUrl());
        
        if (queueName.isEmpty() && queueUrl.isEmpty()) {
            throw new IllegalArgumentException("Either queueName or queueUrl must be specified for container: " + containerId);
        }
        
        if (!queueUrl.isEmpty()) {
            resolvedQueueUrl = queueUrl;
        } else {
            resolvedQueueUrl = sqsService.getQueueUrl(queueName).get();
        }
        
        log.info("Container {} will listen to queue: {}", containerId, resolvedQueueUrl);
    }
    
    private void startMessagePolling() {
        messagePoller.startPolling(
                resolvedQueueUrl,
                listenerAnnotation.maxMessagesPerPoll(),
                listenerAnnotation.pollTimeoutSeconds(),
                this::handlePolledMessages
        );
    }
    
    private void handlePolledMessages(List<SqsMessage> messages) {
        if (listenerAnnotation.batchMode()) {
            processBatch(messages);
        } else {
            messages.forEach(this::processMessage);
        }
    }
    
    private void processMessage(SqsMessage message) {
        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // Process with retry logic
                retryManager.executeWithRetry(() -> {
                    try {
                        messageProcessor.processMessage(message, processingConfig);
                    } catch (MessageProcessor.MessageProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }, retryConfig);
                
                long processingTime = System.currentTimeMillis() - startTime;
                metricsCollector.recordMessageProcessed(containerId);
                metricsCollector.recordProcessingTime(containerId, processingTime);
                
            } catch (Exception e) {
                metricsCollector.recordMessageFailed(containerId, e);
                log.error("Failed to process message {} for container {}: {}", 
                        message.getMessageId(), containerId, e.getMessage(), e);
                
                handleFailedMessage(message, e);
            }
        }, executorService);
    }
    
    private void processBatch(List<SqsMessage> messages) {
        CompletableFuture.runAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                // Process batch with retry logic
                retryManager.executeWithRetry(() -> {
                    try {
                        messageProcessor.processBatch(messages, processingConfig);
                    } catch (MessageProcessor.MessageProcessingException e) {
                        throw new RuntimeException(e);
                    }
                }, retryConfig);
                
                long processingTime = System.currentTimeMillis() - startTime;
                metricsCollector.recordMessageProcessed(containerId);
                metricsCollector.recordProcessingTime(containerId, processingTime);
                
            } catch (Exception e) {
                metricsCollector.recordMessageFailed(containerId, e);
                log.error("Failed to process message batch for container {}: {}", 
                        containerId, e.getMessage(), e);
                
                messages.forEach(message -> handleFailedMessage(message, e));
            }
        }, executorService);
    }
    
    private void handleFailedMessage(SqsMessage message, Exception exception) {
        try {
            boolean dlqSuccess = dlqHandler.sendToDeadLetterQueue(message, exception, dlqConfig);
            // Record DLQ operation through state change
            metricsCollector.recordStateChange(containerId, "PROCESSING", dlqSuccess ? "DLQ_SUCCESS" : "DLQ_FAILED");
        } catch (Exception dlqException) {
            metricsCollector.recordStateChange(containerId, "PROCESSING", "DLQ_FAILED");
            log.error("Failed to handle failed message for container {}: {}", 
                    containerId, dlqException.getMessage(), dlqException);
        }
    }
    
    private String resolveProperty(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return environment.resolvePlaceholders(value);
    }
    
    /**
     * Implementation of ProcessingConfig.
     */
    private class ProcessingConfigImpl implements ProcessingConfig {
        @Override
        public Object getTargetBean() {
            return targetBean;
        }
        
        @Override
        public Method getTargetMethod() {
            return targetMethod;
        }
        
        @Override
        public boolean isBatchMode() {
            return listenerAnnotation.batchMode();
        }
        
        @Override
        public boolean isAutoDelete() {
            return listenerAnnotation.autoDelete();
        }
        
        @Override
        public String getQueueUrl() {
            return resolvedQueueUrl;
        }
    }
    
    /**
     * Implementation of RetryConfig.
     */
    private class RetryConfigImpl implements RetryConfig {
        @Override
        public int getMaxRetryAttempts() {
            return listenerAnnotation.maxRetryAttempts();
        }
        
        @Override
        public long getRetryDelayMillis() {
            return listenerAnnotation.retryDelayMillis();
        }
    }
    
    /**
     * Implementation of DlqConfig.
     */
    private class DlqConfigImpl implements DlqConfig {
        @Override
        public boolean isEnabled() {
            return listenerAnnotation.enableDeadLetterQueue();
        }
        
        @Override
        public String getDlqName() {
            return listenerAnnotation.deadLetterQueueName();
        }
        
        @Override
        public String getContainerId() {
            return containerId;
        }
        
        @Override
        public Map<String, String> getAdditionalMetadata() {
            return Map.of(
                    "originalQueueUrl", resolvedQueueUrl,
                    "listenerMethod", targetMethod.getName(),
                    "batchMode", String.valueOf(listenerAnnotation.batchMode())
            );
        }
    }
    
    /**
     * Container statistics data class.
     */
    public static class ContainerStats {
        private final String containerId;
        private final boolean running;
        private final long processedMessages;
        private final long failedMessages;
        
        public ContainerStats(String containerId, boolean running, long processedMessages, long failedMessages) {
            this.containerId = containerId;
            this.running = running;
            this.processedMessages = processedMessages;
            this.failedMessages = failedMessages;
        }
        
        public String getContainerId() { return containerId; }
        public boolean isRunning() { return running; }
        public long getProcessedMessages() { return processedMessages; }
        public long getFailedMessages() { return failedMessages; }
        
        @Override
        public String toString() {
            return String.format("ContainerStats{id='%s', running=%s, processed=%d, failed=%d}", 
                    containerId, running, processedMessages, failedMessages);
        }
    }
}