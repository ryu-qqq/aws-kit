package com.ryuqq.aws.sqs.annotation;

import java.lang.annotation.*;

/**
 * Marks a parameter as the message processor for receiving operations.
 * The parameter should be of type Consumer&lt;SqsMessage&gt; or Function&lt;SqsMessage, ?&gt;.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageProcessor {
}