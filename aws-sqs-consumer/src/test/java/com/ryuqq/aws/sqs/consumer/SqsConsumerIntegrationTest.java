package com.ryuqq.aws.sqs.consumer;

import com.ryuqq.aws.sqs.consumer.annotation.SqsListener;
import com.ryuqq.aws.sqs.consumer.registry.SqsListenerContainerRegistry;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestPropertySource;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Integration test for SQS Consumer functionality.
 */
@SpringBootTest(classes = AwsSqsConsumerAutoConfiguration.class)
@Import(SqsConsumerIntegrationTest.TestListeners.class)
@TestPropertySource(properties = {
    "aws.sqs.consumer.enabled=true",
    "aws.sqs.consumer.default-poll-timeout-seconds=1",
    "app.queue.name=integration-test-queue",
    "app.batch-queue.name=integration-batch-queue"
})
class SqsConsumerIntegrationTest {
    
    @MockBean
    private SqsService sqsService;
    
    @Autowired
    private SqsListenerContainerRegistry containerRegistry;
    
    @Autowired
    private TestListeners testListeners;
    
    private static final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
    private static final String BATCH_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/batch-queue";
    
    @Test
    void fullIntegration_단일메시지처리() throws Exception {
        // Given
        SqsMessage testMessage = SqsMessage.builder()
            .messageId("test-msg-1")
            .body("Hello World")
            .receiptHandle("receipt-handle-1")
            .build();
        
        when(sqsService.getQueueUrl("integration-test-queue"))
            .thenReturn(CompletableFuture.completedFuture(QUEUE_URL));
        
        when(sqsService.receiveMessages(eq(QUEUE_URL), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(List.of(testMessage)))
            .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        
        when(sqsService.deleteMessage(QUEUE_URL, "receipt-handle-1"))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When
        containerRegistry.start();
        
        // Wait for message processing
        boolean messageProcessed = testListeners.getSingleMessageLatch().await(5, TimeUnit.SECONDS);
        
        containerRegistry.stop();
        
        // Then
        assertThat(messageProcessed).isTrue();
        assertThat(testListeners.getReceivedMessages()).hasSize(1);
        assertThat(testListeners.getReceivedMessages().get(0).getBody()).isEqualTo("Hello World");
    }
    
    @Test
    void fullIntegration_배치메시지처리() throws Exception {
        // Given
        SqsMessage message1 = SqsMessage.builder()
            .messageId("batch-msg-1")
            .body("Message 1")
            .receiptHandle("receipt-1")
            .build();
        
        SqsMessage message2 = SqsMessage.builder()
            .messageId("batch-msg-2")
            .body("Message 2")
            .receiptHandle("receipt-2")
            .build();
        
        when(sqsService.getQueueUrl("integration-batch-queue"))
            .thenReturn(CompletableFuture.completedFuture(BATCH_QUEUE_URL));
        
        when(sqsService.receiveMessages(eq(BATCH_QUEUE_URL), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(List.of(message1, message2)))
            .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        
        when(sqsService.deleteMessageBatch(eq(BATCH_QUEUE_URL), anyList()))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When
        containerRegistry.start();
        
        // Wait for batch processing
        boolean batchProcessed = testListeners.getBatchMessageLatch().await(5, TimeUnit.SECONDS);
        
        containerRegistry.stop();
        
        // Then
        assertThat(batchProcessed).isTrue();
        assertThat(testListeners.getReceivedBatches()).hasSize(1);
        assertThat(testListeners.getReceivedBatches().get(0)).hasSize(2);
    }
    
    @Test
    void containerRegistry_통계() throws Exception {
        // When
        SqsListenerContainerRegistry.RegistryStats stats = containerRegistry.getStats();
        
        // Then
        assertThat(stats.getTotalContainers()).isEqualTo(2); // single + batch listener
        assertThat(stats.getRunningContainers()).isZero(); // Not started yet
    }
    
    @Test
    void containerRegistry_개별컨테이너조회() {
        // When
        var singleContainer = containerRegistry.getContainer("testListeners.handleSingleMessage");
        var batchContainer = containerRegistry.getContainer("testListeners.handleBatchMessages");
        
        // Then
        assertThat(singleContainer).isNotNull();
        assertThat(batchContainer).isNotNull();
    }
    
    @Component
    static class TestListeners {
        private final List<SqsMessage> receivedMessages = Collections.synchronizedList(new java.util.ArrayList<>());
        private final List<List<SqsMessage>> receivedBatches = Collections.synchronizedList(new java.util.ArrayList<>());
        private final CountDownLatch singleMessageLatch = new CountDownLatch(1);
        private final CountDownLatch batchMessageLatch = new CountDownLatch(1);
        
        @SqsListener(queueName = "${app.queue.name}")
        public void handleSingleMessage(SqsMessage message) {
            receivedMessages.add(message);
            singleMessageLatch.countDown();
        }
        
        @SqsListener(queueName = "${app.batch-queue.name}", batchMode = true)
        public void handleBatchMessages(List<SqsMessage> messages) {
            receivedBatches.add(messages);
            batchMessageLatch.countDown();
        }
        
        public List<SqsMessage> getReceivedMessages() {
            return receivedMessages;
        }
        
        public List<List<SqsMessage>> getReceivedBatches() {
            return receivedBatches;
        }
        
        public CountDownLatch getSingleMessageLatch() {
            return singleMessageLatch;
        }
        
        public CountDownLatch getBatchMessageLatch() {
            return batchMessageLatch;
        }
    }
}