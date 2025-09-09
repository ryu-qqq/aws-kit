package com.ryuqq.aws.sqs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * SQS 메시지 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqsMessage {
    
    private String messageId;
    private String receiptHandle;
    private String body;
    private Map<String, String> attributes;
    private Map<String, MessageAttributeValue> messageAttributes;
    private Instant sentTimestamp;
    private Instant firstReceiveTimestamp;
    private int receiveCount;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageAttributeValue {
        private String stringValue;
        private byte[] binaryValue;
        private String dataType;
    }
}