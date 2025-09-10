package com.ryuqq.aws.sqs.annotation;

import java.lang.annotation.*;

/**
 * Marks a parameter as the maximum number of messages to receive.
 * Used with @ReceiveMessages and @StartPolling operations.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MaxMessages {
}