package com.ryuqq.aws.sqs.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Annotation for sending multiple messages to an SQS queue in batch.
 * Maps to {@link com.ryuqq.aws.sqs.service.SqsService#sendMessageBatch(String, java.util.List)}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SendBatch {

    /**
     * The queue name to send the messages to.
     */
    @AliasFor("queueName")
    String value() default "";

    /**
     * The queue name to send the messages to.
     */
    @AliasFor("value")
    String queueName() default "";

    /**
     * Maximum number of messages per batch.
     * If not specified, uses the default SQS limit (10).
     */
    int batchSize() default 10;
}