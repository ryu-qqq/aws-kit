package com.ryuqq.aws.sqs.consumer.container;

import com.ryuqq.aws.sqs.consumer.annotation.SqsListener;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for SqsListenerContainer.
 */
@ExtendWith(MockitoExtension.class)
class SqsListenerContainerTest {
    
    @Mock
    private SqsService sqsService;
    
    @Mock
    private Environment environment;
    
    @Mock
    private ApplicationContext applicationContext;
    
    private ExecutorService executorService;
    private TestListener testListener;
    private SqsListenerContainer container;
    
    private static final String QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue";
    private static final String QUEUE_NAME = "test-queue";
    
    @BeforeEach
    void setUp() throws Exception {
        executorService = Executors.newFixedThreadPool(2);
        testListener = new TestListener();
        
        when(environment.resolvePlaceholders(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sqsService.getQueueUrl(QUEUE_NAME)).thenReturn(CompletableFuture.completedFuture(QUEUE_URL));
        
        Method method = TestListener.class.getDeclaredMethod("handleMessage", SqsMessage.class);
        SqsListener annotation = method.getAnnotation(SqsListener.class);
        
        container = new SqsListenerContainer(
            "test-container",
            testListener,
            method,
            annotation,
            sqsService,
            environment,
            applicationContext,
            executorService,
            executorService
        );
    }
    
    @AfterEach
    void tearDown() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
    
    @Test
    void start_성공() throws Exception {
        // When
        container.start();
        
        // Then
        assertThat(container.isRunning()).isTrue();
        verify(sqsService).getQueueUrl(QUEUE_NAME);
    }
    
    @Test
    void start_이미실행중() throws Exception {
        // Given
        container.start();
        
        // When
        container.start(); // Start again
        
        // Then
        assertThat(container.isRunning()).isTrue();
        // Should not call getQueueUrl again
        verify(sqsService, times(1)).getQueueUrl(QUEUE_NAME);
    }
    
    @Test
    void stop_성공() throws Exception {
        // Given
        container.start();
        assertThat(container.isRunning()).isTrue();
        
        // When
        container.stop();
        
        // Then
        assertThat(container.isRunning()).isFalse();
    }
    
    @Test
    void stop_실행중이아님() {
        // When
        container.stop();
        
        // Then
        assertThat(container.isRunning()).isFalse();
    }
    
    @Test
    void getStats_초기상태() {
        // When
        SqsListenerContainer.ContainerStats stats = container.getStats();
        
        // Then
        assertThat(stats.getContainerId()).isEqualTo("test-container");
        assertThat(stats.isRunning()).isFalse();
        assertThat(stats.getProcessedMessages()).isZero();
        assertThat(stats.getFailedMessages()).isZero();
    }
    
    @Test
    void containerStatsToString() {
        // Given
        SqsListenerContainer.ContainerStats stats = 
            new SqsListenerContainer.ContainerStats("test", ContainerState.RUNNING, true, 10, 2);
        
        // When
        String statsString = stats.toString();
        
        // Then
        assertThat(statsString).contains("id='test'", "running=true", "processed=10", "failed=2");
    }
    
    @Test
    void messageProcessing_단일메시지() throws Exception {
        // Given
        SqsMessage message = createTestMessage("msg1", "receipt1");
        when(sqsService.receiveMessages(eq(QUEUE_URL), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(List.of(message)))
            .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        
        when(sqsService.deleteMessage(QUEUE_URL, "receipt1"))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // When
        container.start();
        Thread.sleep(100); // Allow some processing time
        container.stop();
        
        // Then
        assertThat(testListener.getProcessedMessages()).hasSize(1);
        assertThat(testListener.getProcessedMessages().get(0).getBody()).isEqualTo("msg1");
        
        verify(sqsService).deleteMessage(QUEUE_URL, "receipt1");
    }
    
    @Test
    void messageProcessing_배치모드() throws Exception {
        // Given
        Method batchMethod = TestListener.class.getDeclaredMethod("handleBatch", List.class);
        SqsListener batchAnnotation = batchMethod.getAnnotation(SqsListener.class);
        
        SqsListenerContainer batchContainer = new SqsListenerContainer(
            "batch-container",
            testListener,
            batchMethod,
            batchAnnotation,
            sqsService,
            environment,
            applicationContext,
            executorService,
            executorService
        );
        
        SqsMessage message1 = createTestMessage("msg1", "receipt1");
        SqsMessage message2 = createTestMessage("msg2", "receipt2");
        
        when(sqsService.receiveMessages(eq(QUEUE_URL), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(Arrays.asList(message1, message2)))
            .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        
        when(sqsService.deleteMessageBatch(eq(QUEUE_URL), anyList()))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        try {
            // When
            batchContainer.start();
            Thread.sleep(100); // Allow some processing time
            batchContainer.stop();
            
            // Then
            assertThat(testListener.getBatchProcessedMessages()).hasSize(1);
            assertThat(testListener.getBatchProcessedMessages().get(0)).hasSize(2);
            
            verify(sqsService).deleteMessageBatch(eq(QUEUE_URL), anyList());
        } finally {
            batchContainer.stop();
        }
    }
    
    @Test
    void messageProcessing_자동삭제비활성화() throws Exception {
        // Given
        Method noDeleteMethod = TestListener.class.getDeclaredMethod("handleMessageNoDelete", SqsMessage.class);
        SqsListener noDeleteAnnotation = noDeleteMethod.getAnnotation(SqsListener.class);
        
        SqsListenerContainer noDeleteContainer = new SqsListenerContainer(
            "no-delete-container",
            testListener,
            noDeleteMethod,
            noDeleteAnnotation,
            sqsService,
            environment,
            applicationContext,
            executorService,
            executorService
        );
        
        SqsMessage message = createTestMessage("msg1", "receipt1");
        when(sqsService.receiveMessages(eq(QUEUE_URL), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(List.of(message)))
            .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        
        try {
            // When
            noDeleteContainer.start();
            Thread.sleep(100); // Allow some processing time
            noDeleteContainer.stop();
            
            // Then
            assertThat(testListener.getProcessedMessages()).hasSize(1);
            
            // Should not call delete methods
            verify(sqsService, never()).deleteMessage(anyString(), anyString());
            verify(sqsService, never()).deleteMessageBatch(anyString(), anyList());
        } finally {
            noDeleteContainer.stop();
        }
    }
    
    @Test
    void messageProcessing_재시도() throws Exception {
        // Given
        Method retryMethod = TestListener.class.getDeclaredMethod("handleMessageWithRetry", SqsMessage.class);
        SqsListener retryAnnotation = retryMethod.getAnnotation(SqsListener.class);
        
        SqsListenerContainer retryContainer = new SqsListenerContainer(
            "retry-container",
            testListener,
            retryMethod,
            retryAnnotation,
            sqsService,
            environment,
            applicationContext,
            executorService,
            executorService
        );
        
        SqsMessage message = createTestMessage("msg1", "receipt1");
        when(sqsService.receiveMessages(eq(QUEUE_URL), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(List.of(message)))
            .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));
        
        when(sqsService.deleteMessage(QUEUE_URL, "receipt1"))
            .thenReturn(CompletableFuture.completedFuture(null));
        
        // Configure listener to fail twice, then succeed
        testListener.setFailureCount(2);
        
        try {
            // When
            retryContainer.start();
            Thread.sleep(500); // Allow retry processing time
            retryContainer.stop();
            
            // Then
            assertThat(testListener.getProcessedMessages()).hasSize(1);
            assertThat(testListener.getRetryCount()).isEqualTo(3); // Initial + 2 retries
            
            verify(sqsService).deleteMessage(QUEUE_URL, "receipt1");
        } finally {
            retryContainer.stop();
        }
    }
    
    @Test
    void queueUrl직접사용() throws Exception {
        // Given
        Method directUrlMethod = TestListener.class.getDeclaredMethod("handleMessageDirectUrl", SqsMessage.class);
        SqsListener directUrlAnnotation = directUrlMethod.getAnnotation(SqsListener.class);
        
        SqsListenerContainer directUrlContainer = new SqsListenerContainer(
            "direct-url-container",
            testListener,
            directUrlMethod,
            directUrlAnnotation,
            sqsService,
            environment,
            applicationContext,
            executorService,
            executorService
        );
        
        // When
        directUrlContainer.start();
        
        // Then
        assertThat(directUrlContainer.isRunning()).isTrue();
        
        // Should not call getQueueUrl since direct URL is used
        verify(sqsService, never()).getQueueUrl(anyString());
        
        directUrlContainer.stop();
    }
    
    private SqsMessage createTestMessage(String body, String receiptHandle) {
        return SqsMessage.builder()
            .messageId("id-" + body)
            .body(body)
            .receiptHandle(receiptHandle)
            .build();
    }
    
    // Test listener class
    static class TestListener {
        private final List<SqsMessage> processedMessages = Collections.synchronizedList(new java.util.ArrayList<>());
        private final List<List<SqsMessage>> batchProcessedMessages = Collections.synchronizedList(new java.util.ArrayList<>());
        private volatile int failureCount = 0;
        private volatile int retryCount = 0;
        
        @SqsListener(queueName = "test-queue")
        public void handleMessage(SqsMessage message) {
            processedMessages.add(message);
        }
        
        @SqsListener(queueName = "test-queue", batchMode = true)
        public void handleBatch(List<SqsMessage> messages) {
            batchProcessedMessages.add(messages);
        }
        
        @SqsListener(queueName = "test-queue", autoDelete = false)
        public void handleMessageNoDelete(SqsMessage message) {
            processedMessages.add(message);
        }
        
        @SqsListener(queueName = "test-queue", maxRetryAttempts = 2, retryDelayMillis = 50)
        public void handleMessageWithRetry(SqsMessage message) {
            retryCount++;
            if (failureCount > 0) {
                failureCount--;
                throw new RuntimeException("Simulated failure");
            }
            processedMessages.add(message);
        }
        
        @SqsListener(queueUrl = QUEUE_URL)
        public void handleMessageDirectUrl(SqsMessage message) {
            processedMessages.add(message);
        }
        
        public List<SqsMessage> getProcessedMessages() {
            return processedMessages;
        }
        
        public List<List<SqsMessage>> getBatchProcessedMessages() {
            return batchProcessedMessages;
        }
        
        public void setFailureCount(int count) {
            this.failureCount = count;
        }
        
        public int getRetryCount() {
            return retryCount;
        }
    }
}