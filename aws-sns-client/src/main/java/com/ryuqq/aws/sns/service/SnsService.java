package com.ryuqq.aws.sns.service;

import com.ryuqq.aws.sns.adapter.SnsTypeAdapter;
import com.ryuqq.aws.sns.types.SnsMessage;
import com.ryuqq.aws.sns.types.SnsSubscription;
import com.ryuqq.aws.sns.types.SnsPublishResult;
import com.ryuqq.aws.sns.types.SnsTopic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Async-first SNS service with type abstraction
 */
@Slf4j
@RequiredArgsConstructor
public class SnsService {
    
    private final SnsAsyncClient snsClient;
    private final SnsTypeAdapter typeAdapter;
    
    /**
     * Publish message to topic
     */
    public CompletableFuture<SnsPublishResult> publish(String topicArn, SnsMessage message) {
        PublishRequest request = typeAdapter.toPublishRequest(topicArn, message);
        
        return snsClient.publish(request)
                .thenApply(response -> {
                    log.debug("Published message to topic {} with messageId: {}", 
                             topicArn, response.messageId());
                    return typeAdapter.toPublishResult(response);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to publish message to topic: {}", topicArn, throwable);
                    throw new SnsPublishException("Failed to publish message", throwable);
                });
    }
    
    /**
     * Publish message directly to phone number (SMS)
     */
    public CompletableFuture<SnsPublishResult> publishSms(String phoneNumber, String message) {
        SnsMessage smsMessage = SnsMessage.builder()
                .body(message)
                .phoneNumber(phoneNumber)
                .build();
        
        PublishRequest request = typeAdapter.toPublishRequest(null, smsMessage);
        
        return snsClient.publish(request)
                .thenApply(typeAdapter::toPublishResult)
                .exceptionally(throwable -> {
                    log.error("Failed to send SMS to: {}", phoneNumber, throwable);
                    throw new SnsPublishException("Failed to send SMS", throwable);
                });
    }
    
    /**
     * Batch publish messages to topic (FIFO topics)
     */
    public CompletableFuture<List<SnsPublishResult>> publishBatch(
            String topicArn, List<SnsMessage> messages) {
        
        PublishBatchRequest request = typeAdapter.toPublishBatchRequest(topicArn, messages);
        
        return snsClient.publishBatch(request)
                .thenApply(response -> {
                    log.debug("Published {} messages to topic {}", 
                             response.successful().size(), topicArn);
                    
                    if (!response.failed().isEmpty()) {
                        log.warn("Failed to publish {} messages to topic {}", 
                                response.failed().size(), topicArn);
                    }
                    
                    return response.successful().stream()
                            .map(typeAdapter::toBatchPublishResult)
                            .collect(Collectors.toList());
                })
                .exceptionally(throwable -> {
                    log.error("Failed to publish batch to topic: {}", topicArn, throwable);
                    throw new SnsPublishException("Failed to publish batch", throwable);
                });
    }
    
    /**
     * Create a new SNS topic
     */
    public CompletableFuture<SnsTopic> createTopic(String topicName) {
        CreateTopicRequest request = CreateTopicRequest.builder()
                .name(topicName)
                .build();
        
        return snsClient.createTopic(request)
                .thenApply(response -> {
                    log.info("Created SNS topic: {}", response.topicArn());
                    return typeAdapter.toSnsTopic(response);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to create topic: {}", topicName, throwable);
                    throw new SnsOperationException("Failed to create topic", throwable);
                });
    }
    
    /**
     * Delete an SNS topic
     */
    public CompletableFuture<Void> deleteTopic(String topicArn) {
        DeleteTopicRequest request = DeleteTopicRequest.builder()
                .topicArn(topicArn)
                .build();
        
        return snsClient.deleteTopic(request)
                .thenRun(() -> log.info("Deleted SNS topic: {}", topicArn))
                .exceptionally(throwable -> {
                    log.error("Failed to delete topic: {}", topicArn, throwable);
                    throw new SnsOperationException("Failed to delete topic", throwable);
                });
    }
    
    /**
     * Subscribe to a topic
     */
    public CompletableFuture<SnsSubscription> subscribe(
            String topicArn, String protocol, String endpoint) {
        
        SubscribeRequest request = SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol(protocol)
                .endpoint(endpoint)
                .build();
        
        return snsClient.subscribe(request)
                .thenApply(response -> {
                    log.info("Created subscription {} for topic {}", 
                            response.subscriptionArn(), topicArn);
                    return typeAdapter.toSnsSubscription(response);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to subscribe to topic: {}", topicArn, throwable);
                    throw new SnsOperationException("Failed to subscribe", throwable);
                });
    }
    
    /**
     * Unsubscribe from a topic
     */
    public CompletableFuture<Void> unsubscribe(String subscriptionArn) {
        UnsubscribeRequest request = UnsubscribeRequest.builder()
                .subscriptionArn(subscriptionArn)
                .build();
        
        return snsClient.unsubscribe(request)
                .thenRun(() -> log.info("Unsubscribed: {}", subscriptionArn))
                .exceptionally(throwable -> {
                    log.error("Failed to unsubscribe: {}", subscriptionArn, throwable);
                    throw new SnsOperationException("Failed to unsubscribe", throwable);
                });
    }
    
    /**
     * List all topics
     */
    public CompletableFuture<List<SnsTopic>> listTopics() {
        return snsClient.listTopics()
                .thenApply(response -> 
                    response.topics().stream()
                            .map(typeAdapter::toSnsTopic)
                            .collect(Collectors.toList())
                )
                .exceptionally(throwable -> {
                    log.error("Failed to list topics", throwable);
                    throw new SnsOperationException("Failed to list topics", throwable);
                });
    }
    
    /**
     * List subscriptions for a topic
     */
    public CompletableFuture<List<SnsSubscription>> listSubscriptions(String topicArn) {
        ListSubscriptionsByTopicRequest request = ListSubscriptionsByTopicRequest.builder()
                .topicArn(topicArn)
                .build();
        
        return snsClient.listSubscriptionsByTopic(request)
                .thenApply(response -> 
                    response.subscriptions().stream()
                            .map(typeAdapter::toSnsSubscription)
                            .collect(Collectors.toList())
                )
                .exceptionally(throwable -> {
                    log.error("Failed to list subscriptions for topic: {}", topicArn, throwable);
                    throw new SnsOperationException("Failed to list subscriptions", throwable);
                });
    }
    
    /**
     * Set topic attributes
     */
    public CompletableFuture<Void> setTopicAttributes(
            String topicArn, String attributeName, String attributeValue) {
        
        SetTopicAttributesRequest request = SetTopicAttributesRequest.builder()
                .topicArn(topicArn)
                .attributeName(attributeName)
                .attributeValue(attributeValue)
                .build();
        
        return snsClient.setTopicAttributes(request)
                .thenRun(() -> log.debug("Set attribute {} for topic {}", 
                                        attributeName, topicArn))
                .exceptionally(throwable -> {
                    log.error("Failed to set topic attributes: {}", topicArn, throwable);
                    throw new SnsOperationException("Failed to set attributes", throwable);
                });
    }
    
    // Custom exceptions
    public static class SnsPublishException extends RuntimeException {
        public SnsPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class SnsOperationException extends RuntimeException {
        public SnsOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}