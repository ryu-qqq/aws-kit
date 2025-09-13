package com.ryuqq.aws.sqs.consumer.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DlqMessage(
        /**
         * The original message ID from SQS.
         */
        @JsonProperty("originalMessageId")
        String originalMessageId,

        /**
         * The original message body content.
         */
        @JsonProperty("originalMessage")
        String originalMessage,

        /**
         * The error message that caused the failure.
         */
        @JsonProperty("errorMessage")
        String errorMessage,

        /**
         * The type/class name of the exception that occurred.
         */
        @JsonProperty("errorType")
        String errorType,

        /**
         * Timestamp when the message was sent to the DLQ.
         */
        @JsonProperty("timestamp")
        Instant timestamp,

        /**
         * The container ID that processed the failed message.
         */
        @JsonProperty("containerId")
        String containerId,

        /**
         * The original queue URL where the message was received.
         */
        @JsonProperty("queueUrl")
        String queueUrl,

        /**
         * Number of retry attempts that were made before failure.
         */
        @JsonProperty("retryAttempts")
        Integer retryAttempts,

        /**
         * Original message attributes from SQS.
         */
        @JsonProperty("originalAttributes")
        Map<String, String> originalAttributes,

        /**
         * Additional context information for debugging.
         */
        @JsonProperty("additionalContext")
        Map<String, Object> additionalContext,

        /**
         * Version of the DLQ message format for future compatibility.
         */
        @JsonProperty("version")
        String version,

        /**
         * Source system that generated this DLQ message.
         */
        @JsonProperty("source")
        String source
) {
    /**
     * Static factory method for building DlqMessage with default values.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class to maintain compatibility with the original @Builder pattern.
     */
    public static class Builder {
        private String originalMessageId;
        private String originalMessage;
        private String errorMessage;
        private String errorType;
        private Instant timestamp;
        private String containerId;
        private String queueUrl;
        private Integer retryAttempts;
        private Map<String, String> originalAttributes;
        private Map<String, Object> additionalContext;
        private String version = "1.0";
        private String source = "aws-sqs-consumer";

        public Builder originalMessageId(String originalMessageId) {
            this.originalMessageId = originalMessageId;
            return this;
        }

        public Builder originalMessage(String originalMessage) {
            this.originalMessage = originalMessage;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder errorType(String errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder containerId(String containerId) {
            this.containerId = containerId;
            return this;
        }

        public Builder queueUrl(String queueUrl) {
            this.queueUrl = queueUrl;
            return this;
        }

        public Builder retryAttempts(Integer retryAttempts) {
            this.retryAttempts = retryAttempts;
            return this;
        }

        public Builder originalAttributes(Map<String, String> originalAttributes) {
            this.originalAttributes = originalAttributes;
            return this;
        }

        public Builder additionalContext(Map<String, Object> additionalContext) {
            this.additionalContext = additionalContext;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public DlqMessage build() {
            return new DlqMessage(
                    originalMessageId,
                    originalMessage,
                    errorMessage,
                    errorType,
                    timestamp != null ? timestamp : Instant.now(),
                    containerId,
                    queueUrl,
                    retryAttempts,
                    originalAttributes != null ? Map.copyOf(originalAttributes) : null,
                    additionalContext != null ? Map.copyOf(additionalContext) : null,
                    version,
                    source
            );
        }
    }

    // Getter methods for backward compatibility with existing tests
    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getErrorType() {
        return errorType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getContainerId() {
        return containerId;
    }

    public String getQueueUrl() {
        return queueUrl;
    }

    public Integer getRetryAttempts() {
        return retryAttempts;
    }

    public Map<String, String> getOriginalAttributes() {
        return originalAttributes;
    }

    public Map<String, Object> getAdditionalContext() {
        return additionalContext;
    }

    public String getVersion() {
        return version;
    }

    public String getSource() {
        return source;
    }
}