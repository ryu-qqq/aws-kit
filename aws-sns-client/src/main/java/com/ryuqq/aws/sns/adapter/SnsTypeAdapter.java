package com.ryuqq.aws.sns.adapter;

import com.ryuqq.aws.sns.types.SnsMessage;
import com.ryuqq.aws.sns.types.SnsPublishResult;
import com.ryuqq.aws.sns.types.SnsSubscription;
import com.ryuqq.aws.sns.types.SnsTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.model.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Type adapter for converting between AWS SDK types and abstracted types
 */
@Component
public class SnsTypeAdapter {

    private static final Logger log = LoggerFactory.getLogger(SnsTypeAdapter.class);
    
    /**
     * Convert SnsMessage to PublishRequest
     */
    public PublishRequest toPublishRequest(String topicArn, SnsMessage message) {
        PublishRequest.Builder builder = PublishRequest.builder();
        
        // Set destination
        if (topicArn != null) {
            builder.topicArn(topicArn);
        } else if (message.getTargetArn() != null) {
            builder.targetArn(message.getTargetArn());
        } else if (message.getPhoneNumber() != null) {
            builder.phoneNumber(message.getPhoneNumber());
        }
        
        // Set message content
        builder.message(message.getBody());
        
        if (message.getSubject() != null) {
            builder.subject(message.getSubject());
        }
        
        if (message.getStructure() != null) {
            builder.messageStructure(message.getStructure().getValue());
        }
        
        // Set message attributes
        if (!message.getAttributes().isEmpty()) {
            Map<String, MessageAttributeValue> attrs = message.getAttributes().entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(e.getValue())
                                    .build()
                    ));
            builder.messageAttributes(attrs);
        }
        
        // FIFO topic attributes
        if (message.getMessageGroupId() != null) {
            builder.messageGroupId(message.getMessageGroupId());
        }
        
        if (message.getMessageDeduplicationId() != null) {
            builder.messageDeduplicationId(message.getMessageDeduplicationId());
        }
        
        return builder.build();
    }
    
    /**
     * Convert messages to PublishBatchRequest
     */
    public PublishBatchRequest toPublishBatchRequest(String topicArn, List<SnsMessage> messages) {
        List<PublishBatchRequestEntry> entries = messages.stream()
                .map(message -> {
                    PublishBatchRequestEntry.Builder entryBuilder = PublishBatchRequestEntry.builder()
                            .id(UUID.randomUUID().toString())
                            .message(message.getBody());
                    
                    if (message.getSubject() != null) {
                        entryBuilder.subject(message.getSubject());
                    }
                    
                    if (message.getStructure() != null) {
                        entryBuilder.messageStructure(message.getStructure().getValue());
                    }
                    
                    if (!message.getAttributes().isEmpty()) {
                        Map<String, MessageAttributeValue> attrs = message.getAttributes().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> MessageAttributeValue.builder()
                                                .dataType("String")
                                                .stringValue(e.getValue())
                                                .build()
                                ));
                        entryBuilder.messageAttributes(attrs);
                    }
                    
                    if (message.getMessageGroupId() != null) {
                        entryBuilder.messageGroupId(message.getMessageGroupId());
                    }
                    
                    if (message.getMessageDeduplicationId() != null) {
                        entryBuilder.messageDeduplicationId(message.getMessageDeduplicationId());
                    }
                    
                    return entryBuilder.build();
                })
                .collect(Collectors.toList());
        
        return PublishBatchRequest.builder()
                .topicArn(topicArn)
                .publishBatchRequestEntries(entries)
                .build();
    }
    
    /**
     * Convert PublishResponse to SnsPublishResult
     */
    public SnsPublishResult toPublishResult(PublishResponse response) {
        return SnsPublishResult.of(response.messageId(), response.sequenceNumber());
    }
    
    /**
     * Convert PublishBatchResultEntry to SnsPublishResult
     */
    public SnsPublishResult toBatchPublishResult(PublishBatchResultEntry entry) {
        return SnsPublishResult.of(entry.messageId(), entry.sequenceNumber());
    }
    
    /**
     * Convert Topic to SnsTopic
     */
    public SnsTopic toSnsTopic(Topic topic) {
        return SnsTopic.of(topic.topicArn());
    }
    
    /**
     * Convert CreateTopicResponse to SnsTopic
     */
    public SnsTopic toSnsTopic(CreateTopicResponse response) {
        return SnsTopic.of(response.topicArn());
    }
    
    /**
     * Convert Subscription to SnsSubscription
     */
    public SnsSubscription toSnsSubscription(Subscription subscription) {
        return SnsSubscription.of(
                subscription.subscriptionArn(),
                subscription.topicArn(),
                subscription.protocol(),
                subscription.endpoint(),
                subscription.owner()
        );
    }
    
    /**
     * Convert SubscribeResponse to SnsSubscription
     */
    public SnsSubscription toSnsSubscription(SubscribeResponse response) {
        return SnsSubscription.of(
                response.subscriptionArn(),
                null,
                null,
                null,
                null
        );
    }
}