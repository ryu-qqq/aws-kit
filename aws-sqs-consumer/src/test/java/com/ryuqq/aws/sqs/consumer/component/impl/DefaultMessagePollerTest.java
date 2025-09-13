package com.ryuqq.aws.sqs.consumer.component.impl;

import com.ryuqq.aws.sqs.consumer.component.MessagePoller;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for DefaultMessagePoller.
 */
@ExtendWith(MockitoExtension.class)
class DefaultMessagePollerTest {
    
    @Mock
    private SqsService sqsService;
    
    private DefaultMessagePoller messagePoller;
    
    @BeforeEach
    void setUp() {
        messagePoller = new DefaultMessagePoller(sqsService);
    }
    
    @AfterEach
    void tearDown() {
        if (messagePoller.isPolling()) {
            messagePoller.stopPolling();
        }
    }
    
    @Test
    void shouldPollMessagesSuccessfully() {
        // Given
        String queueUrl = "https://sqs.region.amazonaws.com/123456789012/test-queue";
        SqsMessage message1 = createTestMessage("msg1", "body1");
        SqsMessage message2 = createTestMessage("msg2", "body2");
        List<SqsMessage> expectedMessages = Arrays.asList(message1, message2);
        
        when(sqsService.receiveMessages(eq(queueUrl), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(expectedMessages));
        
        // When
        List<SqsMessage> actualMessages = messagePoller.pollMessages(queueUrl, 10, 20);
        
        // Then
        assertThat(actualMessages).hasSize(2);
        assertThat(actualMessages).containsExactlyInAnyOrder(message1, message2);
        verify(sqsService).receiveMessages(queueUrl, 10);
    }
    
    @Test
    void shouldReturnEmptyListWhenPollingFails() {
        // Given
        String queueUrl = "https://sqs.region.amazonaws.com/123456789012/test-queue";
        
        when(sqsService.receiveMessages(eq(queueUrl), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Polling failed")));
        
        // When
        List<SqsMessage> actualMessages = messagePoller.pollMessages(queueUrl, 10, 20);
        
        // Then
        assertThat(actualMessages).isEmpty();
    }
    
    @Test
    void shouldStartAndStopPollingSuccessfully() throws InterruptedException {
        // Given
        String queueUrl = "https://sqs.region.amazonaws.com/123456789012/test-queue";
        AtomicInteger handlerCallCount = new AtomicInteger(0);
        CountDownLatch handlerCalled = new CountDownLatch(1);
        
        SqsMessage message = createTestMessage("msg1", "body1");
        List<SqsMessage> messages = List.of(message);
        
        when(sqsService.receiveMessages(eq(queueUrl), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(messages))
                .thenReturn(CompletableFuture.completedFuture(List.of())); // Empty on subsequent calls
        
        MessagePoller.MessageHandler handler = receivedMessages -> {
            handlerCallCount.incrementAndGet();
            handlerCalled.countDown();
        };
        
        // When
        assertThat(messagePoller.isPolling()).isFalse();
        
        messagePoller.startPolling(queueUrl, 10, 20, handler);
        
        // Then
        assertThat(messagePoller.isPolling()).isTrue();
        assertThat(handlerCalled.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(handlerCallCount.get()).isGreaterThan(0);
        
        messagePoller.stopPolling();
        assertThat(messagePoller.isPolling()).isFalse();
        
        verify(sqsService, atLeastOnce()).receiveMessages(queueUrl, 10);
    }
    
    @Test
    void shouldHandlePollingExceptionGracefully() throws InterruptedException {
        // Given
        String queueUrl = "https://sqs.region.amazonaws.com/123456789012/test-queue";
        AtomicInteger handlerCallCount = new AtomicInteger(0);
        CountDownLatch handlerCalled = new CountDownLatch(1);
        
        // First call fails, second succeeds
        when(sqsService.receiveMessages(eq(queueUrl), anyInt()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Polling failed")))
                .thenReturn(CompletableFuture.completedFuture(List.of(createTestMessage("msg1", "body1"))))
                .thenReturn(CompletableFuture.completedFuture(List.of())); // Empty on subsequent calls
        
        MessagePoller.MessageHandler handler = receivedMessages -> {
            handlerCallCount.incrementAndGet();
            handlerCalled.countDown();
        };
        
        // When
        messagePoller.startPolling(queueUrl, 10, 20, handler);
        
        // Then - should recover from error and continue polling
        assertThat(handlerCalled.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(handlerCallCount.get()).isGreaterThan(0);
        
        messagePoller.stopPolling();
    }
    
    @Test
    void shouldNotStartPollingWhenAlreadyPolling() {
        // Given
        String queueUrl = "https://sqs.region.amazonaws.com/123456789012/test-queue";
        
        when(sqsService.receiveMessages(eq(queueUrl), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        MessagePoller.MessageHandler handler = receivedMessages -> {};
        
        // When
        messagePoller.startPolling(queueUrl, 10, 20, handler);
        assertThat(messagePoller.isPolling()).isTrue();
        
        // Try to start again
        messagePoller.startPolling(queueUrl, 10, 20, handler);
        
        // Then
        assertThat(messagePoller.isPolling()).isTrue(); // Still polling
        
        messagePoller.stopPolling();
    }
    
    private SqsMessage createTestMessage(String messageId, String body) {
        return SqsMessage.builder()
                .messageId(messageId)
                .body(body)
                .receiptHandle("receipt-" + messageId)
                .build();
    }
}