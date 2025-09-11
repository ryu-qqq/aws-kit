package com.ryuqq.aws.sqs.adapter;

import com.ryuqq.aws.sqs.types.SqsMessage;
import com.ryuqq.aws.sqs.types.SqsMessageAttribute;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adapter class for converting between library types and AWS SDK types.
 * This class encapsulates all AWS SDK dependencies to keep them out of public APIs.
 */
public final class SqsTypeAdapter {
    
    private SqsTypeAdapter() {
        // Utility class
    }
    
    /**
     * Convert AWS SDK Message to library SqsMessage
     */
    public static SqsMessage fromAwsMessage(Message awsMessage) {
        if (awsMessage == null) {
            return null;
        }
        
        SqsMessage.Builder builder = SqsMessage.builder()
                .messageId(awsMessage.messageId())
                .body(awsMessage.body())
                .receiptHandle(awsMessage.receiptHandle())
                .md5OfBody(awsMessage.md5OfBody())
                .md5OfMessageAttributes(awsMessage.md5OfMessageAttributes())
                .attributes(convertAwsAttributesToStringMap(awsMessage.attributes()))
                .timestamp(Instant.now()); // AWS doesn't provide timestamp directly
        
        if (awsMessage.hasMessageAttributes()) {
            Map<String, SqsMessageAttribute> messageAttributes = new HashMap<>();
            for (Map.Entry<String, MessageAttributeValue> entry : awsMessage.messageAttributes().entrySet()) {
                messageAttributes.put(entry.getKey(), fromAwsMessageAttributeValue(entry.getValue()));
            }
            builder.messageAttributes(messageAttributes);
        }
        
        return builder.build();
    }
    
    /**
     * Convert list of AWS SDK Messages to list of library SqsMessages
     */
    public static List<SqsMessage> fromAwsMessages(List<Message> awsMessages) {
        if (awsMessages == null) {
            return List.of();
        }
        
        return awsMessages.stream()
                .map(SqsTypeAdapter::fromAwsMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * Convert library SqsMessageAttribute to AWS SDK MessageAttributeValue
     */
    public static MessageAttributeValue toAwsMessageAttributeValue(SqsMessageAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        
        MessageAttributeValue.Builder builder = MessageAttributeValue.builder()
                .dataType(attribute.getDataType().getValue());
        
        switch (attribute.getDataType()) {
            case STRING:
            case NUMBER:
                builder.stringValue(attribute.getStringValue());
                break;
            case BINARY:
                builder.binaryValue(software.amazon.awssdk.core.SdkBytes.fromByteArray(attribute.getBinaryValue()));
                break;
            default:
                throw new IllegalArgumentException("Unsupported data type: " + attribute.getDataType());
        }
        
        return builder.build();
    }
    
    /**
     * Convert AWS SDK MessageAttributeValue to library SqsMessageAttribute
     */
    public static SqsMessageAttribute fromAwsMessageAttributeValue(MessageAttributeValue awsAttribute) {
        if (awsAttribute == null) {
            return null;
        }
        
        SqsMessageAttribute.DataType dataType = SqsMessageAttribute.DataType.fromValue(awsAttribute.dataType());

        return switch (dataType) {
            case STRING -> SqsMessageAttribute.stringAttribute(awsAttribute.stringValue());
            case NUMBER -> SqsMessageAttribute.numberAttribute(awsAttribute.stringValue());
            case BINARY -> SqsMessageAttribute.binaryAttribute(awsAttribute.binaryValue().asByteArray());
        };
    }
    
    /**
     * Convert map of library SqsMessageAttributes to AWS SDK MessageAttributeValues
     */
    public static Map<String, MessageAttributeValue> toAwsMessageAttributes(Map<String, SqsMessageAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        
        Map<String, MessageAttributeValue> awsAttributes = new HashMap<>();
        for (Map.Entry<String, SqsMessageAttribute> entry : attributes.entrySet()) {
            awsAttributes.put(entry.getKey(), toAwsMessageAttributeValue(entry.getValue()));
        }
        
        return awsAttributes;
    }
    
    /**
     * Convert map of AWS SDK MessageAttributeValues to library SqsMessageAttributes
     */
    public static Map<String, SqsMessageAttribute> fromAwsMessageAttributes(Map<String, MessageAttributeValue> awsAttributes) {
        if (awsAttributes == null || awsAttributes.isEmpty()) {
            return Map.of();
        }
        
        Map<String, SqsMessageAttribute> attributes = new HashMap<>();
        for (Map.Entry<String, MessageAttributeValue> entry : awsAttributes.entrySet()) {
            attributes.put(entry.getKey(), fromAwsMessageAttributeValue(entry.getValue()));
        }
        
        return attributes;
    }
    
    /**
     * Convert AWS SDK MessageSystemAttributeName map to String map
     */
    private static Map<String, String> convertAwsAttributesToStringMap(Map<MessageSystemAttributeName, String> awsAttributes) {
        if (awsAttributes == null || awsAttributes.isEmpty()) {
            return Map.of();
        }
        
        Map<String, String> stringAttributes = new HashMap<>();
        for (Map.Entry<MessageSystemAttributeName, String> entry : awsAttributes.entrySet()) {
            stringAttributes.put(entry.getKey().toString(), entry.getValue());
        }
        
        return stringAttributes;
    }
}