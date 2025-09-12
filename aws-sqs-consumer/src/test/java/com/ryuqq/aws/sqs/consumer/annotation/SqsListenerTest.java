package com.ryuqq.aws.sqs.consumer.annotation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for @SqsListener annotation.
 */
class SqsListenerTest {
    
    @Test
    void annotationDefaultValues() throws Exception {
        // Given
        Method method = TestListener.class.getDeclaredMethod("defaultListener");
        SqsListener annotation = method.getAnnotation(SqsListener.class);
        
        // Then
        assertThat(annotation).isNotNull();
        assertThat(annotation.queueName()).isEmpty();
        assertThat(annotation.queueUrl()).isEmpty();
        assertThat(annotation.id()).isEmpty();
        assertThat(annotation.maxConcurrentMessages()).isEqualTo(10);
        assertThat(annotation.pollTimeoutSeconds()).isEqualTo(20);
        assertThat(annotation.messageVisibilitySeconds()).isEqualTo(30);
        assertThat(annotation.maxMessagesPerPoll()).isEqualTo(10);
        assertThat(annotation.batchMode()).isFalse();
        assertThat(annotation.batchSize()).isEqualTo(10);
        assertThat(annotation.autoDelete()).isTrue();
        assertThat(annotation.maxRetryAttempts()).isEqualTo(3);
        assertThat(annotation.retryDelayMillis()).isEqualTo(1000L);
        assertThat(annotation.enableDeadLetterQueue()).isFalse();
        assertThat(annotation.deadLetterQueueName()).isEmpty();
    }
    
    @Test
    void annotationCustomValues() throws Exception {
        // Given
        Method method = TestListener.class.getDeclaredMethod("customListener");
        SqsListener annotation = method.getAnnotation(SqsListener.class);
        
        // Then
        assertThat(annotation).isNotNull();
        assertThat(annotation.queueName()).isEqualTo("test-queue");
        assertThat(annotation.queueUrl()).isEmpty();
        assertThat(annotation.id()).isEqualTo("custom-listener");
        assertThat(annotation.maxConcurrentMessages()).isEqualTo(5);
        assertThat(annotation.pollTimeoutSeconds()).isEqualTo(10);
        assertThat(annotation.messageVisibilitySeconds()).isEqualTo(60);
        assertThat(annotation.maxMessagesPerPoll()).isEqualTo(5);
        assertThat(annotation.batchMode()).isTrue();
        assertThat(annotation.batchSize()).isEqualTo(20);
        assertThat(annotation.autoDelete()).isFalse();
        assertThat(annotation.maxRetryAttempts()).isEqualTo(5);
        assertThat(annotation.retryDelayMillis()).isEqualTo(2000L);
        assertThat(annotation.enableDeadLetterQueue()).isTrue();
        assertThat(annotation.deadLetterQueueName()).isEqualTo("test-dlq");
    }
    
    @Test
    void annotationQueueUrlValues() throws Exception {
        // Given
        Method method = TestListener.class.getDeclaredMethod("queueUrlListener");
        SqsListener annotation = method.getAnnotation(SqsListener.class);
        
        // Then
        assertThat(annotation).isNotNull();
        assertThat(annotation.queueName()).isEmpty();
        assertThat(annotation.queueUrl()).isEqualTo("https://sqs.us-east-1.amazonaws.com/123456789012/test-queue");
    }
    
    @Test
    void annotationWithPropertyPlaceholders() throws Exception {
        // Given
        Method method = TestListener.class.getDeclaredMethod("propertyPlaceholderListener");
        SqsListener annotation = method.getAnnotation(SqsListener.class);
        
        // Then
        assertThat(annotation).isNotNull();
        assertThat(annotation.queueName()).isEqualTo("${app.queue.name}");
        assertThat(annotation.deadLetterQueueName()).isEqualTo("${app.dlq.name}");
    }
    
    // Test listener class with various annotation configurations
    static class TestListener {
        
        @SqsListener
        public void defaultListener() {
            // Method with default annotation values
        }
        
        @SqsListener(
            queueName = "test-queue",
            id = "custom-listener",
            maxConcurrentMessages = 5,
            pollTimeoutSeconds = 10,
            messageVisibilitySeconds = 60,
            maxMessagesPerPoll = 5,
            batchMode = true,
            batchSize = 20,
            autoDelete = false,
            maxRetryAttempts = 5,
            retryDelayMillis = 2000L,
            enableDeadLetterQueue = true,
            deadLetterQueueName = "test-dlq"
        )
        public void customListener() {
            // Method with custom annotation values
        }
        
        @SqsListener(queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/test-queue")
        public void queueUrlListener() {
            // Method using queue URL instead of queue name
        }
        
        @SqsListener(
            queueName = "${app.queue.name}",
            deadLetterQueueName = "${app.dlq.name}"
        )
        public void propertyPlaceholderListener() {
            // Method using property placeholders
        }
    }
}