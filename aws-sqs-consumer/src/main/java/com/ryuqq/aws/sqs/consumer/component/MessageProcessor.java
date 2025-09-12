package com.ryuqq.aws.sqs.consumer.component;

import com.ryuqq.aws.sqs.types.SqsMessage;

import java.util.List;

/**
 * Interface for processing SQS messages.
 * Handles both single message and batch processing.
 */
public interface MessageProcessor {
    
    /**
     * Process a single SQS message.
     *
     * @param message the message to process
     * @param config processing configuration
     * @throws MessageProcessingException if processing fails
     */
    void processMessage(SqsMessage message, ProcessingConfig config) throws MessageProcessingException;
    
    /**
     * Process a batch of SQS messages.
     *
     * @param messages the messages to process
     * @param config processing configuration
     * @throws MessageProcessingException if processing fails
     */
    void processBatch(List<SqsMessage> messages, ProcessingConfig config) throws MessageProcessingException;
    
    /**
     * Check if batch processing is supported.
     *
     * @return true if batch processing is supported
     */
    boolean supportsBatchProcessing();
    
    /**
     * Configuration for message processing.
     */
    interface ProcessingConfig {
        Object getTargetBean();
        java.lang.reflect.Method getTargetMethod();
        boolean isBatchMode();
        boolean isAutoDelete();
        String getQueueUrl();
    }
    
    /**
     * Exception thrown when message processing fails.
     */
    class MessageProcessingException extends Exception {
        private static final long serialVersionUID = 1L;
        public MessageProcessingException(String message) {
            super(message);
        }
        
        public MessageProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}