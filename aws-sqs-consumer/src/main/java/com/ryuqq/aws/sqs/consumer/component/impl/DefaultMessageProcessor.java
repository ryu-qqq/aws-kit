package com.ryuqq.aws.sqs.consumer.component.impl;

import com.ryuqq.aws.sqs.consumer.component.MessageProcessor;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default implementation of MessageProcessor.
 * Handles both single message and batch processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMessageProcessor implements MessageProcessor {
    
    private final SqsService sqsService;
    
    @Override
    public void processMessage(SqsMessage message, ProcessingConfig config) throws MessageProcessingException {
        try {
            log.debug("Processing message {} for container", message.getMessageId());
            
            invokeTargetMethod(message, config);
            
            // Auto-delete message if configured
            if (config.isAutoDelete()) {
                deleteMessage(config.getQueueUrl(), message.getReceiptHandle());
            }
            
        } catch (Exception e) {
            throw new MessageProcessingException("Failed to process message: " + message.getMessageId(), e);
        }
    }
    
    @Override
    public void processBatch(List<SqsMessage> messages, ProcessingConfig config) throws MessageProcessingException {
        try {
            log.debug("Processing batch of {} messages", messages.size());
            
            invokeTargetMethod(messages, config);
            
            // Auto-delete messages if configured
            if (config.isAutoDelete()) {
                List<String> receiptHandles = messages.stream()
                        .map(SqsMessage::getReceiptHandle)
                        .toList();
                
                deleteMessageBatch(config.getQueueUrl(), receiptHandles);
            }
            
        } catch (Exception e) {
            throw new MessageProcessingException("Failed to process message batch", e);
        }
    }
    
    @Override
    public boolean supportsBatchProcessing() {
        return true;
    }
    
    private void invokeTargetMethod(Object messageParameter, ProcessingConfig config) throws Exception {
        try {
            config.getTargetMethod().invoke(config.getTargetBean(), messageParameter);
        } catch (Exception e) {
            log.error("Error invoking target method: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    private void deleteMessage(String queueUrl, String receiptHandle) {
        sqsService.deleteMessage(queueUrl, receiptHandle)
                .exceptionally(throwable -> {
                    log.warn("Failed to delete message with receipt handle {}: {}", 
                            receiptHandle, throwable.getMessage());
                    return null;
                });
    }
    
    private void deleteMessageBatch(String queueUrl, List<String> receiptHandles) {
        sqsService.deleteMessageBatch(queueUrl, receiptHandles)
                .exceptionally(throwable -> {
                    log.warn("Failed to delete message batch: {}", throwable.getMessage());
                    return null;
                });
    }
}