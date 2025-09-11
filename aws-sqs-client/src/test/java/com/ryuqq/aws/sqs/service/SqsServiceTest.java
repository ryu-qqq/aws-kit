package com.ryuqq.aws.sqs.service;

import com.ryuqq.aws.sqs.properties.SqsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;
import com.ryuqq.aws.sqs.types.SqsMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SqsServiceTest {

    @Mock
    private SqsAsyncClient sqsAsyncClient;

    private SqsService sqsService;

    private static final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
    private static final String QUEUE_NAME = "test-queue";
    private static final String MESSAGE_BODY = "test message";
    private static final String MESSAGE_ID = "12345";
    private static final String RECEIPT_HANDLE = "receipt-handle";

    @BeforeEach
    void setUp() {
        SqsProperties sqsProperties = new SqsProperties();
        sqsService = new SqsService(sqsAsyncClient, sqsProperties);
    }

    @Test
    void sendMessage_성공() {
        // Given
        SendMessageResponse response = SendMessageResponse.builder()
                .messageId(MESSAGE_ID)
                .build();
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = sqsService.sendMessage(QUEUE_URL, MESSAGE_BODY);

        // Then
        assertThat(result.join()).isEqualTo(MESSAGE_ID);
        verify(sqsAsyncClient).sendMessage(any(SendMessageRequest.class));
    }

    @Test
    void sendMessage_실패() {
        // Given
        when(sqsAsyncClient.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("AWS Error")));

        // When & Then
        CompletableFuture<String> result = sqsService.sendMessage(QUEUE_URL, MESSAGE_BODY);
        
        assertThatThrownBy(() -> result.join())
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to send message");
    }

    @Test
    void sendMessageBatch_성공() {
        // Given
        List<String> messages = Arrays.asList("msg1", "msg2", "msg3");
        SendMessageBatchResponse response = SendMessageBatchResponse.builder()
                .successful(
                        SendMessageBatchResultEntry.builder().id("0").messageId("id1").build(),
                        SendMessageBatchResultEntry.builder().id("1").messageId("id2").build(),
                        SendMessageBatchResultEntry.builder().id("2").messageId("id3").build()
                )
                .build();
        
        when(sqsAsyncClient.sendMessageBatch(any(SendMessageBatchRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<String>> result = sqsService.sendMessageBatch(QUEUE_URL, messages);

        // Then
        List<String> messageIds = result.join();
        assertThat(messageIds).containsExactly("id1", "id2", "id3");
    }

    @Test
    void sendMessageBatch_배치크기초과() {
        // Given
        List<String> messages = Arrays.asList("msg1", "msg2", "msg3", "msg4", "msg5", 
                "msg6", "msg7", "msg8", "msg9", "msg10", "msg11");

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> 
                sqsService.sendMessageBatch(QUEUE_URL, messages));
    }

    @Test
    void receiveMessages_성공() {
        // Given
        Message message = Message.builder()
                .messageId(MESSAGE_ID)
                .receiptHandle(RECEIPT_HANDLE)
                .body(MESSAGE_BODY)
                .build();
        
        ReceiveMessageResponse response = ReceiveMessageResponse.builder()
                .messages(message)
                .build();
        
        when(sqsAsyncClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<List<SqsMessage>> result = sqsService.receiveMessages(QUEUE_URL, 5);

        // Then
        List<SqsMessage> messages = result.join();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getMessageId()).isEqualTo(MESSAGE_ID);
        
        verify(sqsAsyncClient).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void deleteMessage_성공() {
        // Given
        DeleteMessageResponse response = DeleteMessageResponse.builder().build();
        when(sqsAsyncClient.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = sqsService.deleteMessage(QUEUE_URL, RECEIPT_HANDLE);

        // Then
        assertThat(result.join()).isNull();
        verify(sqsAsyncClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void deleteMessageBatch_성공() {
        // Given
        List<String> receiptHandles = Arrays.asList("handle1", "handle2", "handle3");
        DeleteMessageBatchResponse response = DeleteMessageBatchResponse.builder()
                .successful(
                        DeleteMessageBatchResultEntry.builder().id("0").build(),
                        DeleteMessageBatchResultEntry.builder().id("1").build(),
                        DeleteMessageBatchResultEntry.builder().id("2").build()
                )
                .build();
        
        when(sqsAsyncClient.deleteMessageBatch(any(DeleteMessageBatchRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<Void> result = sqsService.deleteMessageBatch(QUEUE_URL, receiptHandles);

        // Then
        assertThat(result.join()).isNull();
    }

    @Test
    void createQueue_성공() {
        // Given
        Map<String, String> attributes = Map.of("VisibilityTimeout", "30");
        CreateQueueResponse response = CreateQueueResponse.builder()
                .queueUrl(QUEUE_URL)
                .build();
        
        when(sqsAsyncClient.createQueue(any(CreateQueueRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = sqsService.createQueue(QUEUE_NAME, attributes);

        // Then
        assertThat(result.join()).isEqualTo(QUEUE_URL);
        verify(sqsAsyncClient).createQueue(any(CreateQueueRequest.class));
    }

    @Test
    void getQueueUrl_성공() {
        // Given
        GetQueueUrlResponse response = GetQueueUrlResponse.builder()
                .queueUrl(QUEUE_URL)
                .build();
        
        when(sqsAsyncClient.getQueueUrl(any(GetQueueUrlRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = sqsService.getQueueUrl(QUEUE_NAME);

        // Then
        assertThat(result.join()).isEqualTo(QUEUE_URL);
        verify(sqsAsyncClient).getQueueUrl(any(GetQueueUrlRequest.class));
    }
}