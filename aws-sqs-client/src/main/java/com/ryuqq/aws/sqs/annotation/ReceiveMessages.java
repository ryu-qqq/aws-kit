package com.ryuqq.aws.sqs.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Annotation for receiving messages from an SQS queue.
 * Maps to {@link com.ryuqq.aws.sqs.service.SqsService#receiveAndProcessMessages(String, java.util.function.Consumer)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ReceiveMessages {

    /**
     * The queue name to receive messages from.
     */
    @AliasFor("queueName")
    String value() default "";

    /**
     * The queue name to receive messages from.
     */
    @AliasFor("value")
    String queueName() default "";

    /**
     * Maximum number of messages to receive in a single call.
     * Valid range: 1-10 (SQS limitation).
     */
    int maxMessages() default 10;

    /**
     * Whether to automatically delete messages after successful processing.
     */
    boolean autoDelete() default false;

    /**
     * Wait time for long polling (in seconds).
     * Valid range: 0-20.
     */
    int waitTimeSeconds() default 0;
}