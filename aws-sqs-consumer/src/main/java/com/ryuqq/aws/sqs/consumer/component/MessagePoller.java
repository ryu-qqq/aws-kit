package com.ryuqq.aws.sqs.consumer.component;

import com.ryuqq.aws.sqs.types.SqsMessage;

import java.util.List;

/**
 * Interface for SQS message polling operations.
 * Responsible for retrieving messages from SQS queues.
 */
public interface MessagePoller {
    
    /**
     * Poll messages from the specified queue.
     *
     * @param queueUrl the SQS queue URL
     * @param maxMessages maximum number of messages to poll
     * @param pollTimeoutSeconds polling timeout in seconds
     * @return list of received messages
     */
    List<SqsMessage> pollMessages(String queueUrl, int maxMessages, int pollTimeoutSeconds);
    
    /**
     * Start polling for messages asynchronously.
     *
     * @param queueUrl the SQS queue URL
     * @param maxMessages maximum number of messages per poll
     * @param pollTimeoutSeconds polling timeout in seconds
     * @param messageHandler handler for processing received messages
     */
    void startPolling(String queueUrl, int maxMessages, int pollTimeoutSeconds, 
                     MessageHandler messageHandler);
    
    /**
     * Stop the polling process.
     */
    void stopPolling();
    
    /**
     * Check if polling is active.
     *
     * @return true if actively polling
     */
    boolean isPolling();
    
    /**
     * Callback interface for handling polled messages.
     */
    @FunctionalInterface
    interface MessageHandler {
        void handleMessages(List<SqsMessage> messages);
    }
}