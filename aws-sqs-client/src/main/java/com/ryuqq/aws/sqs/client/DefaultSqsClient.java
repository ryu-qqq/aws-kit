package com.ryuqq.aws.sqs.client;

import com.ryuqq.aws.commons.exception.AwsServiceException;
import com.ryuqq.aws.commons.metrics.AwsMetricsProvider;
import com.ryuqq.aws.sqs.model.SqsMessage;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * SQS 클라이언트 기본 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSqsClient implements SqsClient {

    private static final String SERVICE_NAME = "sqs";
    private final SqsAsyncClient sqsAsyncClient;
    private final AwsMetricsProvider metricsProvider;

    @Override
    public CompletableFuture<String> sendMessage(String queueUrl, String messageBody) {
        return sendMessage(queueUrl, messageBody, Collections.emptyMap());
    }

    @Override
    public CompletableFuture<String> sendMessage(String queueUrl, String messageBody, 
                                               Map<String, String> messageAttributes) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "sendMessage");
        
        SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody);

        if (!messageAttributes.isEmpty()) {
            Map<String, MessageAttributeValue> attributes = messageAttributes.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(entry.getValue())
                                    .build()
                    ));
            requestBuilder.messageAttributes(attributes);
        }

        return sqsAsyncClient.sendMessage(requestBuilder.build())
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "sendMessage", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "sendMessage");
                    log.debug("Message sent successfully. MessageId: {}", response.messageId());
                    return response.messageId();
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "sendMessage", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "sendMessage", errorCode);
                    log.error("Failed to send message to queue: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to send message", throwable);
                });
    }

    @Override
    public CompletableFuture<List<String>> sendMessages(String queueUrl, List<String> messageBodies) {
        if (messageBodies.size() > 10) {
            throw new IllegalArgumentException("Batch size cannot exceed 10 messages");
        }

        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "sendMessageBatch");

        List<SendMessageBatchRequestEntry> entries = IntStream.range(0, messageBodies.size())
                .mapToObj(i -> SendMessageBatchRequestEntry.builder()
                        .id(String.valueOf(i))
                        .messageBody(messageBodies.get(i))
                        .build())
                .collect(Collectors.toList());

        SendMessageBatchRequest request = SendMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(entries)
                .build();

        return sqsAsyncClient.sendMessageBatch(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "sendMessageBatch", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "sendMessageBatch");
                    
                    if (!response.failed().isEmpty()) {
                        log.warn("Some messages failed to send: {}", response.failed());
                    }
                    
                    return response.successful().stream()
                            .map(SendMessageBatchResultEntry::messageId)
                            .collect(Collectors.toList());
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "sendMessageBatch", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "sendMessageBatch", errorCode);
                    log.error("Failed to send batch messages to queue: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to send batch messages", throwable);
                });
    }

    @Override
    public CompletableFuture<String> sendFifoMessage(String queueUrl, String messageBody, 
                                                   String messageGroupId, String messageDeduplicationId) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "sendFifoMessage");

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .messageGroupId(messageGroupId)
                .messageDeduplicationId(messageDeduplicationId)
                .build();

        return sqsAsyncClient.sendMessage(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "sendFifoMessage", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "sendFifoMessage");
                    log.debug("FIFO message sent successfully. MessageId: {}", response.messageId());
                    return response.messageId();
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "sendFifoMessage", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "sendFifoMessage", errorCode);
                    log.error("Failed to send FIFO message to queue: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to send FIFO message", throwable);
                });
    }

    @Override
    public CompletableFuture<SqsMessage> receiveMessage(String queueUrl) {
        return receiveMessages(queueUrl, 1)
                .thenApply(messages -> messages.isEmpty() ? null : messages.getFirst());
    }

    @Override
    public CompletableFuture<List<SqsMessage>> receiveMessages(String queueUrl, int maxMessages) {
        return receiveMessagesWithLongPolling(queueUrl, maxMessages, 0);
    }

    @Override
    public CompletableFuture<List<SqsMessage>> receiveMessagesWithLongPolling(String queueUrl, 
                                                                            int maxMessages, 
                                                                            int waitTimeSeconds) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "receiveMessage");

        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(Math.min(maxMessages, 10))
                .waitTimeSeconds(waitTimeSeconds)
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames("All")
                .build();

        return sqsAsyncClient.receiveMessage(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "receiveMessage", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "receiveMessage");
                    
                    List<SqsMessage> messages = response.messages().stream()
                            .map(this::convertToSqsMessage)
                            .collect(Collectors.toList());
                    
                    log.debug("Received {} messages from queue: {}", messages.size(), queueUrl);
                    return messages;
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "receiveMessage", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "receiveMessage", errorCode);
                    log.error("Failed to receive messages from queue: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to receive messages", throwable);
                });
    }

    @Override
    public CompletableFuture<Void> deleteMessage(String queueUrl, String receiptHandle) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "deleteMessage");

        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();

        return sqsAsyncClient.deleteMessage(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "deleteMessage", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "deleteMessage");
                    log.debug("Message deleted successfully from queue: {}", queueUrl);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "deleteMessage", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "deleteMessage", errorCode);
                    log.error("Failed to delete message from queue: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to delete message", throwable);
                });
    }

    @Override
    public CompletableFuture<Void> deleteMessages(String queueUrl, List<String> receiptHandles) {
        if (receiptHandles.size() > 10) {
            throw new IllegalArgumentException("Batch size cannot exceed 10 messages");
        }

        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "deleteMessageBatch");

        List<DeleteMessageBatchRequestEntry> entries = IntStream.range(0, receiptHandles.size())
                .mapToObj(i -> DeleteMessageBatchRequestEntry.builder()
                        .id(String.valueOf(i))
                        .receiptHandle(receiptHandles.get(i))
                        .build())
                .collect(Collectors.toList());

        DeleteMessageBatchRequest request = DeleteMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(entries)
                .build();

        return sqsAsyncClient.deleteMessageBatch(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "deleteMessageBatch", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "deleteMessageBatch");
                    
                    if (!response.failed().isEmpty()) {
                        log.warn("Some messages failed to delete: {}", response.failed());
                    }
                    
                    log.debug("Batch deleted {} messages from queue: {}", 
                            response.successful().size(), queueUrl);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "deleteMessageBatch", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "deleteMessageBatch", errorCode);
                    log.error("Failed to delete batch messages from queue: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to delete batch messages", throwable);
                });
    }

    @Override
    public CompletableFuture<Void> changeMessageVisibility(String queueUrl, String receiptHandle, 
                                                          int visibilityTimeoutSeconds) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "changeMessageVisibility");

        ChangeMessageVisibilityRequest request = ChangeMessageVisibilityRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .visibilityTimeout(visibilityTimeoutSeconds)
                .build();

        return sqsAsyncClient.changeMessageVisibility(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "changeMessageVisibility", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "changeMessageVisibility");
                    log.debug("Message visibility changed successfully for queue: {}", queueUrl);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "changeMessageVisibility", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "changeMessageVisibility", errorCode);
                    log.error("Failed to change message visibility for queue: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to change message visibility", throwable);
                });
    }

    @Override
    public CompletableFuture<String> getQueueUrl(String queueName) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "getQueueUrl");

        GetQueueUrlRequest request = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();

        return sqsAsyncClient.getQueueUrl(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getQueueUrl", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "getQueueUrl");
                    log.debug("Queue URL retrieved: {}", response.queueUrl());
                    return response.queueUrl();
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getQueueUrl", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "getQueueUrl", errorCode);
                    log.error("Failed to get queue URL for: {}", queueName, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to get queue URL", throwable);
                });
    }

    @Override
    public CompletableFuture<Map<String, String>> getQueueAttributes(String queueUrl) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "getQueueAttributes");

        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.ALL)
                .build();

        return sqsAsyncClient.getQueueAttributes(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getQueueAttributes", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "getQueueAttributes");
                    return response.attributesAsStrings();
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "getQueueAttributes", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "getQueueAttributes", errorCode);
                    log.error("Failed to get queue attributes for: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to get queue attributes", throwable);
                });
    }

    @Override
    public CompletableFuture<String> createQueue(String queueName, Map<String, String> attributes) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "createQueue");

        // Convert String attributes to QueueAttributeName enum
        Map<QueueAttributeName, String> enumAttributes = new HashMap<>();
        attributes.forEach((key, value) -> {
            try {
                QueueAttributeName attributeName = QueueAttributeName.fromValue(key);
                enumAttributes.put(attributeName, value);
            } catch (Exception e) {
                log.warn("Unknown queue attribute: {}, skipping", key);
            }
        });
        
        CreateQueueRequest request = CreateQueueRequest.builder()
                .queueName(queueName)
                .attributes(enumAttributes)
                .build();

        return sqsAsyncClient.createQueue(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "createQueue", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "createQueue");
                    log.info("Queue created successfully: {}", response.queueUrl());
                    return response.queueUrl();
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "createQueue", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "createQueue", errorCode);
                    log.error("Failed to create queue: {}", queueName, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to create queue", throwable);
                });
    }

    @Override
    public CompletableFuture<Void> deleteQueue(String queueUrl) {
        Timer.Sample sample = metricsProvider.startApiCallTimer(SERVICE_NAME, "deleteQueue");

        DeleteQueueRequest request = DeleteQueueRequest.builder()
                .queueUrl(queueUrl)
                .build();

        return sqsAsyncClient.deleteQueue(request)
                .thenApply(response -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "deleteQueue", true);
                    metricsProvider.incrementSuccessCounter(SERVICE_NAME, "deleteQueue");
                    log.info("Queue deleted successfully: {}", queueUrl);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    metricsProvider.stopApiCallTimer(sample, SERVICE_NAME, "deleteQueue", false);
                    String errorCode = extractErrorCode(throwable);
                    metricsProvider.incrementErrorCounter(SERVICE_NAME, "deleteQueue", errorCode);
                    log.error("Failed to delete queue: {}", queueUrl, throwable);
                    throw new AwsServiceException(SERVICE_NAME, "Failed to delete queue", throwable);
                });
    }

    @Override
    public CompletableFuture<Integer> getApproximateNumberOfMessages(String queueUrl) {
        return getQueueAttributes(queueUrl)
                .thenApply(attributes -> {
                    String count = attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES.toString());
                    return count != null ? Integer.parseInt(count) : 0;
                });
    }

    private SqsMessage convertToSqsMessage(Message message) {
        Map<String, SqsMessage.MessageAttributeValue> messageAttributes = new HashMap<>();
        
        message.messageAttributes().forEach((key, value) -> {
            messageAttributes.put(key, SqsMessage.MessageAttributeValue.builder()
                    .stringValue(value.stringValue())
                    .binaryValue(value.binaryValue() != null ? value.binaryValue().asByteArray() : null)
                    .dataType(value.dataType())
                    .build());
        });

        return SqsMessage.builder()
                .messageId(message.messageId())
                .receiptHandle(message.receiptHandle())
                .body(message.body())
                .attributes(message.attributesAsStrings())
                .messageAttributes(messageAttributes)
                .sentTimestamp(message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP) != null ?
                        Instant.ofEpochMilli(Long.parseLong(message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP))) : null)
                .firstReceiveTimestamp(message.attributes().get(MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP) != null ?
                        Instant.ofEpochMilli(Long.parseLong(message.attributes().get(MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP))) : null)
                .receiveCount(message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT) != null ?
                        Integer.parseInt(message.attributes().get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)) : 0)
                .build();
    }

    private String extractErrorCode(Throwable throwable) {
        if (throwable.getCause() instanceof SqsException sqsException) {
            return sqsException.awsErrorDetails().errorCode();
        }
        return "UNKNOWN_ERROR";
    }
}