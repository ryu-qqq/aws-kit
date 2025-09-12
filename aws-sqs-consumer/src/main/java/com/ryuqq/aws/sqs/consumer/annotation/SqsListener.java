package com.ryuqq.aws.sqs.consumer.annotation;

import java.lang.annotation.*;

/**
 * Annotation for SQS message listener methods.
 * Provides declarative configuration for SQS message consumption.
 * 
 * Example usage:
 * <pre>
 * {@code
 * @SqsListener(queueName = "user-events-queue")
 * public void handleUserEvent(SqsMessage message) {
 *     // Process message
 * }
 * 
 * @SqsListener(queueName = "batch-events-queue", batchMode = true, maxConcurrentMessages = 5)
 * public void handleBatchEvents(List<SqsMessage> messages) {
 *     // Process batch of messages
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SqsListener {
    
    /**
     * SQS queue name to listen to.
     * Supports property placeholders (${property.name}) and SpEL expressions (#{expression}).
     * Either queueName or queueUrl must be specified.
     */
    String queueName() default "";
    
    /**
     * SQS queue URL to listen to.
     * Supports property placeholders (${property.name}) and SpEL expressions (#{expression}).
     * Either queueName or queueUrl must be specified.
     */
    String queueUrl() default "";
    
    /**
     * Unique identifier for this listener container.
     * If not specified, defaults to the method name.
     */
    String id() default "";
    
    /**
     * Maximum number of concurrent messages to process simultaneously.
     * Default is 10.
     */
    int maxConcurrentMessages() default 10;
    
    /**
     * Long polling timeout in seconds (0-20).
     * Default is 20 seconds for optimal performance.
     */
    int pollTimeoutSeconds() default 20;
    
    /**
     * Message visibility timeout in seconds.
     * Time before the message becomes visible to other consumers if not processed.
     * Default is 30 seconds.
     */
    int messageVisibilitySeconds() default 30;
    
    /**
     * Maximum number of messages to receive in a single poll.
     * Valid range is 1-10. Default is 10.
     */
    int maxMessagesPerPoll() default 10;
    
    /**
     * Enable batch mode for processing multiple messages together.
     * When enabled, the annotated method should accept List<SqsMessage> parameter.
     * Default is false.
     */
    boolean batchMode() default false;
    
    /**
     * Batch size for batch mode processing.
     * Only applicable when batchMode is true.
     * Default is 10.
     */
    int batchSize() default 10;
    
    /**
     * Enable automatic message deletion after successful processing.
     * Default is true.
     */
    boolean autoDelete() default true;
    
    /**
     * Maximum number of retry attempts for failed message processing.
     * Default is 3.
     */
    int maxRetryAttempts() default 3;
    
    /**
     * Delay between retry attempts in milliseconds.
     * Default is 1000ms (1 second).
     */
    long retryDelayMillis() default 1000L;
    
    /**
     * Enable dead letter queue processing for failed messages.
     * Default is false.
     */
    boolean enableDeadLetterQueue() default false;
    
    /**
     * Dead letter queue name for failed messages.
     * Only used when enableDeadLetterQueue is true.
     */
    String deadLetterQueueName() default "";
}