package com.ryuqq.aws.sqs.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Annotation for starting continuous polling on an SQS queue.
 * Maps to {@link com.ryuqq.aws.sqs.service.SqsService#startContinuousPolling(String, java.util.function.Consumer)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StartPolling {

    /**
     * The queue name to start polling.
     */
    @AliasFor("queueName")
    String value() default "";

    /**
     * The queue name to start polling.
     */
    @AliasFor("value")
    String queueName() default "";

    /**
     * Whether to automatically delete messages after successful processing.
     */
    boolean autoDelete() default true;

    /**
     * Number of messages to receive in each poll.
     * Valid range: 1-10.
     */
    int maxMessages() default 10;

    /**
     * Wait time for long polling (in seconds).
     * Valid range: 0-20.
     */
    int waitTimeSeconds() default 20;
}