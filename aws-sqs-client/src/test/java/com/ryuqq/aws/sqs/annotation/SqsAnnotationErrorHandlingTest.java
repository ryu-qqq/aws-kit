package com.ryuqq.aws.sqs.annotation;

import com.ryuqq.aws.sqs.example.OrderDto;
import com.ryuqq.aws.sqs.example.OrderQueueService;
import com.ryuqq.aws.sqs.model.SqsMessage;
import com.ryuqq.aws.sqs.proxy.SqsClientProxyFactory;
import com.ryuqq.aws.sqs.serialization.JacksonMessageSerializer;
import com.ryuqq.aws.sqs.serialization.MessageSerializationException;
import com.ryuqq.aws.sqs.service.SqsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Error handling tests for annotation-based SQS client.
 * Tests exception propagation, error recovery, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class SqsAnnotationErrorHandlingTest {

    @Mock
    private SqsService sqsService;
    
    @Mock
    private JacksonMessageSerializer mockSerializer;
    
    private SqsClientProxyFactory proxyFactory;
    private OrderQueueService orderService;
    private OrderQueueService mockSerializerOrderService;
    
    @BeforeEach
    void setUp() {
        // Service with real serializer
        JacksonMessageSerializer realSerializer = new JacksonMessageSerializer();
        proxyFactory = new SqsClientProxyFactory(sqsService, realSerializer);
        orderService = proxyFactory.createProxy(OrderQueueService.class);
        
        // Service with mock serializer for testing serialization errors
        SqsClientProxyFactory mockSerializerProxyFactory = new SqsClientProxyFactory(sqsService, mockSerializer);
        mockSerializerOrderService = mockSerializerProxyFactory.createProxy(OrderQueueService.class);
    }

    @Nested
    @DisplayName("SQS Service Error Handling")
    class SqsServiceErrorHandling {
        
        @Test
        @DisplayName("Should propagate SQS service exceptions on send")
        void shouldPropagateSqsServiceExceptionsOnSend() {
            // Given
            OrderDto order = OrderDto.create("ORDER-ERROR", "CUSTOMER", BigDecimal.valueOf(99.99));
            RuntimeException sqsException = new RuntimeException("SQS service unavailable");
            
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(sqsException));
            
            // When & Then
            CompletableFuture<String> future = orderService.sendOrderForProcessing(order);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("SQS service unavailable");
        }
        
        @Test
        @DisplayName("Should propagate SQS service exceptions on batch send")
        void shouldPropagateSqsServiceExceptionsOnBatchSend() {
            // Given
            List<OrderDto> orders = List.of(
                    OrderDto.create("ORDER-1", "CUSTOMER", BigDecimal.valueOf(50.00)),
                    OrderDto.create("ORDER-2", "CUSTOMER", BigDecimal.valueOf(75.00))
            );
            RuntimeException sqsException = new RuntimeException("Batch send failed");
            
            when(sqsService.sendMessageBatch(anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(sqsException));
            
            // When & Then
            CompletableFuture<List<String>> future = orderService.sendOrderBatch(orders);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("Batch send failed");
        }
        
        @Test
        @DisplayName("Should propagate SQS service exceptions on receive")
        void shouldPropagateSqsServiceExceptionsOnReceive() {
            // Given
            Consumer<SqsMessage> processor = message -> {};
            RuntimeException sqsException = new RuntimeException("Receive failed");
            
            when(sqsService.receiveAndProcessMessages(anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(sqsException));
            
            // When & Then
            CompletableFuture<Void> future = orderService.processOrders(processor);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("Receive failed");
        }
        
        @Test
        @DisplayName("Should handle timeout exceptions")
        void shouldHandleTimeoutExceptions() {
            // Given
            OrderDto order = OrderDto.create("ORDER-TIMEOUT", "CUSTOMER", BigDecimal.valueOf(99.99));
            RuntimeException timeoutException = new RuntimeException("Operation timed out");
            
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(timeoutException));
            
            // When & Then
            CompletableFuture<String> future = orderService.sendOrderForProcessing(order);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("Operation timed out");
        }
    }

    @Nested
    @DisplayName("Serialization Error Handling")
    class SerializationErrorHandling {
        
        @Test
        @DisplayName("Should handle serialization exceptions")
        void shouldHandleSerializationExceptions() {
            // Given
            OrderDto order = OrderDto.create("ORDER-SERIALIZE", "CUSTOMER", BigDecimal.valueOf(99.99));
            MessageSerializationException serializationException = 
                    new MessageSerializationException("Failed to serialize object");
            
            when(mockSerializer.serialize(order)).thenThrow(serializationException);
            
            // When & Then
            assertThatThrownBy(() -> mockSerializerOrderService.sendOrderForProcessing(order))
                    .isInstanceOf(MessageSerializationException.class)
                    .hasMessage("Failed to serialize object");
        }
        
        @Test
        @DisplayName("Should handle serialization exceptions in batch operations")
        void shouldHandleSerializationExceptionsInBatch() {
            // Given
            List<OrderDto> orders = List.of(
                    OrderDto.create("ORDER-1", "CUSTOMER", BigDecimal.valueOf(50.00)),
                    OrderDto.create("ORDER-2", "CUSTOMER", BigDecimal.valueOf(75.00))
            );
            MessageSerializationException serializationException = 
                    new MessageSerializationException("Failed to serialize batch");
            
            when(mockSerializer.serialize(any())).thenThrow(serializationException);
            
            // When & Then
            assertThatThrownBy(() -> mockSerializerOrderService.sendOrderBatch(orders))
                    .isInstanceOf(MessageSerializationException.class)
                    .hasMessage("Failed to serialize batch");
        }
        
        @Test
        @DisplayName("Should handle null serialization result")
        void shouldHandleNullSerializationResult() {
            // Given
            OrderDto order = OrderDto.create("ORDER-NULL", "CUSTOMER", BigDecimal.valueOf(99.99));
            
            when(mockSerializer.serialize(order)).thenReturn(null);
            when(sqsService.sendMessage(anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture("msg-id"));
            
            // When
            CompletableFuture<String> future = mockSerializerOrderService.sendOrderForProcessing(order);
            
            // Then - Should handle null gracefully
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .hasRootCauseMessage("Message body is required for send operations");
        }
    }

    @Nested
    @DisplayName("Parameter Validation Error Handling")
    class ParameterValidationErrorHandling {
        
        @Test
        @DisplayName("Should reject null message body")
        void shouldRejectNullMessageBody() {
            // When & Then
            assertThatThrownBy(() -> orderService.sendOrderForProcessing(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Message body is required for send operations");
        }
        
        @Test
        @DisplayName("Should reject null queue name")
        void shouldRejectNullQueueName() {
            // Given
            OrderDto order = OrderDto.create("ORDER-001", "CUSTOMER", BigDecimal.valueOf(99.99));
            
            // When & Then
            assertThatThrownBy(() -> orderService.sendToQueue(null, order))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Queue name is required");
        }
        
        @Test
        @DisplayName("Should reject empty queue name")
        void shouldRejectEmptyQueueName() {
            // Given
            OrderDto order = OrderDto.create("ORDER-001", "CUSTOMER", BigDecimal.valueOf(99.99));
            
            // When & Then
            assertThatThrownBy(() -> orderService.sendToQueue("", order))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Queue name is required");
        }
        
        @Test
        @DisplayName("Should reject null message processor")
        void shouldRejectNullMessageProcessor() {
            // When & Then
            assertThatThrownBy(() -> orderService.processOrders(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Message processor is required for receive operations");
        }
        
        @Test
        @DisplayName("Should reject empty batch messages")
        void shouldRejectEmptyBatchMessages() {
            // When & Then
            assertThatThrownBy(() -> orderService.sendOrderBatch(List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Batch messages are required for batch send operations");
        }
        
        @Test
        @DisplayName("Should reject null batch messages")
        void shouldRejectNullBatchMessages() {
            // When & Then
            assertThatThrownBy(() -> orderService.sendOrderBatch(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Batch messages are required for batch send operations");
        }
    }

    @Nested
    @DisplayName("FIFO Queue Error Handling")
    class FifoQueueErrorHandling {
        
        @Test
        @DisplayName("Should reject FIFO operation without message group ID")
        void shouldRejectFifoOperationWithoutMessageGroupId() {
            // Given
            OrderDto order = OrderDto.create("ORDER-FIFO", "CUSTOMER", BigDecimal.valueOf(99.99));
            
            // When & Then
            assertThatThrownBy(() -> orderService.sendOrderToFifoQueue(order, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("MessageGroupId is required for FIFO queue operations");
        }
        
        @Test
        @DisplayName("Should reject FIFO operation with empty message group ID")
        void shouldRejectFifoOperationWithEmptyMessageGroupId() {
            // Given
            OrderDto order = OrderDto.create("ORDER-FIFO", "CUSTOMER", BigDecimal.valueOf(99.99));
            
            // When & Then
            assertThatThrownBy(() -> orderService.sendOrderToFifoQueue(order, ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("MessageGroupId is required for FIFO queue operations");
        }
    }

    @Nested
    @DisplayName("Runtime Exception Handling")
    class RuntimeExceptionHandling {
        
        @Test
        @DisplayName("Should handle unexpected runtime exceptions")
        void shouldHandleUnexpectedRuntimeExceptions() {
            // Given
            OrderDto order = OrderDto.create("ORDER-RUNTIME", "CUSTOMER", BigDecimal.valueOf(99.99));
            RuntimeException unexpectedException = new RuntimeException("Unexpected error");
            
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenThrow(unexpectedException);
            
            // When & Then
            assertThatThrownBy(() -> orderService.sendOrderForProcessing(order))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Unexpected error");
        }
        
        @Test
        @DisplayName("Should handle processor exceptions")
        void shouldHandleProcessorExceptions() {
            // Given
            Consumer<SqsMessage> faultyProcessor = message -> {
                throw new RuntimeException("Processor failed");
            };
            RuntimeException processorException = new RuntimeException("Processor execution failed");
            
            when(sqsService.receiveAndProcessMessages(anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(processorException));
            
            // When & Then
            CompletableFuture<Void> future = orderService.processOrders(faultyProcessor);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("Processor execution failed");
        }
        
        @Test
        @DisplayName("Should handle CompletionException wrapping")
        void shouldHandleCompletionExceptionWrapping() {
            // Given
            OrderDto order = OrderDto.create("ORDER-COMPLETION", "CUSTOMER", BigDecimal.valueOf(99.99));
            RuntimeException baseException = new RuntimeException("Base error");
            CompletionException wrappedException = new CompletionException(baseException);
            
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(wrappedException));
            
            // When & Then
            CompletableFuture<String> future = orderService.sendOrderForProcessing(order);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(CompletionException.class);
        }
    }

    @Nested
    @DisplayName("Edge Case Error Handling")
    class EdgeCaseErrorHandling {
        
        @Test
        @DisplayName("Should handle very large message bodies")
        void shouldHandleVeryLargeMessageBodies() {
            // Given - Create a very large order (simulate large payload)
            StringBuilder largeCustomerId = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largeCustomerId.append("CUSTOMER-").append(i).append("-");
            }
            
            OrderDto largeOrder = OrderDto.create("ORDER-LARGE", largeCustomerId.toString(), BigDecimal.valueOf(99.99));
            RuntimeException messageTooLargeException = new RuntimeException("Message size exceeds limit");
            
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(messageTooLargeException));
            
            // When & Then
            CompletableFuture<String> future = orderService.sendOrderForProcessing(largeOrder);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("Message size exceeds limit");
        }
        
        @Test
        @DisplayName("Should handle special characters in message attributes")
        void shouldHandleSpecialCharactersInMessageAttributes() {
            // Given
            OrderDto order = OrderDto.create("ORDER-SPECIAL", "CUSTOMER", BigDecimal.valueOf(99.99));
            Map<String, String> specialAttributes = Map.of(
                    "special-chars", "!@#$%^&*()_+-=[]{}|;':\",./<>?",
                    "unicode", "你好世界 🌍 العالم",
                    "empty", "",
                    "spaces", "   "
            );
            
            RuntimeException attributeException = new RuntimeException("Invalid message attributes");
            when(sqsService.sendMessageWithAttributes(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.failedFuture(attributeException));
            
            // When & Then
            CompletableFuture<String> future = orderService.sendOrderWithPriority(order, specialAttributes);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("Invalid message attributes");
        }
        
        @Test
        @DisplayName("Should handle network interruption scenarios")
        void shouldHandleNetworkInterruptionScenarios() {
            // Given
            OrderDto order = OrderDto.create("ORDER-NETWORK", "CUSTOMER", BigDecimal.valueOf(99.99));
            RuntimeException networkException = new RuntimeException("Network timeout");
            
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(networkException));
            
            // When & Then
            CompletableFuture<String> future = orderService.sendOrderForProcessing(order);
            
            assertThatThrownBy(future::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class)
                    .hasRootCauseMessage("Network timeout");
        }
        
        @Test
        @DisplayName("Should handle concurrent error scenarios")
        void shouldHandleConcurrentErrorScenarios() throws Exception {
            // Given
            OrderDto order = OrderDto.create("ORDER-CONCURRENT", "CUSTOMER", BigDecimal.valueOf(99.99));
            RuntimeException concurrentException = new RuntimeException("Concurrent access error");
            
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(concurrentException));
            
            // When - Multiple concurrent calls with errors
            CompletableFuture<String> future1 = orderService.sendOrderForProcessing(order);
            CompletableFuture<String> future2 = orderService.sendOrderForProcessing(order);
            CompletableFuture<String> future3 = orderService.sendOrderForProcessing(order);
            
            // Then - All should fail consistently
            assertThatThrownBy(future1::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
            
            assertThatThrownBy(future2::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
            
            assertThatThrownBy(future3::get)
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Error Recovery Scenarios")
    class ErrorRecoveryScenarios {
        
        @Test
        @DisplayName("Should recover from transient errors")
        void shouldRecoverFromTransientErrors() throws Exception {
            // Given
            OrderDto order = OrderDto.create("ORDER-RECOVERY", "CUSTOMER", BigDecimal.valueOf(99.99));
            
            // First call fails, second succeeds
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Transient error")))
                    .thenReturn(CompletableFuture.completedFuture("msg-success"));
            
            // When - First call fails
            CompletableFuture<String> firstAttempt = orderService.sendOrderForProcessing(order);
            assertThatThrownBy(firstAttempt::get)
                    .isInstanceOf(ExecutionException.class);
            
            // When - Second call succeeds
            CompletableFuture<String> secondAttempt = orderService.sendOrderForProcessing(order);
            String result = secondAttempt.get();
            
            // Then
            assertThat(result).isEqualTo("msg-success");
        }
        
        @Test
        @DisplayName("Should maintain proxy state after errors")
        void shouldMaintainProxyStateAfterErrors() throws Exception {
            // Given
            OrderDto order = OrderDto.create("ORDER-STATE", "CUSTOMER", BigDecimal.valueOf(99.99));
            
            // Simulate error then success
            when(sqsService.sendMessage(anyString(), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Error")))
                    .thenReturn(CompletableFuture.completedFuture("msg-success"));
            
            // When - Error occurs
            CompletableFuture<String> errorAttempt = orderService.sendOrderForProcessing(order);
            assertThatThrownBy(errorAttempt::get).isInstanceOf(ExecutionException.class);
            
            // When - Proxy should still work
            CompletableFuture<String> successAttempt = orderService.sendOrderForProcessing(order);
            String result = successAttempt.get();
            
            // Then - Proxy state should be maintained
            assertThat(result).isEqualTo("msg-success");
            assertThat(orderService.toString()).isEqualTo("SqsClientProxy[order-service]");
        }
    }
}