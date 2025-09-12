package com.ryuqq.aws.sqs.consumer.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.Map;

/**
 * Data class representing a failed message that will be sent to a Dead Letter Queue (DLQ).
 * 
 * <p>This class provides secure JSON serialization for failed messages, including:</p>
 * <ul>
 *   <li>Original message context and metadata</li>
 *   <li>Error information and diagnostics</li>
 *   <li>Processing context (container ID, retry attempts)</li>
 *   <li>Timestamp information for troubleshooting</li>
 * </ul>
 * 
 * <p>Security considerations:</p>
 * <ul>
 *   <li>Uses Jackson annotations for safe JSON serialization</li>
 *   <li>Prevents JSON injection through proper escaping</li>
 *   <li>Includes all necessary context for debugging without exposing sensitive data</li>
 * </ul>
 * 
 * @since 1.0.0
 */
@Builder
@Getter
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DlqMessage {
    
    /**
     * The original message ID from SQS.
     */
    @JsonProperty("originalMessageId")
    private final String originalMessageId;
    
    /**
     * The original message body content.
     */
    @JsonProperty("originalMessage")
    private final String originalMessage;
    
    /**
     * The error message that caused the failure.
     */
    @JsonProperty("errorMessage")
    private final String errorMessage;
    
    /**
     * The type/class name of the exception that occurred.
     */
    @JsonProperty("errorType")
    private final String errorType;
    
    /**
     * Timestamp when the message was sent to the DLQ.
     */
    @JsonProperty("timestamp")
    private final Instant timestamp;
    
    /**
     * The container ID that processed the failed message.
     */
    @JsonProperty("containerId")
    private final String containerId;
    
    /**
     * The original queue URL where the message was received.
     */
    @JsonProperty("queueUrl")
    private final String queueUrl;
    
    /**
     * Number of retry attempts that were made before failure.
     */
    @JsonProperty("retryAttempts")
    private final Integer retryAttempts;
    
    /**
     * Original message attributes from SQS.
     */
    @JsonProperty("originalAttributes")
    private final Map<String, String> originalAttributes;
    
    /**
     * Additional context information for debugging.
     */
    @JsonProperty("additionalContext")
    private final Map<String, Object> additionalContext;
    
    /**
     * Version of the DLQ message format for future compatibility.
     */
    @JsonProperty("version")
    @Builder.Default
    private final String version = "1.0";
    
    /**
     * Source system that generated this DLQ message.
     */
    @JsonProperty("source")
    @Builder.Default
    private final String source = "aws-sqs-consumer";
}