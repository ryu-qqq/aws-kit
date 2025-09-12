package com.ryuqq.aws.sqs.consumer.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SqsConsumerProperties configuration.
 */
class SqsConsumerPropertiesTest {
    
    @Test
    void defaultValues() {
        // Given
        SqsConsumerProperties properties = new SqsConsumerProperties();
        
        // Then
        assertThat(properties.getDefaultMaxConcurrentMessages()).isEqualTo(10);
        assertThat(properties.getDefaultPollTimeoutSeconds()).isEqualTo(20);
        assertThat(properties.getDefaultMessageVisibilitySeconds()).isEqualTo(30);
        assertThat(properties.getDefaultMaxMessagesPerPoll()).isEqualTo(10);
        assertThat(properties.getDefaultBatchSize()).isEqualTo(10);
        assertThat(properties.getDefaultMaxRetryAttempts()).isEqualTo(3);
        assertThat(properties.getDefaultRetryDelayMillis()).isEqualTo(1000L);
        assertThat(properties.isDefaultAutoDelete()).isTrue();
        
        assertThat(properties.getThreadPoolSize()).isEqualTo(20);
        assertThat(properties.getThreadPoolCoreSize()).isEqualTo(10);
        assertThat(properties.getThreadPoolMaxSize()).isEqualTo(50);
        assertThat(properties.getThreadPoolQueueCapacity()).isEqualTo(100);
        assertThat(properties.getThreadPoolKeepAliveSeconds()).isEqualTo(60);
        assertThat(properties.getThreadNamePrefix()).isEqualTo("sqs-consumer-");
        
        assertThat(properties.isEnableMetrics()).isTrue();
        assertThat(properties.getHealthCheckIntervalMillis()).isEqualTo(30000L);
        assertThat(properties.getShutdownTimeoutMillis()).isEqualTo(30000L);
    }
    
    @Test
    void settersAndGetters() {
        // Given
        SqsConsumerProperties properties = new SqsConsumerProperties();
        
        // When
        properties.setDefaultMaxConcurrentMessages(5);
        properties.setDefaultPollTimeoutSeconds(15);
        properties.setDefaultMessageVisibilitySeconds(60);
        properties.setDefaultMaxMessagesPerPoll(5);
        properties.setDefaultBatchSize(20);
        properties.setDefaultMaxRetryAttempts(5);
        properties.setDefaultRetryDelayMillis(2000L);
        properties.setDefaultAutoDelete(false);
        
        properties.setThreadPoolSize(30);
        properties.setThreadPoolCoreSize(15);
        properties.setThreadPoolMaxSize(100);
        properties.setThreadPoolQueueCapacity(200);
        properties.setThreadPoolKeepAliveSeconds(120);
        properties.setThreadNamePrefix("custom-consumer-");
        
        properties.setEnableMetrics(false);
        properties.setHealthCheckIntervalMillis(60000L);
        properties.setShutdownTimeoutMillis(60000L);
        
        // Then
        assertThat(properties.getDefaultMaxConcurrentMessages()).isEqualTo(5);
        assertThat(properties.getDefaultPollTimeoutSeconds()).isEqualTo(15);
        assertThat(properties.getDefaultMessageVisibilitySeconds()).isEqualTo(60);
        assertThat(properties.getDefaultMaxMessagesPerPoll()).isEqualTo(5);
        assertThat(properties.getDefaultBatchSize()).isEqualTo(20);
        assertThat(properties.getDefaultMaxRetryAttempts()).isEqualTo(5);
        assertThat(properties.getDefaultRetryDelayMillis()).isEqualTo(2000L);
        assertThat(properties.isDefaultAutoDelete()).isFalse();
        
        assertThat(properties.getThreadPoolSize()).isEqualTo(30);
        assertThat(properties.getThreadPoolCoreSize()).isEqualTo(15);
        assertThat(properties.getThreadPoolMaxSize()).isEqualTo(100);
        assertThat(properties.getThreadPoolQueueCapacity()).isEqualTo(200);
        assertThat(properties.getThreadPoolKeepAliveSeconds()).isEqualTo(120);
        assertThat(properties.getThreadNamePrefix()).isEqualTo("custom-consumer-");
        
        assertThat(properties.isEnableMetrics()).isFalse();
        assertThat(properties.getHealthCheckIntervalMillis()).isEqualTo(60000L);
        assertThat(properties.getShutdownTimeoutMillis()).isEqualTo(60000L);
    }
    
    @SpringBootTest(classes = PropertyBindingTest.TestConfiguration.class)
    @TestPropertySource(properties = {
        "aws.sqs.consumer.default-max-concurrent-messages=15",
        "aws.sqs.consumer.default-poll-timeout-seconds=10",
        "aws.sqs.consumer.default-message-visibility-seconds=45",
        "aws.sqs.consumer.default-max-messages-per-poll=8",
        "aws.sqs.consumer.default-batch-size=25",
        "aws.sqs.consumer.default-max-retry-attempts=2",
        "aws.sqs.consumer.default-retry-delay-millis=1500",
        "aws.sqs.consumer.default-auto-delete=false",
        "aws.sqs.consumer.thread-pool-size=40",
        "aws.sqs.consumer.thread-pool-core-size=20",
        "aws.sqs.consumer.thread-pool-max-size=80",
        "aws.sqs.consumer.thread-pool-queue-capacity=150",
        "aws.sqs.consumer.thread-pool-keep-alive-seconds=90",
        "aws.sqs.consumer.thread-name-prefix=test-consumer-",
        "aws.sqs.consumer.enable-metrics=false",
        "aws.sqs.consumer.health-check-interval-millis=45000",
        "aws.sqs.consumer.shutdown-timeout-millis=45000"
    })
    static class PropertyBindingTest {
        
        @Autowired
        private SqsConsumerProperties properties;
        
        @Test
        void propertiesAreCorrectlyBound() {
            // Then
            assertThat(properties.getDefaultMaxConcurrentMessages()).isEqualTo(15);
            assertThat(properties.getDefaultPollTimeoutSeconds()).isEqualTo(10);
            assertThat(properties.getDefaultMessageVisibilitySeconds()).isEqualTo(45);
            assertThat(properties.getDefaultMaxMessagesPerPoll()).isEqualTo(8);
            assertThat(properties.getDefaultBatchSize()).isEqualTo(25);
            assertThat(properties.getDefaultMaxRetryAttempts()).isEqualTo(2);
            assertThat(properties.getDefaultRetryDelayMillis()).isEqualTo(1500L);
            assertThat(properties.isDefaultAutoDelete()).isFalse();
            
            assertThat(properties.getThreadPoolSize()).isEqualTo(40);
            assertThat(properties.getThreadPoolCoreSize()).isEqualTo(20);
            assertThat(properties.getThreadPoolMaxSize()).isEqualTo(80);
            assertThat(properties.getThreadPoolQueueCapacity()).isEqualTo(150);
            assertThat(properties.getThreadPoolKeepAliveSeconds()).isEqualTo(90);
            assertThat(properties.getThreadNamePrefix()).isEqualTo("test-consumer-");
            
            assertThat(properties.isEnableMetrics()).isFalse();
            assertThat(properties.getHealthCheckIntervalMillis()).isEqualTo(45000L);
            assertThat(properties.getShutdownTimeoutMillis()).isEqualTo(45000L);
        }
        
        @EnableConfigurationProperties(SqsConsumerProperties.class)
        static class TestConfiguration {
        }
    }
}