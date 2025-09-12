package com.ryuqq.aws.sqs.consumer.container;

import com.ryuqq.aws.sqs.consumer.annotation.SqsListener;
import com.ryuqq.aws.sqs.consumer.component.*;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RefactoredSqsListenerContainer.
 */
@ExtendWith(MockitoExtension.class)
class RefactoredSqsListenerContainerTest {
    
    @Mock
    private SqsService sqsService;
    
    @Mock
    private Environment environment;
    
    @Mock
    private MessagePoller messagePoller;
    
    @Mock
    private MessageProcessor messageProcessor;
    
    @Mock
    private RetryManager retryManager;
    
    @Mock
    private DeadLetterQueueHandler dlqHandler;
    
    @Mock
    private MetricsCollector metricsCollector;
    
    @Mock
    private MetricsCollector.ContainerMetrics containerMetrics;
    
    private ExecutorService executorService;
    private TestListener testListener;
    private RefactoredSqsListenerContainer container;
    
    @BeforeEach
    void setUp() throws Exception {
        executorService = Executors.newCachedThreadPool();
        testListener = new TestListener();
        
        when(environment.resolvePlaceholders(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sqsService.getQueueUrl("test-queue")).thenReturn(CompletableFuture.completedFuture("https://sqs.region.amazonaws.com/123456789012/test-queue"));
        when(metricsCollector.getContainerMetrics(anyString())).thenReturn(containerMetrics);
        when(containerMetrics.getProcessedCount()).thenReturn(5L);
        when(containerMetrics.getFailedCount()).thenReturn(1L);
        
        Method listenerMethod = TestListener.class.getDeclaredMethod("handleMessage", SqsMessage.class);
        SqsListener annotation = createSqsListenerAnnotation();
        
        container = new RefactoredSqsListenerContainer(
                "test-container",
                testListener,
                listenerMethod,
                annotation,
                sqsService,
                environment,
                executorService,
                messagePoller,
                messageProcessor,
                retryManager,
                dlqHandler,
                metricsCollector
        );
    }
    
    @AfterEach
    void tearDown() {
        if (container.isRunning()) {
            container.stop();
        }
        executorService.shutdown();
    }
    
    @Test
    void shouldStartContainerSuccessfully() {
        // When
        container.start();
        
        // Then
        assertThat(container.isRunning()).isTrue();
        verify(messagePoller).startPolling(
                eq("https://sqs.region.amazonaws.com/123456789012/test-queue"),
                eq(10),
                eq(20),
                any(MessagePoller.MessageHandler.class)
        );
    }
    
    @Test
    void shouldStopContainerSuccessfully() {
        // Given
        container.start();
        assertThat(container.isRunning()).isTrue();
        
        // When
        container.stop();
        
        // Then
        assertThat(container.isRunning()).isFalse();
        verify(messagePoller).stopPolling();
    }
    
    @Test
    void shouldNotStartWhenAlreadyRunning() {
        // Given
        container.start();
        assertThat(container.isRunning()).isTrue();
        
        // When
        container.start(); // Second start
        
        // Then
        assertThat(container.isRunning()).isTrue();
        verify(messagePoller, times(1)).startPolling(anyString(), anyInt(), anyInt(), any());
    }
    
    @Test
    void shouldHandleStartupFailure() {
        // Given
        when(sqsService.getQueueUrl("test-queue"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Queue not found")));
        
        // When & Then
        assertThatThrownBy(() -> container.start())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to start SQS listener container");
        
        assertThat(container.isRunning()).isFalse();
    }
    
    @Test
    void shouldProcessSingleMessagesUsingComponents() throws Exception {
        // Given
        container.start();
        
        ArgumentCaptor<MessagePoller.MessageHandler> handlerCaptor = 
                ArgumentCaptor.forClass(MessagePoller.MessageHandler.class);
        verify(messagePoller).startPolling(anyString(), anyInt(), anyInt(), handlerCaptor.capture());
        
        SqsMessage message1 = createTestMessage("msg1", "body1");
        SqsMessage message2 = createTestMessage("msg2", "body2");
        List<SqsMessage> messages = Arrays.asList(message1, message2);
        
        // Setup mocks for successful processing
        doAnswer(invocation -> {
            Runnable operation = invocation.getArgument(0);
            operation.run();
            return null;
        }).when(retryManager).executeWithRetry(any(Runnable.class), any());
        
        // When
        MessagePoller.MessageHandler messageHandler = handlerCaptor.getValue();
        messageHandler.handleMessages(messages);
        
        // Give some time for async processing
        Thread.sleep(100);
        
        // Then
        verify(messageProcessor, times(2)).processMessage(any(SqsMessage.class), any());
        verify(metricsCollector, times(2)).recordMessageProcessed(eq("test-container"));
        verify(metricsCollector, times(2)).recordProcessingTime(eq("test-container"), anyLong());
    }
    
    @Test
    void shouldProcessBatchMessagesUsingComponents() throws Exception {
        // Given - Create container with batch mode
        Method batchListenerMethod = TestListener.class.getDeclaredMethod("handleMessageBatch", List.class);
        SqsListener batchAnnotation = createBatchSqsListenerAnnotation();
        
        RefactoredSqsListenerContainer batchContainer = new RefactoredSqsListenerContainer(
                "batch-container",
                testListener,
                batchListenerMethod,
                batchAnnotation,
                sqsService,
                environment,
                executorService,
                messagePoller,
                messageProcessor,
                retryManager,
                dlqHandler,
                metricsCollector
        );
        
        batchContainer.start();
        
        ArgumentCaptor<MessagePoller.MessageHandler> handlerCaptor = 
                ArgumentCaptor.forClass(MessagePoller.MessageHandler.class);
        verify(messagePoller).startPolling(anyString(), anyInt(), anyInt(), handlerCaptor.capture());
        
        SqsMessage message1 = createTestMessage("msg1", "body1");
        SqsMessage message2 = createTestMessage("msg2", "body2");
        List<SqsMessage> messages = Arrays.asList(message1, message2);
        
        // Setup mocks for successful processing
        doAnswer(invocation -> {
            Runnable operation = invocation.getArgument(0);
            operation.run();
            return null;
        }).when(retryManager).executeWithRetry(any(Runnable.class), any());
        
        // When
        MessagePoller.MessageHandler messageHandler = handlerCaptor.getValue();
        messageHandler.handleMessages(messages);
        
        // Give some time for async processing
        Thread.sleep(100);
        
        // Then
        verify(messageProcessor).processBatch(eq(messages), any());
        verify(metricsCollector).recordMessageProcessed(eq("batch-container"));
        verify(metricsCollector).recordProcessingTime(eq("batch-container"), anyLong());
        
        batchContainer.stop();
    }
    
    @Test
    void shouldHandleProcessingFailureWithDlq() throws Exception {
        // Given
        container.start();
        
        ArgumentCaptor<MessagePoller.MessageHandler> handlerCaptor = 
                ArgumentCaptor.forClass(MessagePoller.MessageHandler.class);
        verify(messagePoller).startPolling(anyString(), anyInt(), anyInt(), handlerCaptor.capture());
        
        SqsMessage message = createTestMessage("msg1", "body1");
        List<SqsMessage> messages = List.of(message);
        
        RuntimeException processingException = new RuntimeException("Processing failed");
        
        // Setup mocks for failed processing
        doAnswer(invocation -> {
            Runnable operation = invocation.getArgument(0);
            operation.run();
            return null;
        }).when(retryManager).executeWithRetry(any(Runnable.class), any());
        
        doThrow(new MessageProcessor.MessageProcessingException("Processing failed", processingException))
                .when(messageProcessor).processMessage(any(), any());
        
        when(dlqHandler.sendToDeadLetterQueue(any(), any(), any())).thenReturn(true);
        
        // When
        MessagePoller.MessageHandler messageHandler = handlerCaptor.getValue();
        messageHandler.handleMessages(messages);
        
        // Give some time for async processing
        Thread.sleep(100);
        
        // Then
        verify(metricsCollector).recordMessageFailed(eq("test-container"), any());
        verify(dlqHandler).sendToDeadLetterQueue(eq(message), any(), any());
        verify(metricsCollector).recordStateChange(eq("test-container"), eq("PROCESSING"), eq("DLQ_SUCCESS"));
    }
    
    @Test
    void shouldReturnContainerStats() {
        // When
        RefactoredSqsListenerContainer.ContainerStats stats = container.getStats();
        
        // Then
        assertThat(stats.getContainerId()).isEqualTo("test-container");
        assertThat(stats.isRunning()).isFalse();
        assertThat(stats.getProcessedMessages()).isEqualTo(5L);
        assertThat(stats.getFailedMessages()).isEqualTo(1L);
        
        verify(metricsCollector).getContainerMetrics("test-container");
    }
    
    @Test
    void shouldResolveQueueUrlFromQueueName() {
        // Given
        container.start();
        
        // Then
        verify(sqsService).getQueueUrl("test-queue");
        verify(messagePoller).startPolling(
                eq("https://sqs.region.amazonaws.com/123456789012/test-queue"),
                anyInt(),
                anyInt(),
                any()
        );
    }
    
    @Test
    void shouldThrowExceptionWhenNoQueueSpecified() throws Exception {
        // Given
        Method listenerMethod = TestListener.class.getDeclaredMethod("handleMessage", SqsMessage.class);
        SqsListener annotation = createEmptySqsListenerAnnotation();
        
        RefactoredSqsListenerContainer invalidContainer = new RefactoredSqsListenerContainer(
                "invalid-container",
                testListener,
                listenerMethod,
                annotation,
                sqsService,
                environment,
                executorService,
                messagePoller,
                messageProcessor,
                retryManager,
                dlqHandler,
                metricsCollector
        );
        
        // When & Then
        assertThatThrownBy(invalidContainer::start)
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
    
    private SqsMessage createTestMessage(String messageId, String body) {
        return SqsMessage.builder()
                .messageId(messageId)
                .body(body)
                .receiptHandle("receipt-" + messageId)
                .build();
    }
    
    private SqsListener createSqsListenerAnnotation() {
        return new SqsListener() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return SqsListener.class;
            }
            
            @Override
            public String queueName() { return "test-queue"; }
            
            @Override
            public String queueUrl() { return ""; }
            
            @Override
            public String id() { return ""; }
            
            @Override
            public int maxConcurrentMessages() { return 10; }
            
            @Override
            public int pollTimeoutSeconds() { return 20; }
            
            @Override
            public int messageVisibilitySeconds() { return 30; }
            
            @Override
            public int maxMessagesPerPoll() { return 10; }
            
            @Override
            public boolean batchMode() { return false; }
            
            @Override
            public int batchSize() { return 10; }
            
            @Override
            public boolean autoDelete() { return true; }
            
            @Override
            public int maxRetryAttempts() { return 3; }
            
            @Override
            public long retryDelayMillis() { return 1000L; }
            
            @Override
            public boolean enableDeadLetterQueue() { return true; }
            
            @Override
            public String deadLetterQueueName() { return "test-dlq"; }
        };
    }
    
    private SqsListener createBatchSqsListenerAnnotation() {
        return new SqsListener() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return SqsListener.class;
            }
            
            @Override
            public String queueName() { return "test-queue"; }
            
            @Override
            public String queueUrl() { return ""; }
            
            @Override
            public String id() { return ""; }
            
            @Override
            public int maxConcurrentMessages() { return 10; }
            
            @Override
            public int pollTimeoutSeconds() { return 20; }
            
            @Override
            public int messageVisibilitySeconds() { return 30; }
            
            @Override
            public int maxMessagesPerPoll() { return 10; }
            
            @Override
            public boolean batchMode() { return true; }
            
            @Override
            public int batchSize() { return 10; }
            
            @Override
            public boolean autoDelete() { return true; }
            
            @Override
            public int maxRetryAttempts() { return 3; }
            
            @Override
            public long retryDelayMillis() { return 1000L; }
            
            @Override
            public boolean enableDeadLetterQueue() { return true; }
            
            @Override
            public String deadLetterQueueName() { return "test-dlq"; }
        };
    }
    
    private SqsListener createEmptySqsListenerAnnotation() {
        return new SqsListener() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return SqsListener.class;
            }
            
            @Override
            public String queueName() { return ""; }
            
            @Override
            public String queueUrl() { return ""; }
            
            @Override
            public String id() { return ""; }
            
            @Override
            public int maxConcurrentMessages() { return 10; }
            
            @Override
            public int pollTimeoutSeconds() { return 20; }
            
            @Override
            public int messageVisibilitySeconds() { return 30; }
            
            @Override
            public int maxMessagesPerPoll() { return 10; }
            
            @Override
            public boolean batchMode() { return false; }
            
            @Override
            public int batchSize() { return 10; }
            
            @Override
            public boolean autoDelete() { return true; }
            
            @Override
            public int maxRetryAttempts() { return 3; }
            
            @Override
            public long retryDelayMillis() { return 1000L; }
            
            @Override
            public boolean enableDeadLetterQueue() { return false; }
            
            @Override
            public String deadLetterQueueName() { return ""; }
        };
    }
    
    /**
     * Test listener class for testing.
     */
    public static class TestListener {
        
        public void handleMessage(SqsMessage message) {
            // Test implementation
        }
        
        public void handleMessageBatch(List<SqsMessage> messages) {
            // Test implementation
        }
    }
}