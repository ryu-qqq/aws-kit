package com.ryuqq.aws.sqs.service;

import com.ryuqq.aws.sqs.properties.SqsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Simplified SQS service - direct AWS SDK usage
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqsService {

    private final SqsAsyncClient sqsAsyncClient;
    private final SqsProperties sqsProperties;

    /**
     * 메시지 전송
     */
    public CompletableFuture<String> sendMessage(String queueUrl, String body) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build();

        return sqsAsyncClient.sendMessage(request)
                .thenApply(SendMessageResponse::messageId);
    }

    /**
     * 배치 메시지 전송
     */
    public CompletableFuture<List<String>> sendMessageBatch(String queueUrl, List<String> messages) {
        if (messages.size() > sqsProperties.getMaxBatchSize()) {
            throw new IllegalArgumentException("Batch size cannot exceed " + sqsProperties.getMaxBatchSize());
        }

        List<SendMessageBatchRequestEntry> entries = IntStream.range(0, messages.size())
                .mapToObj(i -> SendMessageBatchRequestEntry.builder()
                        .id(String.valueOf(i))
                        .messageBody(messages.get(i))
                        .build())
                .collect(Collectors.toList());

        SendMessageBatchRequest request = SendMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(entries)
                .build();

        return sqsAsyncClient.sendMessageBatch(request)
                .thenApply(response -> {
                    if (!response.failed().isEmpty()) {
                        log.warn("Some messages failed to send: {}", response.failed());
                    }
                    return response.successful().stream()
                            .map(SendMessageBatchResultEntry::messageId)
                            .collect(Collectors.toList());
                });
    }

    /**
     * 메시지 수신
     */
    public CompletableFuture<List<Message>> receiveMessages(String queueUrl, int maxMessages) {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(Math.min(maxMessages, sqsProperties.getMaxBatchSize()))
                .waitTimeSeconds(sqsProperties.getLongPollingWaitSeconds())
                .visibilityTimeout(sqsProperties.getVisibilityTimeout())
                .attributeNames(QueueAttributeName.ALL)
                .messageAttributeNames("All")
                .build();

        return sqsAsyncClient.receiveMessage(request)
                .thenApply(ReceiveMessageResponse::messages);
    }

    /**
     * 메시지 삭제
     */
    public CompletableFuture<Void> deleteMessage(String queueUrl, String receiptHandle) {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();

        return sqsAsyncClient.deleteMessage(request)
                .thenRun(() -> log.debug("Message deleted from queue: {}", queueUrl));
    }

    /**
     * 배치 메시지 삭제
     */
    public CompletableFuture<Void> deleteMessageBatch(String queueUrl, List<String> receiptHandles) {
        if (receiptHandles.size() > sqsProperties.getMaxBatchSize()) {
            throw new IllegalArgumentException("Batch size cannot exceed " + sqsProperties.getMaxBatchSize());
        }

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
                .thenRun(() -> log.debug("Batch deleted messages from queue: {}", queueUrl));
    }

    /**
     * 큐 생성
     */
    public CompletableFuture<String> createQueue(String queueName, Map<String, String> attributes) {
        CreateQueueRequest.Builder requestBuilder = CreateQueueRequest.builder()
                .queueName(queueName);
        
        if (attributes != null && !attributes.isEmpty()) {
            // Convert String Map to QueueAttributeName Map
            Map<QueueAttributeName, String> queueAttributes = attributes.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> QueueAttributeName.fromValue(entry.getKey()),
                            Map.Entry::getValue
                    ));
            requestBuilder.attributes(queueAttributes);
        }

        return sqsAsyncClient.createQueue(requestBuilder.build())
                .thenApply(CreateQueueResponse::queueUrl);
    }

    /**
     * 큐 URL 조회
     */
    public CompletableFuture<String> getQueueUrl(String queueName) {
        GetQueueUrlRequest request = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();

        return sqsAsyncClient.getQueueUrl(request)
                .thenApply(GetQueueUrlResponse::queueUrl);
    }
}