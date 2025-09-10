package com.ryuqq.aws.sqs.annotation;

import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * Marks an interface as an SQS client, similar to FeignClient.
 * The annotated interface will be proxied to provide SQS operations.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SqsClient {

    /**
     * The logical name of the SQS client.
     * Used for metrics and logging.
     */
    @AliasFor("name")
    String value() default "";

    /**
     * The logical name of the SQS client.
     * Used for metrics and logging.
     */
    @AliasFor("value")
    String name() default "";

    /**
     * Default queue name prefix for all operations in this client.
     * Can be overridden by individual method annotations.
     */
    String queuePrefix() default "";

    /**
     * Whether to automatically create queues if they don't exist.
     */
    boolean autoCreateQueues() default false;

    /**
     * Default queue attributes for auto-created queues.
     */
    String[] defaultQueueAttributes() default {};
}