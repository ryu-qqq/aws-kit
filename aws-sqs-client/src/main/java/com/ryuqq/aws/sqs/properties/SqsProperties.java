package com.ryuqq.aws.sqs.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/**
 * SQS 클라이언트 설정 Properties
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "aws.sqs")
public class SqsProperties {

    @Valid
    @NotNull
    private MessageConfig messageConfig = new MessageConfig();

    @Valid
    @NotNull
    private BatchConfig batchConfig = new BatchConfig();

    @Valid
    @NotNull
    private DeadLetterQueue deadLetterQueue = new DeadLetterQueue();

    private String defaultQueueUrlPrefix;
    private boolean enableLongPolling = true;
    private Duration longPollingWaitTime = Duration.ofSeconds(20);

    @Getter
    @Setter
    public static class MessageConfig {
        @Min(0)
        @Max(1209600) // 14 days
        private int messageRetentionPeriod = 345600; // 4 days in seconds

        @Min(0)
        private int visibilityTimeout = 30; // seconds (max 15 minutes)

        @Min(0)
        private int receiveMessageWaitTime = 20; // seconds for long polling (max 20 seconds)

        private boolean enableMessageDeduplication = false;
        private boolean fifoQueue = false;
    }

    @Getter
    @Setter
    public static class BatchConfig {
        @Min(1)
        private int maxBatchSize = 10; // AWS SQS limit

        @Min(1)  
        private int maxReceiveMessages = 10; // AWS SQS limit

        private Duration batchFlushInterval = Duration.ofSeconds(5);
        private boolean enableBatching = true;
    }

    @Getter
    @Setter
    public static class DeadLetterQueue {
        private boolean enabled = false;
        private String queueName;
        
        @Min(1)
        private int maxReceiveCount = 3;
    }
}