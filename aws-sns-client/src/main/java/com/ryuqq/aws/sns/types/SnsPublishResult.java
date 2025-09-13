package com.ryuqq.aws.sns.types;

/**
 * Result of SNS publish operation as an immutable record
 */
public record SnsPublishResult(
    /**
     * Unique identifier assigned to the published message
     */
    String messageId,

    /**
     * Sequence number for FIFO topics
     */
    String sequenceNumber
) {

    /**
     * Create result with only message ID
     */
    public static SnsPublishResult of(String messageId) {
        return new SnsPublishResult(messageId, null);
    }

    /**
     * Create result with message ID and sequence number
     */
    public static SnsPublishResult of(String messageId, String sequenceNumber) {
        return new SnsPublishResult(messageId, sequenceNumber);
    }
}