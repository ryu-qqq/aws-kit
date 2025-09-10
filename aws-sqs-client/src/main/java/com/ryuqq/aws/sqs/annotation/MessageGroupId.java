package com.ryuqq.aws.sqs.annotation;

import java.lang.annotation.*;

/**
 * Marks a parameter as the message group ID for FIFO queue operations.
 * Required for FIFO queues to maintain message ordering.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageGroupId {
}