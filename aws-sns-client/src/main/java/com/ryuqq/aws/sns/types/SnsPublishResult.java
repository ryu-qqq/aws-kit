package com.ryuqq.aws.sns.types;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Result of SNS publish operation
 */
@Getter
@Builder
@ToString
public class SnsPublishResult {
    
    /**
     * Unique identifier assigned to the published message
     */
    private final String messageId;
    
    /**
     * Sequence number for FIFO topics
     */
    private final String sequenceNumber;
}