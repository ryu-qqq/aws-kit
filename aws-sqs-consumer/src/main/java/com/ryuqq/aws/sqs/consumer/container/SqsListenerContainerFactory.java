package com.ryuqq.aws.sqs.consumer.container;

import com.ryuqq.aws.sqs.consumer.annotation.SqsListener;
import com.ryuqq.aws.sqs.consumer.component.*;
import com.ryuqq.aws.sqs.service.SqsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

/**
 * Factory for creating SqsListenerContainer instances with proper dependency injection.
 * Follows the Factory pattern and ensures proper component wiring.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsListenerContainerFactory {
    
    private final SqsService sqsService;
    private final Environment environment;
    private final ApplicationContext applicationContext;
    private final ExecutorService executorService;
    
    // Component dependencies
    private final MessagePoller messagePoller;
    private final MessageProcessor messageProcessor;
    private final RetryManager retryManager;
    private final DeadLetterQueueHandler dlqHandler;
    private final MetricsCollector metricsCollector;
    
    /**
     * Create a new SqsListenerContainer with the refactored implementation.
     *
     * @param containerId unique identifier for the container
     * @param targetBean the bean containing the listener method
     * @param targetMethod the listener method
     * @param listenerAnnotation the SqsListener annotation
     * @return configured SqsListenerContainer
     */
    public RefactoredSqsListenerContainer createContainer(String containerId,
                                                        Object targetBean,
                                                        Method targetMethod,
                                                        SqsListener listenerAnnotation) {
        
        log.debug("Creating SQS listener container: {}", containerId);
        
        validateContainerConfiguration(containerId, targetBean, targetMethod, listenerAnnotation);
        
        return new RefactoredSqsListenerContainer(
                containerId,
                targetBean,
                targetMethod,
                listenerAnnotation,
                sqsService,
                environment,
                executorService,
                messagePoller,
                messageProcessor,
                retryManager,
                dlqHandler,
                metricsCollector
        );
    }
    
    /**
     * Create a container using the legacy implementation for backward compatibility.
     *
     * @param containerId unique identifier for the container
     * @param targetBean the bean containing the listener method
     * @param targetMethod the listener method
     * @param listenerAnnotation the SqsListener annotation
     * @return configured legacy SqsListenerContainer
     */
    public SqsListenerContainer createLegacyContainer(String containerId,
                                                     Object targetBean,
                                                     Method targetMethod,
                                                     SqsListener listenerAnnotation) {
        
        log.debug("Creating legacy SQS listener container: {}", containerId);
        
        validateContainerConfiguration(containerId, targetBean, targetMethod, listenerAnnotation);
        
        return new SqsListenerContainer(
                containerId,
                targetBean,
                targetMethod,
                listenerAnnotation,
                sqsService,
                environment,
                applicationContext,
                executorService,
                executorService
        );
    }
    
    private void validateContainerConfiguration(String containerId,
                                              Object targetBean,
                                              Method targetMethod,
                                              SqsListener listenerAnnotation) {
        
        if (containerId == null || containerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Container ID cannot be null or empty");
        }
        
        if (targetBean == null) {
            throw new IllegalArgumentException("Target bean cannot be null");
        }
        
        if (targetMethod == null) {
            throw new IllegalArgumentException("Target method cannot be null");
        }
        
        if (listenerAnnotation == null) {
            throw new IllegalArgumentException("SqsListener annotation cannot be null");
        }
        
        // Validate queue configuration
        String queueName = listenerAnnotation.queueName();
        String queueUrl = listenerAnnotation.queueUrl();
        
        if ((queueName == null || queueName.trim().isEmpty()) && 
            (queueUrl == null || queueUrl.trim().isEmpty())) {
            throw new IllegalArgumentException("Either queueName or queueUrl must be specified");
        }
        
        // Validate method parameters for batch mode
        if (listenerAnnotation.batchMode()) {
            Class<?>[] parameterTypes = targetMethod.getParameterTypes();
            if (parameterTypes.length != 1 || 
                !java.util.List.class.isAssignableFrom(parameterTypes[0])) {
                throw new IllegalArgumentException(
                        "Batch mode requires method to have exactly one List parameter");
            }
        } else {
            Class<?>[] parameterTypes = targetMethod.getParameterTypes();
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException(
                        "Single message mode requires method to have exactly one parameter");
            }
        }
        
        // Validate DLQ configuration
        if (listenerAnnotation.enableDeadLetterQueue() && 
            (listenerAnnotation.deadLetterQueueName() == null || 
             listenerAnnotation.deadLetterQueueName().trim().isEmpty())) {
            throw new IllegalArgumentException(
                    "Dead letter queue name must be specified when DLQ is enabled");
        }
        
        // Validate retry configuration
        if (listenerAnnotation.maxRetryAttempts() < 0) {
            throw new IllegalArgumentException("Max retry attempts cannot be negative");
        }
        
        if (listenerAnnotation.retryDelayMillis() < 0) {
            throw new IllegalArgumentException("Retry delay cannot be negative");
        }
        
        // Validate polling configuration
        if (listenerAnnotation.maxMessagesPerPoll() < 1 || listenerAnnotation.maxMessagesPerPoll() > 10) {
            throw new IllegalArgumentException("Max messages per poll must be between 1 and 10");
        }
        
        if (listenerAnnotation.pollTimeoutSeconds() < 0 || listenerAnnotation.pollTimeoutSeconds() > 20) {
            throw new IllegalArgumentException("Poll timeout must be between 0 and 20 seconds");
        }
    }
}