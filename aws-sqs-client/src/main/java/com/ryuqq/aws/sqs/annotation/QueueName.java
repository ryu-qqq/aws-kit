package com.ryuqq.aws.sqs.annotation;

import java.lang.annotation.*;

/**
 * Marks a parameter as the queue name for SQS operations.
 * This allows dynamic queue name specification at runtime.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QueueName {
}