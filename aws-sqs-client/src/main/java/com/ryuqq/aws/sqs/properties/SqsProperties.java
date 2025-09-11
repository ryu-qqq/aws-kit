package com.ryuqq.aws.sqs.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simplified SQS configuration properties
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "aws.sqs")
public class SqsProperties {

    /**
     * Long polling wait time (seconds) - AWS SDK validates 0-20
     */
    private int longPollingWaitSeconds = 20;

    /**
     * Maximum batch size - AWS SDK validates 1-10
     */
    private int maxBatchSize = 10;

    /**
     * Message visibility timeout (seconds)
     */
    private int visibilityTimeout = 30;
}