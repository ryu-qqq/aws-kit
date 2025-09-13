package com.ryuqq.aws.sqs.consumer;

import com.ryuqq.aws.sqs.consumer.executor.ExecutorServiceProvider;
import com.ryuqq.aws.sqs.consumer.processor.SqsListenerAnnotationBeanPostProcessor;
import com.ryuqq.aws.sqs.consumer.properties.SqsConsumerProperties;
import com.ryuqq.aws.sqs.consumer.registry.SqsListenerContainerRegistry;
import com.ryuqq.aws.sqs.service.SqsService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for AwsSqsConsumerAutoConfiguration.
 */
class AwsSqsConsumerAutoConfigurationTest {
    
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AwsSqsConsumerAutoConfiguration.class))
        .withUserConfiguration(TestConfiguration.class)
        .withPropertyValues(
            "aws.sqs.consumer.default-max-concurrent-messages=10",
            "aws.sqs.consumer.default-poll-timeout-seconds=20",
            "aws.sqs.consumer.default-message-visibility-seconds=30",
            "aws.sqs.consumer.default-max-messages-per-poll=10",
            "aws.sqs.consumer.default-batch-size=10",
            "aws.sqs.consumer.thread-pool-size=20",
            "aws.sqs.consumer.thread-pool-core-size=10",
            "aws.sqs.consumer.thread-pool-max-size=50"
        );
    
    @Test
    void autoConfiguration_기본활성화() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SqsListenerContainerRegistry.class);
            assertThat(context).hasSingleBean(SqsListenerAnnotationBeanPostProcessor.class);
            assertThat(context).hasSingleBean(SqsConsumerProperties.class);
            assertThat(context).hasSingleBean(ExecutorServiceProvider.class);
        });
    }
    
    @Test
    void autoConfiguration_활성화설정() {
        contextRunner
            .withPropertyValues("aws.sqs.consumer.enabled=true")
            .run(context -> {
                assertThat(context).hasSingleBean(SqsListenerContainerRegistry.class);
                assertThat(context).hasSingleBean(SqsListenerAnnotationBeanPostProcessor.class);
                assertThat(context).hasSingleBean(SqsConsumerProperties.class);
                assertThat(context).hasSingleBean(ExecutorServiceProvider.class);
            });
    }
    
    @Test
    void autoConfiguration_비활성화설정() {
        contextRunner
            .withPropertyValues("aws.sqs.consumer.enabled=false")
            .run(context -> {
                assertThat(context).doesNotHaveBean(SqsListenerContainerRegistry.class);
                assertThat(context).doesNotHaveBean(SqsListenerAnnotationBeanPostProcessor.class);
                assertThat(context).doesNotHaveBean(SqsConsumerProperties.class);
            });
    }
    
    @Test
    void autoConfiguration_SqsService없으면비활성화() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AwsSqsConsumerAutoConfiguration.class))
            .withPropertyValues(
                "aws.sqs.consumer.default-max-concurrent-messages=10",
                "aws.sqs.consumer.default-poll-timeout-seconds=20",
                "aws.sqs.consumer.default-message-visibility-seconds=30",
                "aws.sqs.consumer.default-max-messages-per-poll=10",
                "aws.sqs.consumer.default-batch-size=10"
            )
            .run(context -> {
                assertThat(context).doesNotHaveBean(SqsListenerContainerRegistry.class);
                assertThat(context).doesNotHaveBean(SqsListenerAnnotationBeanPostProcessor.class);
            });
    }
    
    @Test
    void autoConfiguration_프로퍼티설정() {
        contextRunner
            .withPropertyValues(
                "aws.sqs.consumer.default-max-concurrent-messages=15",
                "aws.sqs.consumer.thread-pool-size=25",
                "aws.sqs.consumer.enable-metrics=false"
            )
            .run(context -> {
                SqsConsumerProperties properties = context.getBean(SqsConsumerProperties.class);
                assertThat(properties.getDefaultMaxConcurrentMessages()).isEqualTo(15);
                assertThat(properties.getThreadPoolSize()).isEqualTo(25);
                assertThat(properties.isEnableMetrics()).isFalse();
            });
    }
    
    @Test
    void autoConfiguration_커스텀Bean우선() {
        contextRunner
            .withUserConfiguration(CustomBeanConfiguration.class)
            .run(context -> {
                assertThat(context).hasSingleBean(SqsListenerContainerRegistry.class);
                assertThat(context).hasSingleBean(SqsListenerAnnotationBeanPostProcessor.class);
                
                // Custom beans should be used
                assertThat(context.getBean(SqsListenerContainerRegistry.class))
                    .isSameAs(CustomBeanConfiguration.customRegistry);
                assertThat(context.getBean(SqsListenerAnnotationBeanPostProcessor.class))
                    .isSameAs(CustomBeanConfiguration.customProcessor);
            });
    }
    
    @Configuration
    static class TestConfiguration {
        @Bean
        public SqsService sqsService() {
            return mock(SqsService.class);
        }
    }
    
    @Configuration
    static class CustomBeanConfiguration {
        static final SqsListenerContainerRegistry customRegistry = mock(SqsListenerContainerRegistry.class);
        static final SqsListenerAnnotationBeanPostProcessor customProcessor = mock(SqsListenerAnnotationBeanPostProcessor.class);
        
        @Bean
        public SqsService sqsService() {
            return mock(SqsService.class);
        }
        
        @Bean
        public SqsListenerContainerRegistry sqsListenerContainerRegistry() {
            return customRegistry;
        }
        
        @Bean
        public SqsListenerAnnotationBeanPostProcessor sqsListenerAnnotationBeanPostProcessor() {
            return customProcessor;
        }
    }
}