package com.ryuqq.aws.sqs.consumer.component.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqq.aws.sqs.consumer.component.DeadLetterQueueHandler;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of DeadLetterQueueHandler.
 * Manages failed messages and sends them to configured dead letter queues.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDeadLetterQueueHandler implements DeadLetterQueueHandler {
    
    private final SqsService sqsService;
    private final Environment environment;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public boolean sendToDeadLetterQueue(SqsMessage originalMessage, Exception exception, DlqConfig config) {
        if (!isDlqEnabled(config)) {
            log.debug("DLQ is not enabled for container: {}", config.getContainerId());
            return false;
        }
        
        try {
            validateDlqConfig(config);
            
            String dlqName = resolveProperty(config.getDlqName());
            String dlqUrl = sqsService.getQueueUrl(dlqName).get();
            
            String dlqMessageBody = createDlqMessageBody(originalMessage, exception, config);
            
            sqsService.sendMessage(dlqUrl, dlqMessageBody).get();
            
            log.info("Successfully sent failed message {} to DLQ for container {}", 
                    originalMessage.getMessageId(), config.getContainerId());
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to send message to DLQ for container {}: {}", 
                    config.getContainerId(), e.getMessage(), e);
            
            throw new DeadLetterQueueException("Failed to send message to DLQ", e);
        }
    }
    
    @Override
    public boolean isDlqEnabled(DlqConfig config) {
        return config.isEnabled() && 
               config.getDlqName() != null && 
               !config.getDlqName().trim().isEmpty();
    }
    
    @Override
    public void validateDlqConfig(DlqConfig config) throws IllegalArgumentException {
        if (!config.isEnabled()) {
            throw new IllegalArgumentException("DLQ is not enabled");
        }
        
        if (config.getDlqName() == null || config.getDlqName().trim().isEmpty()) {
            throw new IllegalArgumentException("DLQ name cannot be null or empty");
        }
        
        if (config.getContainerId() == null || config.getContainerId().trim().isEmpty()) {
            throw new IllegalArgumentException("Container ID cannot be null or empty");
        }
    }
    
    private String createDlqMessageBody(SqsMessage originalMessage, Exception exception, DlqConfig config) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            
            // Original message information
            dlqMessage.put("originalMessageId", originalMessage.getMessageId());
            dlqMessage.put("originalMessageBody", originalMessage.getBody());
            
            if (config.preserveOriginalAttributes() && originalMessage.getAttributes() != null) {
                dlqMessage.put("originalAttributes", originalMessage.getAttributes());
            }
            
            // Failure information
            dlqMessage.put("errorMessage", exception.getMessage());
            dlqMessage.put("errorType", exception.getClass().getSimpleName());
            dlqMessage.put("failureTimestamp", Instant.now().toString());
            
            // Container information
            dlqMessage.put("containerId", config.getContainerId());
            
            // Additional metadata
            if (config.getAdditionalMetadata() != null && !config.getAdditionalMetadata().isEmpty()) {
                dlqMessage.put("additionalMetadata", config.getAdditionalMetadata());
            }
            
            return objectMapper.writeValueAsString(dlqMessage);
            
        } catch (JsonProcessingException e) {
            log.warn("Failed to create structured DLQ message, falling back to simple format: {}", e.getMessage());
            
            // Fallback to simple string format
            return String.format(
                    "{\"originalMessage\":\"%s\",\"error\":\"%s\",\"timestamp\":\"%s\",\"containerId\":\"%s\"}",
                    escapeJson(originalMessage.getBody()),
                    escapeJson(exception.getMessage()),
                    Instant.now(),
                    config.getContainerId()
            );
        }
    }
    
    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\\\"").replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }
    
    private String resolveProperty(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return environment.resolvePlaceholders(value);
    }
}