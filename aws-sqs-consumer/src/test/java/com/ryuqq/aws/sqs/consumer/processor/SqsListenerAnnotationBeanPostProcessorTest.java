package com.ryuqq.aws.sqs.consumer.processor;

import com.ryuqq.aws.sqs.consumer.annotation.SqsListener;
import com.ryuqq.aws.sqs.consumer.executor.ExecutorServiceProvider;
import com.ryuqq.aws.sqs.consumer.registry.SqsListenerContainerRegistry;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests for SqsListenerAnnotationBeanPostProcessor.
 */
@ExtendWith(MockitoExtension.class)
class SqsListenerAnnotationBeanPostProcessorTest {
    
    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private SqsListenerContainerRegistry containerRegistry;

    @Mock
    private SqsService sqsService;

    @Mock
    private Environment environment;

    @Mock
    private ExecutorServiceProvider executorServiceProvider;
    
    private SqsListenerAnnotationBeanPostProcessor processor;
    
    @BeforeEach
    void setUp() {
        processor = new SqsListenerAnnotationBeanPostProcessor();
        processor.setApplicationContext(applicationContext);

        // Use lenient() for all bean stubs since some tests set applicationContext to null
        lenient().when(applicationContext.getBean(SqsListenerContainerRegistry.class)).thenReturn(containerRegistry);
        lenient().when(applicationContext.getBean(SqsService.class)).thenReturn(sqsService);
        lenient().when(applicationContext.getBean(ExecutorServiceProvider.class)).thenReturn(executorServiceProvider);
        lenient().when(applicationContext.getEnvironment()).thenReturn(environment);
        // Use lenient() for stubs that aren't used by all tests
        lenient().when(environment.resolvePlaceholders(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // Mock ExecutorService creation - use lenient() since not all tests need these
        ExecutorService mockExecutorService = Executors.newCachedThreadPool();
        lenient().when(executorServiceProvider.createMessageProcessingExecutor(any())).thenReturn(mockExecutorService);
        lenient().when(executorServiceProvider.createPollingExecutor(any())).thenReturn(mockExecutorService);
        lenient().when(executorServiceProvider.getThreadingModel()).thenReturn(ExecutorServiceProvider.ThreadingModel.VIRTUAL_THREADS);
    }
    
    @Test
    void postProcessAfterInitialization_어노테이션없음() {
        // Given
        Object bean = new Object();
        
        // When
        Object result = processor.postProcessAfterInitialization(bean, "testBean");
        
        // Then
        assertThat(result).isSameAs(bean);
        verifyNoInteractions(containerRegistry);
    }
    
    @Test
    void postProcessAfterInitialization_유효한리스너() {
        // Given
        TestListener bean = new TestListener();
        
        // When
        Object result = processor.postProcessAfterInitialization(bean, "testListener");
        
        // Then
        assertThat(result).isSameAs(bean);
        
        ArgumentCaptor<String> containerIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(containerRegistry, times(3)).registerContainer(containerIdCaptor.capture(), any());

        List<String> containerIds = containerIdCaptor.getAllValues();
        assertThat(containerIds).contains("testListener.validListener");
    }
    
    @Test
    void postProcessAfterInitialization_배치모드리스너() {
        // Given
        TestListener bean = new TestListener();
        
        // When
        Object result = processor.postProcessAfterInitialization(bean, "testListener");
        
        // Then
        assertThat(result).isSameAs(bean);
        
        // Both methods should be processed
        verify(containerRegistry, times(3)).registerContainer(any(), any());
    }
    
    @Test
    void postProcessAfterInitialization_커스텀ID() {
        // Given
        TestListener bean = new TestListener();
        
        // When
        processor.postProcessAfterInitialization(bean, "testListener");
        
        // Then
        ArgumentCaptor<String> containerIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(containerRegistry, times(3)).registerContainer(containerIdCaptor.capture(), any());
        
        List<String> containerIds = containerIdCaptor.getAllValues();
        assertThat(containerIds).contains("custom-id");
    }
    
    @Test
    void validation_잘못된파라미터개수() {
        // Given
        InvalidListener bean = new InvalidListener();
        
        // When & Then
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(bean, "invalidListener"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);

        // Verify the root cause message
        try {
            processor.postProcessAfterInitialization(bean, "invalidListener");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getMessage()).contains("must have exactly one parameter");
        }
    }
    
    @Test
    void validation_잘못된파라미터타입() {
        // Given
        InvalidTypeListener bean = new InvalidTypeListener();
        
        // When & Then
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(bean, "invalidTypeListener"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);

        // Verify the root cause message
        try {
            processor.postProcessAfterInitialization(bean, "invalidTypeListener");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getMessage()).contains("must accept SqsMessage parameter");
        }
    }
    
    @Test
    void validation_배치모드잘못된파라미터타입() {
        // Given
        InvalidBatchListener bean = new InvalidBatchListener();
        
        // When & Then
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(bean, "invalidBatchListener"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);

        // Verify the root cause message
        try {
            processor.postProcessAfterInitialization(bean, "invalidBatchListener");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getMessage()).contains("must accept List parameter");
        }
    }
    
    @Test
    void validation_큐설정없음() {
        // Given
        NoQueueListener bean = new NoQueueListener();
        
        // When & Then
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(bean, "noQueueListener"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);

        // Verify the root cause message
        try {
            processor.postProcessAfterInitialization(bean, "noQueueListener");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getMessage()).contains("must specify either queueName or queueUrl");
        }
    }
    
    @Test
    void validation_큐설정중복() {
        // Given
        DuplicateQueueListener bean = new DuplicateQueueListener();
        
        // When & Then
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(bean, "duplicateQueueListener"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);

        // Verify the root cause message
        try {
            processor.postProcessAfterInitialization(bean, "duplicateQueueListener");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getMessage()).contains("cannot specify both queueName and queueUrl");
        }
    }
    
    @Test
    void validation_잘못된수치설정() {
        // Given
        InvalidNumericListener bean = new InvalidNumericListener();
        
        // When & Then
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(bean, "invalidNumericListener"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);

        // Verify the root cause message
        try {
            processor.postProcessAfterInitialization(bean, "invalidNumericListener");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getMessage()).contains("must be positive");
        }
    }
    
    @Test
    void validation_잘못된폴링타임아웃() {
        // Given
        InvalidTimeoutListener bean = new InvalidTimeoutListener();
        
        // When & Then
        assertThatThrownBy(() -> processor.postProcessAfterInitialization(bean, "invalidTimeoutListener"))
            .isInstanceOf(RuntimeException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class);

        // Verify the root cause message
        try {
            processor.postProcessAfterInitialization(bean, "invalidTimeoutListener");
        } catch (RuntimeException e) {
            assertThat(e.getCause().getMessage()).contains("must be between 0 and 20");
        }
    }
    
    @Test
    void postProcessAfterInitialization_applicationContext가null() {
        // Given
        processor.setApplicationContext(null);
        Object bean = new TestListener();
        
        // When
        Object result = processor.postProcessAfterInitialization(bean, "testBean");
        
        // Then
        assertThat(result).isSameAs(bean);
        verifyNoInteractions(containerRegistry);
    }
    
    // Test listener classes
    static class TestListener {
        
        @SqsListener(queueName = "test-queue")
        public void validListener(SqsMessage message) {
        }
        
        @SqsListener(queueName = "batch-queue", batchMode = true)
        public void batchListener(List<SqsMessage> messages) {
        }
        
        @SqsListener(queueName = "custom-queue", id = "custom-id")
        public void customIdListener(SqsMessage message) {
        }
        
        // Non-annotated method should be ignored
        public void normalMethod() {
        }
    }
    
    static class InvalidListener {
        @SqsListener(queueName = "test-queue")
        public void invalidListener() {
            // No parameters
        }
    }
    
    static class InvalidTypeListener {
        @SqsListener(queueName = "test-queue")
        public void invalidTypeListener(String message) {
            // Wrong parameter type
        }
    }
    
    static class InvalidBatchListener {
        @SqsListener(queueName = "test-queue", batchMode = true)
        public void invalidBatchListener(String message) {
            // Batch mode but wrong parameter type
        }
    }
    
    static class NoQueueListener {
        @SqsListener
        public void noQueueListener(SqsMessage message) {
            // No queue specified
        }
    }
    
    static class DuplicateQueueListener {
        @SqsListener(queueName = "test-queue", queueUrl = "https://example.com/queue")
        public void duplicateQueueListener(SqsMessage message) {
            // Both queue name and URL specified
        }
    }
    
    static class InvalidNumericListener {
        @SqsListener(queueName = "test-queue", maxConcurrentMessages = 0)
        public void invalidNumericListener(SqsMessage message) {
            // Invalid numeric value
        }
    }
    
    static class InvalidTimeoutListener {
        @SqsListener(queueName = "test-queue", pollTimeoutSeconds = 25)
        public void invalidTimeoutListener(SqsMessage message) {
            // Invalid timeout value
        }
    }
}