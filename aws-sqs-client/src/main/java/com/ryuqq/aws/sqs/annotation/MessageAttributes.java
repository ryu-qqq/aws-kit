package com.ryuqq.aws.sqs.annotation;

import java.lang.annotation.*;

/**
 * Marks a parameter as message attributes for SQS operations.
 * The parameter should be of type Map&lt;String, String&gt;.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageAttributes {
}