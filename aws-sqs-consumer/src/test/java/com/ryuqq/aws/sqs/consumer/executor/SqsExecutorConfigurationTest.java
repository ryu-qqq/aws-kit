package com.ryuqq.aws.sqs.consumer.executor;

import com.ryuqq.aws.sqs.consumer.properties.SqsConsumerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;

class SqsExecutorConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(SqsExecutorConfiguration.class, TestSqsProperties.class);
    
    @Test
    void shouldCreateDefaultPlatformThreadExecutorProvider() {
        contextRunner
            .withPropertyValues("aws.sqs.consumer.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(ExecutorServiceProvider.class);
                ExecutorServiceProvider provider = context.getBean(ExecutorServiceProvider.class);
                assertThat(provider).isInstanceOf(PlatformThreadExecutorServiceProvider.class);
                assertThat(provider.getThreadingModel())
                    .isEqualTo(ExecutorServiceProvider.ThreadingModel.PLATFORM_THREADS);
            });
    }
    
    @Test
    void shouldCreateVirtualThreadExecutorProvider_whenConfigured() {
        contextRunner
            .withPropertyValues(
                "aws.sqs.consumer.enabled=true",
                "aws.sqs.consumer.executor.type=VIRTUAL_THREADS"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ExecutorServiceProvider.class);
                ExecutorServiceProvider provider = context.getBean(ExecutorServiceProvider.class);
                
                // Will be VirtualThread provider if Java 21+, otherwise fallback to PlatformThread
                if (VirtualThreadExecutorServiceProvider.isVirtualThreadsSupported()) {
                    assertThat(provider).isInstanceOf(VirtualThreadExecutorServiceProvider.class);
                    assertThat(provider.getThreadingModel())
                        .isEqualTo(ExecutorServiceProvider.ThreadingModel.VIRTUAL_THREADS);
                } else {
                    assertThat(provider).isInstanceOf(PlatformThreadExecutorServiceProvider.class);
                    assertThat(provider.getThreadingModel())
                        .isEqualTo(ExecutorServiceProvider.ThreadingModel.PLATFORM_THREADS);
                }
            });
    }
    
    @Test
    void shouldCreateCustomExecutorProvider_whenConfigured() {
        contextRunner
            .withUserConfiguration(CustomExecutorConfig.class)
            .withPropertyValues(
                "aws.sqs.consumer.enabled=true",
                "aws.sqs.consumer.executor.type=CUSTOM"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ExecutorServiceProvider.class);
                ExecutorServiceProvider provider = context.getBean(ExecutorServiceProvider.class);
                assertThat(provider).isInstanceOf(CustomExecutorServiceProvider.class);
                assertThat(provider.getThreadingModel())
                    .isEqualTo(ExecutorServiceProvider.ThreadingModel.CUSTOM);
            });
    }
    
    @Test
    void shouldAutoSelectVirtualThreads_whenPreferVirtualThreadsEnabled() {
        contextRunner
            .withPropertyValues(
                "aws.sqs.consumer.enabled=true",
                "aws.sqs.consumer.executor.prefer-virtual-threads=true"
            )
            .run(context -> {
                assertThat(context).hasSingleBean(ExecutorServiceProvider.class);
                ExecutorServiceProvider provider = context.getBean(ExecutorServiceProvider.class);
                
                if (VirtualThreadExecutorServiceProvider.isVirtualThreadsSupported()) {
                    assertThat(provider).isInstanceOf(VirtualThreadExecutorServiceProvider.class);
                } else {
                    assertThat(provider).isInstanceOf(PlatformThreadExecutorServiceProvider.class);
                }
            });
    }
    
    @Test
    void shouldCreateExecutorsSuccessfully() {
        contextRunner
            .withPropertyValues("aws.sqs.consumer.enabled=true")
            .run(context -> {
                ExecutorServiceProvider provider = context.getBean(ExecutorServiceProvider.class);
                
                ExecutorService messageExecutor = provider.createMessageProcessingExecutor("test-consumer");
                ExecutorService pollingExecutor = provider.createPollingExecutor("test-consumer");
                
                assertThat(messageExecutor).isNotNull();
                assertThat(pollingExecutor).isNotNull();
                
                // Cleanup
                if (provider.supportsGracefulShutdown()) {
                    provider.shutdown(1000);
                }
            });
    }
    
    @TestConfiguration
    static class TestSqsProperties {
        @Bean
        public SqsConsumerProperties sqsConsumerProperties() {
            return new SqsConsumerProperties();
        }
    }
    
    @TestConfiguration
    static class CustomExecutorConfig {
        @Bean
        public ExecutorServiceProvider customExecutorServiceProvider() {
            return CustomExecutorServiceProvider.builder()
                .executorFactory(consumerName -> java.util.concurrent.Executors.newCachedThreadPool())
                .withLifecycleManagement()
                .build();
        }
    }
}