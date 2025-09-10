package com.ryuqq.aws.sqs.annotation;

import java.lang.annotation.*;

/**
 * Marks a parameter as the message body for SQS operations.
 * The parameter will be serialized as JSON if it's not a String.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageBody {

    /**
     * Whether to serialize non-String objects as JSON.
     * If false, toString() will be used.
     */
    boolean json() default true;
}