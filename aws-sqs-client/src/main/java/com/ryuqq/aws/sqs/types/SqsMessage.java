package com.ryuqq.aws.sqs.types;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Library-specific message representation for SQS operations.
 * Replaces AWS SDK Message to avoid exposing SDK types in public API.
 */
public final class SqsMessage {
    
    private final String messageId;
    private final String body;
    private final String receiptHandle;
    private final String md5OfBody;
    private final String md5OfMessageAttributes;
    private final Map<String, String> attributes;
    private final Map<String, SqsMessageAttribute> messageAttributes;
    private final Instant timestamp;
    
    private SqsMessage(Builder builder) {
        this.messageId = builder.messageId;
        this.body = builder.body;
        this.receiptHandle = builder.receiptHandle;
        this.md5OfBody = builder.md5OfBody;
        this.md5OfMessageAttributes = builder.md5OfMessageAttributes;
        this.attributes = builder.attributes != null ? Map.copyOf(builder.attributes) : Map.of();
        this.messageAttributes = builder.messageAttributes != null ? Map.copyOf(builder.messageAttributes) : Map.of();
        this.timestamp = builder.timestamp;
    }
    
    public String getMessageId() {
        return messageId;
    }
    
    public String getBody() {
        return body;
    }
    
    public String getReceiptHandle() {
        return receiptHandle;
    }
    
    public String getMd5OfBody() {
        return md5OfBody;
    }
    
    public String getMd5OfMessageAttributes() {
        return md5OfMessageAttributes;
    }
    
    public Map<String, String> getAttributes() {
        return attributes;
    }
    
    public Map<String, SqsMessageAttribute> getMessageAttributes() {
        return messageAttributes;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    public String getAttribute(String attributeName) {
        return attributes.get(attributeName);
    }
    
    public SqsMessageAttribute getMessageAttribute(String attributeName) {
        return messageAttributes.get(attributeName);
    }
    
    public String getApproximateReceiveCount() {
        return attributes.get("ApproximateReceiveCount");
    }
    
    public String getApproximateFirstReceiveTimestamp() {
        return attributes.get("ApproximateFirstReceiveTimestamp");
    }
    
    public String getSentTimestamp() {
        return attributes.get("SentTimestamp");
    }
    
    public String getSenderId() {
        return attributes.get("SenderId");
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String messageId;
        private String body;
        private String receiptHandle;
        private String md5OfBody;
        private String md5OfMessageAttributes;
        private Map<String, String> attributes;
        private Map<String, SqsMessageAttribute> messageAttributes;
        private Instant timestamp;
        
        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }
        
        public Builder body(String body) {
            this.body = body;
            return this;
        }
        
        public Builder receiptHandle(String receiptHandle) {
            this.receiptHandle = receiptHandle;
            return this;
        }
        
        public Builder md5OfBody(String md5OfBody) {
            this.md5OfBody = md5OfBody;
            return this;
        }
        
        public Builder md5OfMessageAttributes(String md5OfMessageAttributes) {
            this.md5OfMessageAttributes = md5OfMessageAttributes;
            return this;
        }
        
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }
        
        public Builder messageAttributes(Map<String, SqsMessageAttribute> messageAttributes) {
            this.messageAttributes = messageAttributes;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public SqsMessage build() {
            return new SqsMessage(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqsMessage that = (SqsMessage) o;
        return Objects.equals(messageId, that.messageId) &&
               Objects.equals(body, that.body) &&
               Objects.equals(receiptHandle, that.receiptHandle) &&
               Objects.equals(md5OfBody, that.md5OfBody) &&
               Objects.equals(md5OfMessageAttributes, that.md5OfMessageAttributes) &&
               Objects.equals(attributes, that.attributes) &&
               Objects.equals(messageAttributes, that.messageAttributes) &&
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messageId, body, receiptHandle, md5OfBody, md5OfMessageAttributes, 
                          attributes, messageAttributes, timestamp);
    }
    
    @Override
    public String toString() {
        return "SqsMessage{" +
               "messageId='" + messageId + '\'' +
               ", bodyLength=" + (body != null ? body.length() : 0) +
               ", receiptHandle='" + receiptHandle + '\'' +
               ", attributeCount=" + attributes.size() +
               ", messageAttributeCount=" + messageAttributes.size() +
               ", timestamp=" + timestamp +
               '}';
    }
}