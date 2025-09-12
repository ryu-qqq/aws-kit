package com.ryuqq.aws.sqs.consumer.component;

import com.ryuqq.aws.sqs.types.SqsMessage;

/**
 * Interface for handling dead letter queue operations.
 * Manages failed messages that need to be sent to DLQ.
 */
public interface DeadLetterQueueHandler {
    
    /**
     * Send a failed message to the dead letter queue.
     *
     * @param originalMessage the original message that failed processing
     * @param exception the exception that caused the failure
     * @param config DLQ configuration
     * @return true if successfully sent to DLQ, false otherwise
     */
    boolean sendToDeadLetterQueue(SqsMessage originalMessage, Exception exception, DlqConfig config);
    
    /**
     * Check if dead letter queue handling is enabled for the given configuration.
     *
     * @param config DLQ configuration
     * @return true if DLQ is enabled and configured
     */
    boolean isDlqEnabled(DlqConfig config);
    
    /**
     * Validate dead letter queue configuration.
     *
     * @param config DLQ configuration to validate
     * @throws IllegalArgumentException if configuration is invalid
     */
    void validateDlqConfig(DlqConfig config) throws IllegalArgumentException;
    
    /**
     * Configuration for dead letter queue operations.
     */
    interface DlqConfig {
        /**
         * Check if DLQ is enabled.
         */
        boolean isEnabled();
        
        /**
         * Get the dead letter queue name.
         */
        String getDlqName();
        
        /**
         * Get the container ID for logging purposes.
         */
        String getContainerId();
        
        /**
         * Get additional metadata to include in DLQ message.
         */
        default java.util.Map<String, String> getAdditionalMetadata() {
            return java.util.Collections.emptyMap();
        }
        
        /**
         * Check if original message attributes should be preserved.
         */
        default boolean preserveOriginalAttributes() {
            return true;
        }
    }
    
    /**
     * Exception thrown when DLQ operations fail.
     */
    class DeadLetterQueueException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public DeadLetterQueueException(String message) {
            super(message);
        }
        
        public DeadLetterQueueException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}