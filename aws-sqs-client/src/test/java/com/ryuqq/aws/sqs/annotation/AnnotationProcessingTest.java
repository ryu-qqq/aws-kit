package com.ryuqq.aws.sqs.annotation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for annotation processing and metadata extraction.
 * Validates annotation attributes and parameter extraction logic.
 */
class AnnotationProcessingTest {

    @Nested
    @DisplayName("SqsClient Annotation Processing")
    class SqsClientAnnotationProcessing {
        
        @Test
        @DisplayName("Should extract SqsClient annotation attributes")
        void shouldExtractSqsClientAnnotation() {
            // Given
            Class<TestSqsClient> clientClass = TestSqsClient.class;
            
            // When
            SqsClient annotation = clientClass.getAnnotation(SqsClient.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("test-service");
            assertThat(annotation.name()).isEqualTo("test-service");
            assertThat(annotation.queuePrefix()).isEqualTo("test-");
        }
        
        @Test
        @DisplayName("Should handle empty queue prefix")
        void shouldHandleEmptyQueuePrefix() {
            // Given
            Class<ClientWithoutPrefix> clientClass = ClientWithoutPrefix.class;
            
            // When
            SqsClient annotation = clientClass.getAnnotation(SqsClient.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.queuePrefix()).isEmpty();
        }
        
        @Test
        @DisplayName("Should use value as name when name not specified")
        void shouldUseValueAsNameWhenNameNotSpecified() {
            // Given
            Class<ClientWithValueOnly> clientClass = ClientWithValueOnly.class;
            
            // When
            SqsClient annotation = clientClass.getAnnotation(SqsClient.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("value-only");
            assertThat(annotation.name()).isEqualTo("value-only");
        }
    }

    @Nested
    @DisplayName("SendMessage Annotation Processing")
    class SendMessageAnnotationProcessing {
        
        @Test
        @DisplayName("Should extract SendMessage annotation attributes")
        void shouldExtractSendMessageAnnotation() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendMessage", String.class);
            
            // When
            SendMessage annotation = method.getAnnotation(SendMessage.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("test-queue");
            assertThat(annotation.queueName()).isEmpty();
            assertThat(annotation.delaySeconds()).isEqualTo(0);
            assertThat(annotation.fifo()).isFalse();
        }
        
        @Test
        @DisplayName("Should extract SendMessage with all attributes")
        void shouldExtractSendMessageWithAllAttributes() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendDelayedFifoMessage", String.class, String.class);
            
            // When
            SendMessage annotation = method.getAnnotation(SendMessage.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("fifo-queue.fifo");
            assertThat(annotation.delaySeconds()).isEqualTo(300);
            assertThat(annotation.fifo()).isTrue();
        }
        
        @Test
        @DisplayName("Should prefer queueName over value")
        void shouldPreferQueueNameOverValue() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendWithQueueName", String.class);
            
            // When
            SendMessage annotation = method.getAnnotation(SendMessage.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("value-queue");
            assertThat(annotation.queueName()).isEqualTo("queue-name");
        }
    }

    @Nested
    @DisplayName("SendBatch Annotation Processing")
    class SendBatchAnnotationProcessing {
        
        @Test
        @DisplayName("Should extract SendBatch annotation attributes")
        void shouldExtractSendBatchAnnotation() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendBatch", List.class);
            
            // When
            SendBatch annotation = method.getAnnotation(SendBatch.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("batch-queue");
            assertThat(annotation.batchSize()).isEqualTo(10);
        }
        
        @Test
        @DisplayName("Should extract custom batch size")
        void shouldExtractCustomBatchSize() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendCustomBatch", List.class);
            
            // When
            SendBatch annotation = method.getAnnotation(SendBatch.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.batchSize()).isEqualTo(25);
        }
    }

    @Nested
    @DisplayName("ReceiveMessages Annotation Processing")
    class ReceiveMessagesAnnotationProcessing {
        
        @Test
        @DisplayName("Should extract ReceiveMessages annotation attributes")
        void shouldExtractReceiveMessagesAnnotation() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("receiveMessages", Consumer.class);
            
            // When
            ReceiveMessages annotation = method.getAnnotation(ReceiveMessages.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("receive-queue");
            assertThat(annotation.maxMessages()).isEqualTo(10);
            assertThat(annotation.autoDelete()).isFalse();
            assertThat(annotation.waitTimeSeconds()).isEqualTo(0);
        }
        
        @Test
        @DisplayName("Should extract ReceiveMessages with all attributes")
        void shouldExtractReceiveMessagesWithAllAttributes() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("receiveWithConfig", Consumer.class);
            
            // When
            ReceiveMessages annotation = method.getAnnotation(ReceiveMessages.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("config-queue");
            assertThat(annotation.maxMessages()).isEqualTo(5);
            assertThat(annotation.autoDelete()).isTrue();
            assertThat(annotation.waitTimeSeconds()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("StartPolling Annotation Processing")
    class StartPollingAnnotationProcessing {
        
        @Test
        @DisplayName("Should extract StartPolling annotation attributes")
        void shouldExtractStartPollingAnnotation() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("startPolling", Consumer.class);
            
            // When
            StartPolling annotation = method.getAnnotation(StartPolling.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("polling-queue");
            assertThat(annotation.maxMessages()).isEqualTo(10);
            assertThat(annotation.autoDelete()).isTrue();
            assertThat(annotation.waitTimeSeconds()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("Parameter Annotation Processing")
    class ParameterAnnotationProcessing {
        
        @Test
        @DisplayName("Should identify MessageBody parameters")
        void shouldIdentifyMessageBodyParameters() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendMessage", String.class);
            Parameter[] parameters = method.getParameters();
            
            // When
            Parameter messageBodyParam = parameters[0];
            MessageBody annotation = messageBodyParam.getAnnotation(MessageBody.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.json()).isTrue();
        }
        
        @Test
        @DisplayName("Should identify QueueName parameters")
        void shouldIdentifyQueueNameParameters() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendToDynamicQueue", String.class, String.class);
            Parameter[] parameters = method.getParameters();
            
            // When
            Parameter queueNameParam = parameters[0];
            Parameter messageBodyParam = parameters[1];
            
            // Then
            assertThat(queueNameParam.getAnnotation(QueueName.class)).isNotNull();
            assertThat(messageBodyParam.getAnnotation(MessageBody.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should identify MessageGroupId parameters")
        void shouldIdentifyMessageGroupIdParameters() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendDelayedFifoMessage", String.class, String.class);
            Parameter[] parameters = method.getParameters();
            
            // When
            Parameter messageBodyParam = parameters[0];
            Parameter groupIdParam = parameters[1];
            
            // Then
            assertThat(messageBodyParam.getAnnotation(MessageBody.class)).isNotNull();
            assertThat(groupIdParam.getAnnotation(MessageGroupId.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should identify MessageAttributes parameters")
        void shouldIdentifyMessageAttributesParameters() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendMessageWithAttributes", String.class, Map.class);
            Parameter[] parameters = method.getParameters();
            
            // When
            Parameter messageBodyParam = parameters[0];
            Parameter attributesParam = parameters[1];
            
            // Then
            assertThat(messageBodyParam.getAnnotation(MessageBody.class)).isNotNull();
            assertThat(attributesParam.getAnnotation(MessageAttributes.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should identify MaxMessages parameters")
        void shouldIdentifyMaxMessagesParameters() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("receiveWithMaxMessages", String.class, Integer.class, Consumer.class);
            Parameter[] parameters = method.getParameters();
            
            // When
            Parameter queueNameParam = parameters[0];
            Parameter maxMessagesParam = parameters[1];
            Parameter processorParam = parameters[2];
            
            // Then
            assertThat(queueNameParam.getAnnotation(QueueName.class)).isNotNull();
            assertThat(maxMessagesParam.getAnnotation(MaxMessages.class)).isNotNull();
            assertThat(processorParam.getAnnotation(MessageProcessor.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should identify MessageProcessor parameters")
        void shouldIdentifyMessageProcessorParameters() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("receiveMessages", Consumer.class);
            Parameter[] parameters = method.getParameters();
            
            // When
            Parameter processorParam = parameters[0];
            MessageProcessor annotation = processorParam.getAnnotation(MessageProcessor.class);
            
            // Then
            assertThat(annotation).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle non-JSON MessageBody")
        void shouldHandleNonJsonMessageBody() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("sendPlainTextMessage", String.class);
            Parameter[] parameters = method.getParameters();
            
            // When
            Parameter messageBodyParam = parameters[0];
            MessageBody annotation = messageBodyParam.getAnnotation(MessageBody.class);
            
            // Then
            assertThat(annotation).isNotNull();
            assertThat(annotation.json()).isFalse();
        }
    }

    @Nested
    @DisplayName("Method Return Type Validation")
    class MethodReturnTypeValidation {
        
        @Test
        @DisplayName("Should validate CompletableFuture return types")
        void shouldValidateCompletableFutureReturnTypes() throws NoSuchMethodException {
            // Given
            Method sendMethod = TestSqsClient.class.getMethod("sendMessage", String.class);
            Method batchMethod = TestSqsClient.class.getMethod("sendBatch", List.class);
            Method receiveMethod = TestSqsClient.class.getMethod("receiveMessages", Consumer.class);
            
            // When & Then
            assertThat(sendMethod.getReturnType()).isEqualTo(CompletableFuture.class);
            assertThat(batchMethod.getReturnType()).isEqualTo(CompletableFuture.class);
            assertThat(receiveMethod.getReturnType()).isEqualTo(CompletableFuture.class);
        }
        
        @Test
        @DisplayName("Should validate void return types")
        void shouldValidateVoidReturnTypes() throws NoSuchMethodException {
            // Given
            Method pollingMethod = TestSqsClient.class.getMethod("startPolling", Consumer.class);
            Method syncReceiveMethod = TestSqsClient.class.getMethod("receiveMessagesSync", Consumer.class);
            
            // When & Then
            assertThat(pollingMethod.getReturnType()).isEqualTo(void.class);
            assertThat(syncReceiveMethod.getReturnType()).isEqualTo(void.class);
        }
    }

    @Nested
    @DisplayName("Complex Annotation Combinations")
    class ComplexAnnotationCombinations {
        
        @Test
        @DisplayName("Should handle method with multiple parameter annotations")
        void shouldHandleMultipleParameterAnnotations() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("complexMethod", String.class, String.class, Map.class, String.class);
            Parameter[] parameters = method.getParameters();
            
            // When & Then
            assertThat(parameters[0].getAnnotation(QueueName.class)).isNotNull();
            assertThat(parameters[1].getAnnotation(MessageBody.class)).isNotNull();
            assertThat(parameters[2].getAnnotation(MessageAttributes.class)).isNotNull();
            assertThat(parameters[3].getAnnotation(MessageGroupId.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should handle method with mixed annotation types")
        void shouldHandleMixedAnnotationTypes() throws NoSuchMethodException {
            // Given
            Method method = TestSqsClient.class.getMethod("mixedMethod", String.class, List.class, Consumer.class);
            
            // When
            SendBatch sendBatchAnnotation = method.getAnnotation(SendBatch.class);
            Parameter[] parameters = method.getParameters();
            
            // Then
            assertThat(sendBatchAnnotation).isNotNull();
            assertThat(parameters[0].getAnnotation(QueueName.class)).isNotNull();
            assertThat(parameters[1].getAnnotation(MessageBody.class)).isNotNull();
            assertThat(parameters[2].getAnnotation(MessageProcessor.class)).isNotNull();
        }
    }

    // Test interfaces
    @SqsClient(value = "test-service", queuePrefix = "test-")
    interface TestSqsClient {
        
        @SendMessage("test-queue")
        CompletableFuture<String> sendMessage(@MessageBody String message);
        
        @SendMessage(value = "fifo-queue.fifo", delaySeconds = 300, fifo = true)
        CompletableFuture<String> sendDelayedFifoMessage(
                @MessageBody String message,
                @MessageGroupId String groupId
        );
        
        @SendMessage(value = "value-queue", queueName = "queue-name")
        CompletableFuture<String> sendWithQueueName(@MessageBody String message);
        
        @SendMessage("plain-text")
        CompletableFuture<String> sendPlainTextMessage(@MessageBody(json = false) String message);
        
        @SendMessage("attributes")
        CompletableFuture<String> sendMessageWithAttributes(
                @MessageBody String message,
                @MessageAttributes Map<String, String> attributes
        );
        
        @SendMessage
        CompletableFuture<String> sendToDynamicQueue(
                @QueueName String queueName,
                @MessageBody String message
        );
        
        @SendBatch("batch-queue")
        CompletableFuture<List<String>> sendBatch(@MessageBody List<String> messages);
        
        @SendBatch(value = "custom-batch", batchSize = 25)
        CompletableFuture<List<String>> sendCustomBatch(@MessageBody List<String> messages);
        
        @ReceiveMessages("receive-queue")
        CompletableFuture<Void> receiveMessages(@MessageProcessor Consumer<com.ryuqq.aws.sqs.model.SqsMessage> processor);
        
        @ReceiveMessages(value = "config-queue", maxMessages = 5, autoDelete = true, waitTimeSeconds = 20)
        CompletableFuture<Void> receiveWithConfig(@MessageProcessor Consumer<com.ryuqq.aws.sqs.model.SqsMessage> processor);
        
        @ReceiveMessages("sync-receive")
        void receiveMessagesSync(@MessageProcessor Consumer<com.ryuqq.aws.sqs.model.SqsMessage> processor);
        
        @ReceiveMessages
        CompletableFuture<Void> receiveWithMaxMessages(
                @QueueName String queueName,
                @MaxMessages Integer maxMessages,
                @MessageProcessor Consumer<com.ryuqq.aws.sqs.model.SqsMessage> processor
        );
        
        @StartPolling(value = "polling-queue", maxMessages = 10, autoDelete = true, waitTimeSeconds = 20)
        void startPolling(@MessageProcessor Consumer<com.ryuqq.aws.sqs.model.SqsMessage> processor);
        
        @SendMessage(fifo = true)
        CompletableFuture<String> complexMethod(
                @QueueName String queueName,
                @MessageBody String message,
                @MessageAttributes Map<String, String> attributes,
                @MessageGroupId String groupId
        );
        
        @SendBatch
        CompletableFuture<List<String>> mixedMethod(
                @QueueName String queueName,
                @MessageBody List<String> messages,
                @MessageProcessor Consumer<com.ryuqq.aws.sqs.model.SqsMessage> processor
        );
    }
    
    @SqsClient("no-prefix")
    interface ClientWithoutPrefix {
        @SendMessage("test")
        CompletableFuture<String> sendMessage(@MessageBody String message);
    }
    
    @SqsClient("value-only")
    interface ClientWithValueOnly {
        @SendMessage("test")
        CompletableFuture<String> sendMessage(@MessageBody String message);
    }
}