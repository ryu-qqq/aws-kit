package com.ryuqq.aws.sqs.proxy;

import com.ryuqq.aws.sqs.annotation.*;
import com.ryuqq.aws.sqs.model.SqsMessage;
import com.ryuqq.aws.sqs.serialization.MessageSerializer;
import com.ryuqq.aws.sqs.service.SqsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SqsClientInvocationHandler.
 * Tests proxy method invocation and parameter extraction.
 */
@ExtendWith(MockitoExtension.class)
class SqsClientInvocationHandlerTest {

    @Mock
    private SqsService sqsService;
    
    @Mock
    private MessageSerializer messageSerializer;
    
    @Mock
    private SqsClient sqsClientAnnotation;
    
    private SqsClientInvocationHandler handler;
    
    @BeforeEach
    void setUp() {
        when(sqsClientAnnotation.name()).thenReturn("test-client");
        when(sqsClientAnnotation.queuePrefix()).thenReturn("test-");
        handler = new SqsClientInvocationHandler(sqsService, messageSerializer, sqsClientAnnotation);
    }

    @Nested
    @DisplayName("Object Method Handling")
    class ObjectMethodHandling {
        
        @Test
        @DisplayName("Should handle toString method")
        void shouldHandleToString() throws Throwable {
            Method method = Object.class.getMethod("toString");
            
            Object result = handler.invoke(null, method, null);
            
            assertThat(result).isEqualTo("SqsClientProxy[test-client]");
        }
        
        @Test
        @DisplayName("Should handle hashCode method")
        void shouldHandleHashCode() throws Throwable {
            Method method = Object.class.getMethod("hashCode");
            when(sqsClientAnnotation.hashCode()).thenReturn(12345);
            
            Object result = handler.invoke(null, method, null);
            
            assertThat(result).isEqualTo(12345);
        }
        
        @Test
        @DisplayName("Should handle equals method")
        void shouldHandleEquals() throws Throwable {
            Method method = Object.class.getMethod("equals", Object.class);
            Object[] args = {"test"};
            
            Object result = handler.invoke(null, method, args);
            
            assertThat(result).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("SendMessage Processing")
    class SendMessageProcessing {
        
        @Test
        @DisplayName("Should handle simple send message")
        void shouldHandleSimpleSendMessage() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendSimpleMessage", String.class);
            Object[] args = {"test message"};
            
            when(messageSerializer.serialize("test message")).thenReturn("\"test message\"");
            when(sqsService.sendMessage("test-simple", "\"test message\""))
                    .thenReturn(CompletableFuture.completedFuture("msg-123"));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            assertThat(result).isInstanceOf(CompletableFuture.class);
            CompletableFuture<String> future = (CompletableFuture<String>) result;
            assertThat(future.get()).isEqualTo("msg-123");
            
            verify(sqsService).sendMessage("test-simple", "\"test message\"");
        }
        
        @Test
        @DisplayName("Should handle send message with attributes")
        void shouldHandleSendMessageWithAttributes() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendMessageWithAttributes", String.class, Map.class);
            Map<String, String> attributes = Map.of("priority", "high");
            Object[] args = {"test message", attributes};
            
            when(messageSerializer.serialize("test message")).thenReturn("\"test message\"");
            when(sqsService.sendMessageWithAttributes("test-simple", "\"test message\"", attributes))
                    .thenReturn(CompletableFuture.completedFuture("msg-456"));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            CompletableFuture<String> future = (CompletableFuture<String>) result;
            assertThat(future.get()).isEqualTo("msg-456");
            
            verify(sqsService).sendMessageWithAttributes("test-simple", "\"test message\"", attributes);
        }
        
        @Test
        @DisplayName("Should handle FIFO message with message group ID")
        void shouldHandleFifoMessage() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendFifoMessage", String.class, String.class);
            Object[] args = {"test message", "group1"};
            
            when(messageSerializer.serialize("test message")).thenReturn("\"test message\"");
            when(sqsService.sendFifoMessage("test-fifo.fifo", "\"test message\"", "group1"))
                    .thenReturn(CompletableFuture.completedFuture("msg-fifo"));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            CompletableFuture<String> future = (CompletableFuture<String>) result;
            assertThat(future.get()).isEqualTo("msg-fifo");
            
            verify(sqsService).sendFifoMessage("test-fifo.fifo", "\"test message\"", "group1");
        }
        
        @Test
        @DisplayName("Should handle delayed message")
        void shouldHandleDelayedMessage() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendDelayedMessage", String.class);
            Object[] args = {"test message"};
            
            when(messageSerializer.serialize("test message")).thenReturn("\"test message\"");
            when(sqsService.sendDelayedMessage("test-delayed", "\"test message\"", Duration.ofSeconds(300)))
                    .thenReturn(CompletableFuture.completedFuture("msg-delayed"));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            CompletableFuture<String> future = (CompletableFuture<String>) result;
            assertThat(future.get()).isEqualTo("msg-delayed");
            
            verify(sqsService).sendDelayedMessage("test-delayed", "\"test message\"", Duration.ofSeconds(300));
        }
        
        @Test
        @DisplayName("Should handle dynamic queue name")
        void shouldHandleDynamicQueueName() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendToDynamicQueue", String.class, String.class);
            Object[] args = {"custom-queue", "test message"};
            
            when(messageSerializer.serialize("test message")).thenReturn("\"test message\"");
            when(sqsService.sendMessage("custom-queue", "\"test message\""))
                    .thenReturn(CompletableFuture.completedFuture("msg-dynamic"));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            CompletableFuture<String> future = (CompletableFuture<String>) result;
            assertThat(future.get()).isEqualTo("msg-dynamic");
            
            verify(sqsService).sendMessage("custom-queue", "\"test message\"");
        }
    }

    @Nested
    @DisplayName("SendBatch Processing")
    class SendBatchProcessing {
        
        @Test
        @DisplayName("Should handle batch message sending")
        void shouldHandleBatchSending() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendBatchMessages", List.class);
            List<String> messages = List.of("msg1", "msg2", "msg3");
            Object[] args = {messages};
            
            when(messageSerializer.serialize("msg1")).thenReturn("\"msg1\"");
            when(messageSerializer.serialize("msg2")).thenReturn("\"msg2\"");
            when(messageSerializer.serialize("msg3")).thenReturn("\"msg3\"");
            when(sqsService.sendMessageBatch(eq("test-batch"), anyList()))
                    .thenReturn(CompletableFuture.completedFuture(List.of("id1", "id2", "id3")));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            CompletableFuture<List<String>> future = (CompletableFuture<List<String>>) result;
            assertThat(future.get()).containsExactly("id1", "id2", "id3");
            
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(sqsService).sendMessageBatch(eq("test-batch"), captor.capture());
            assertThat(captor.getValue()).containsExactly("\"msg1\"", "\"msg2\"", "\"msg3\"");
        }
        
        @Test
        @DisplayName("Should handle empty batch")
        void shouldHandleEmptyBatch() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendBatchMessages", List.class);
            Object[] args = {List.of()};
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            CompletableFuture<List<String>> future = (CompletableFuture<List<String>>) result;
            assertThat(future.get()).isEmpty();
            
            verify(sqsService, never()).sendMessageBatch(anyString(), anyList());
        }
    }

    @Nested
    @DisplayName("ReceiveMessages Processing")
    class ReceiveMessagesProcessing {
        
        @Test
        @DisplayName("Should handle receive messages with CompletableFuture return")
        void shouldHandleReceiveMessagesAsync() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("receiveMessages", Consumer.class);
            Consumer<SqsMessage> processor = mock(Consumer.class);
            Object[] args = {processor};
            
            when(sqsService.receiveAndProcessMessages("test-receive", processor))
                    .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            assertThat(result).isInstanceOf(CompletableFuture.class);
            verify(sqsService).receiveAndProcessMessages("test-receive", processor);
        }
        
        @Test
        @DisplayName("Should handle receive messages with void return")
        void shouldHandleReceiveMessagesSync() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("receiveMessagesSync", Consumer.class);
            Consumer<SqsMessage> processor = mock(Consumer.class);
            Object[] args = {processor};
            
            when(sqsService.receiveAndProcessMessages("test-receive", processor))
                    .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            assertThat(result).isNull();
            verify(sqsService).receiveAndProcessMessages("test-receive", processor);
        }
        
        @Test
        @DisplayName("Should handle receive and auto-delete")
        void shouldHandleReceiveAndAutoDelete() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("receiveAndDelete", Consumer.class);
            Consumer<SqsMessage> processor = mock(Consumer.class);
            Object[] args = {processor};
            
            when(sqsService.receiveProcessAndDelete("test-auto-delete", processor))
                    .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            assertThat(result).isInstanceOf(CompletableFuture.class);
            verify(sqsService).receiveProcessAndDelete("test-auto-delete", processor);
        }
        
        @Test
        @DisplayName("Should handle dynamic receive configuration")
        void shouldHandleDynamicReceiveConfig() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("receiveFromDynamicQueue", String.class, Integer.class, Consumer.class);
            Consumer<SqsMessage> processor = mock(Consumer.class);
            Object[] args = {"dynamic-receive", 5, processor};
            
            when(sqsService.receiveAndProcessMessages("dynamic-receive", processor))
                    .thenReturn(CompletableFuture.completedFuture(null));
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            assertThat(result).isInstanceOf(CompletableFuture.class);
            verify(sqsService).receiveAndProcessMessages("dynamic-receive", processor);
        }
    }

    @Nested
    @DisplayName("StartPolling Processing")
    class StartPollingProcessing {
        
        @Test
        @DisplayName("Should handle start polling")
        void shouldHandleStartPolling() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("startPolling", Consumer.class);
            Consumer<SqsMessage> processor = mock(Consumer.class);
            Object[] args = {processor};
            
            // When
            Object result = handler.invoke(testClient, method, args);
            
            // Then
            assertThat(result).isNull();
            verify(sqsService).startContinuousPolling("test-polling", processor);
        }
    }

    @Nested
    @DisplayName("Parameter Extraction")
    class ParameterExtraction {
        
        @Test
        @DisplayName("Should extract queue name parameter")
        void shouldExtractQueueNameParameter() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendToDynamicQueue", String.class, String.class);
            Object[] args = {"my-queue", "message"};
            
            when(messageSerializer.serialize("message")).thenReturn("\"message\"");
            when(sqsService.sendMessage("my-queue", "\"message\""))
                    .thenReturn(CompletableFuture.completedFuture("msg-id"));
            
            // When
            handler.invoke(testClient, method, args);
            
            // Then
            verify(sqsService).sendMessage("my-queue", "\"message\"");
        }
        
        @Test
        @DisplayName("Should apply queue prefix")
        void shouldApplyQueuePrefix() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendSimpleMessage", String.class);
            Object[] args = {"message"};
            
            when(messageSerializer.serialize("message")).thenReturn("\"message\"");
            when(sqsService.sendMessage("test-simple", "\"message\""))
                    .thenReturn(CompletableFuture.completedFuture("msg-id"));
            
            // When
            handler.invoke(testClient, method, args);
            
            // Then
            verify(sqsService).sendMessage("test-simple", "\"message\"");
        }
        
        @Test
        @DisplayName("Should serialize complex objects")
        void shouldSerializeComplexObjects() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendComplexObject", TestDto.class);
            TestDto dto = new TestDto("value");
            Object[] args = {dto};
            
            when(messageSerializer.serialize(dto)).thenReturn("{\"field\":\"value\"}");
            when(sqsService.sendMessage("test-simple", "{\"field\":\"value\"}"))
                    .thenReturn(CompletableFuture.completedFuture("msg-id"));
            
            // When
            handler.invoke(testClient, method, args);
            
            // Then
            verify(messageSerializer).serialize(dto);
            verify(sqsService).sendMessage("test-simple", "{\"field\":\"value\"}");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {
        
        @Test
        @DisplayName("Should throw exception for missing queue name")
        void shouldThrowExceptionForMissingQueueName() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendToDynamicQueue", String.class, String.class);
            Object[] args = {null, "message"};
            
            // When & Then
            assertThatThrownBy(() -> handler.invoke(testClient, method, args))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Queue name is required");
        }
        
        @Test
        @DisplayName("Should throw exception for missing message body")
        void shouldThrowExceptionForMissingMessageBody() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendSimpleMessage", String.class);
            Object[] args = {null};
            
            // When & Then
            assertThatThrownBy(() -> handler.invoke(testClient, method, args))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Message body is required for send operations");
        }
        
        @Test
        @DisplayName("Should throw exception for missing message group ID in FIFO")
        void shouldThrowExceptionForMissingMessageGroupId() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("sendFifoMessage", String.class, String.class);
            Object[] args = {"message", null};
            
            when(messageSerializer.serialize("message")).thenReturn("\"message\"");
            
            // When & Then
            assertThatThrownBy(() -> handler.invoke(testClient, method, args))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("MessageGroupId is required for FIFO queue operations");
        }
        
        @Test
        @DisplayName("Should throw exception for missing processor")
        void shouldThrowExceptionForMissingProcessor() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("receiveMessages", Consumer.class);
            Object[] args = {null};
            
            // When & Then
            assertThatThrownBy(() -> handler.invoke(testClient, method, args))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Message processor is required for receive operations");
        }
        
        @Test
        @DisplayName("Should throw exception for unsupported method")
        void shouldThrowExceptionForUnsupportedMethod() throws Throwable {
            // Given
            TestSqsClient testClient = mock(TestSqsClient.class);
            Method method = TestSqsClient.class.getMethod("unsupportedMethod");
            Object[] args = {};
            
            // When & Then
            assertThatThrownBy(() -> handler.invoke(testClient, method, args))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Unsupported operation for method: unsupportedMethod");
        }
    }

    // Test interfaces and classes
    @SqsClient(value = "test-client", queuePrefix = "test-")
    interface TestSqsClient {
        
        @SendMessage("simple")
        CompletableFuture<String> sendSimpleMessage(@MessageBody String message);
        
        @SendMessage("simple")
        CompletableFuture<String> sendMessageWithAttributes(
                @MessageBody String message,
                @MessageAttributes Map<String, String> attributes
        );
        
        @SendMessage(value = "fifo.fifo", fifo = true)
        CompletableFuture<String> sendFifoMessage(
                @MessageBody String message,
                @MessageGroupId String groupId
        );
        
        @SendMessage(value = "delayed", delaySeconds = 300)
        CompletableFuture<String> sendDelayedMessage(@MessageBody String message);
        
        @SendMessage
        CompletableFuture<String> sendToDynamicQueue(
                @QueueName String queueName,
                @MessageBody String message
        );
        
        @SendMessage("simple")
        CompletableFuture<String> sendComplexObject(@MessageBody TestDto dto);
        
        @SendBatch("batch")
        CompletableFuture<List<String>> sendBatchMessages(@MessageBody List<String> messages);
        
        @ReceiveMessages("receive")
        CompletableFuture<Void> receiveMessages(@MessageProcessor Consumer<SqsMessage> processor);
        
        @ReceiveMessages("receive")
        void receiveMessagesSync(@MessageProcessor Consumer<SqsMessage> processor);
        
        @ReceiveMessages(value = "auto-delete", autoDelete = true)
        CompletableFuture<Void> receiveAndDelete(@MessageProcessor Consumer<SqsMessage> processor);
        
        @ReceiveMessages
        CompletableFuture<Void> receiveFromDynamicQueue(
                @QueueName String queueName,
                @MaxMessages Integer maxMessages,
                @MessageProcessor Consumer<SqsMessage> processor
        );
        
        @StartPolling("polling")
        void startPolling(@MessageProcessor Consumer<SqsMessage> processor);
        
        // Unsupported method for testing
        void unsupportedMethod();
    }
    
    static class TestDto {
        private final String field;
        
        public TestDto(String field) {
            this.field = field;
        }
        
        public String getField() {
            return field;
        }
    }
}