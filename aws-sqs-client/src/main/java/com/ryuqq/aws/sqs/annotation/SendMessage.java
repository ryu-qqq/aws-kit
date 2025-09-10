package com.ryuqq.aws.sqs.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Annotation for sending a single message to an SQS queue.
 * Maps to {@link com.ryuqq.aws.sqs.service.SqsService#sendMessage(String, String)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SendMessage {

    /**
     * The queue name to send the message to.
     */
    @AliasFor("queueName")
    String value() default "";

    /**
     * The queue name to send the message to.
     */
    @AliasFor("value")
    String queueName() default "";

    /**
     * Delay before the message becomes available for processing (in seconds).
     * Maximum value is 900 seconds (15 minutes).
     */
    int delaySeconds() default 0;

    /**
     * Whether this is a FIFO queue operation.
     * If true, requires @MessageGroupId parameter.
     */
    boolean fifo() default false;
}