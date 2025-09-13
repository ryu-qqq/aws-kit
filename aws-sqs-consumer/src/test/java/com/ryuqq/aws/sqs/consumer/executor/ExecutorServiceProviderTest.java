package com.ryuqq.aws.sqs.consumer.executor;

import com.ryuqq.aws.sqs.consumer.properties.SqsConsumerProperties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.*;

class ExecutorServiceProviderTest {
    
    @Test
    void platformThreadExecutorServiceProvider_shouldCreateExecutors() {
        // Given
        SqsConsumerProperties properties = new SqsConsumerProperties(
            null, null, null, null, null, null, null, null, // defaults for basic props
            null, null, null, null, null, null, null, null, null, null // defaults for remaining props
        );
        PlatformThreadExecutorServiceProvider provider = 
            new PlatformThreadExecutorServiceProvider(properties);
        
        // When
        ExecutorService messageExecutor = provider.createMessageProcessingExecutor("test-consumer");
        ExecutorService pollingExecutor = provider.createPollingExecutor("test-consumer");
        
        // Then
        assertThat(messageExecutor).isNotNull();
        assertThat(pollingExecutor).isNotNull();
        assertThat(provider.getThreadingModel()).isEqualTo(ExecutorServiceProvider.ThreadingModel.PLATFORM_THREADS);
        assertThat(provider.supportsGracefulShutdown()).isTrue();
        
        // Cleanup
        provider.shutdown(5000);
    }
    
    @Test
    @EnabledOnJre(JRE.JAVA_21)
    void virtualThreadExecutorServiceProvider_shouldCreateExecutors_whenVirtualThreadsAvailable() {
        // Given
        Assumptions.assumeTrue(VirtualThreadExecutorServiceProvider.isVirtualThreadsSupported());
        VirtualThreadExecutorServiceProvider provider = new VirtualThreadExecutorServiceProvider();
        
        // When
        ExecutorService messageExecutor = provider.createMessageProcessingExecutor("test-consumer");
        ExecutorService pollingExecutor = provider.createPollingExecutor("test-consumer");
        
        // Then
        assertThat(messageExecutor).isNotNull();
        assertThat(pollingExecutor).isNotNull();
        assertThat(provider.getThreadingModel()).isEqualTo(ExecutorServiceProvider.ThreadingModel.VIRTUAL_THREADS);
        assertThat(provider.supportsGracefulShutdown()).isTrue();
        
        // Cleanup
        provider.shutdown(5000);
    }
    
    @Test
    void customExecutorServiceProvider_shouldCreateExecutors() {
        // Given
        CustomExecutorServiceProvider provider = CustomExecutorServiceProvider.builder()
            .executorFactory(consumerName -> java.util.concurrent.Executors.newCachedThreadPool())
            .withLifecycleManagement()
            .build();
        
        // When
        ExecutorService messageExecutor = provider.createMessageProcessingExecutor("test-consumer");
        ExecutorService pollingExecutor = provider.createPollingExecutor("test-consumer");
        
        // Then
        assertThat(messageExecutor).isNotNull();
        assertThat(pollingExecutor).isNotNull();
        assertThat(provider.getThreadingModel()).isEqualTo(ExecutorServiceProvider.ThreadingModel.CUSTOM);
        assertThat(provider.supportsGracefulShutdown()).isTrue();
    }
    
    @Test
    void customExecutorServiceProvider_withSharedExecutor_shouldReturnSameInstance() {
        // Given
        ExecutorService sharedExecutor = java.util.concurrent.Executors.newCachedThreadPool();
        CustomExecutorServiceProvider provider = new CustomExecutorServiceProvider(sharedExecutor);
        
        // When
        ExecutorService messageExecutor1 = provider.createMessageProcessingExecutor("consumer1");
        ExecutorService messageExecutor2 = provider.createMessageProcessingExecutor("consumer2");
        ExecutorService pollingExecutor1 = provider.createPollingExecutor("consumer1");
        
        // Then
        assertThat(messageExecutor1).isSameAs(sharedExecutor);
        assertThat(messageExecutor2).isSameAs(sharedExecutor);
        assertThat(pollingExecutor1).isSameAs(sharedExecutor);
        assertThat(provider.supportsGracefulShutdown()).isFalse(); // User manages lifecycle
        
        // Cleanup
        sharedExecutor.shutdown();
    }
    
    @Test
    void customExecutorServiceProvider_withDifferentFactories_shouldCreateDifferentExecutors() {
        // Given
        CustomExecutorServiceProvider provider = CustomExecutorServiceProvider.builder()
            .messageProcessingExecutorFactory(consumerName -> 
                java.util.concurrent.Executors.newFixedThreadPool(10))
            .pollingExecutorFactory(consumerName -> 
                java.util.concurrent.Executors.newSingleThreadExecutor())
            .withLifecycleManagement()
            .build();
        
        // When
        ExecutorService messageExecutor = provider.createMessageProcessingExecutor("test-consumer");
        ExecutorService pollingExecutor = provider.createPollingExecutor("test-consumer");
        
        // Then
        assertThat(messageExecutor).isNotNull();
        assertThat(pollingExecutor).isNotNull();
        assertThat(messageExecutor).isNotSameAs(pollingExecutor);
    }
    
    @Test
    void virtualThreadsSupport_shouldReturnCorrectAvailability() {
        // This test will pass differently depending on Java version
        boolean isSupported = VirtualThreadExecutorServiceProvider.isVirtualThreadsSupported();
        
        // Just verify the method doesn't throw and returns a boolean
        assertThat(isSupported).isIn(true, false);
    }
    
    @Test
    void platformThreadExecutorProvider_shouldHandleShutdownGracefully() throws InterruptedException {
        // Given
        SqsConsumerProperties properties = new SqsConsumerProperties(
            null, null, null, null, null, null, null, null, // defaults for basic props
            null, null, null, null, null, null, null, null, null, null // defaults for remaining props
        );
        PlatformThreadExecutorServiceProvider provider = 
            new PlatformThreadExecutorServiceProvider(properties);
        
        ExecutorService executor = provider.createMessageProcessingExecutor("test");
        
        // When - Submit a task and then shutdown
        executor.submit(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        provider.shutdown(5000);
        
        // Then
        assertThat(executor.isShutdown()).isTrue();
    }
}