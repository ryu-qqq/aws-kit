package com.ryuqq.aws.sqs.proxy;

import com.ryuqq.aws.sqs.annotation.*;
import com.ryuqq.aws.sqs.model.SqsMessage;
import com.ryuqq.aws.sqs.serialization.MessageSerializer;
import com.ryuqq.aws.sqs.service.SqsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * InvocationHandler for SQS client proxies.
 * Routes method calls to appropriate SQS service operations.
 */
@Slf4j
@RequiredArgsConstructor
public class SqsClientInvocationHandler implements InvocationHandler {

    private final SqsService sqsService;
    private final MessageSerializer messageSerializer;
    private final SqsClient sqsClientAnnotation;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        log.debug("Invoking SQS method: {}.{}", method.getDeclaringClass().getSimpleName(), method.getName());

        // Handle Object methods
        if (method.getDeclaringClass() == Object.class) {
            return handleObjectMethod(method, args);
        }

        // Parse method metadata
        MethodMetadata metadata = parseMethodMetadata(method, args);

        // Route to appropriate SQS operation
        return routeToSqsOperation(metadata, method, args);
    }

    private MethodMetadata parseMethodMetadata(Method method, Object[] args) {
        MethodMetadata metadata = new MethodMetadata();
        
        // Check for operation annotations
        if (method.isAnnotationPresent(SendMessage.class)) {
            SendMessage annotation = method.getAnnotation(SendMessage.class);
            metadata.operation = OperationType.SEND_MESSAGE;
            metadata.queueName = getQueueName(annotation.queueName(), annotation.value());
            metadata.delaySeconds = annotation.delaySeconds();
            metadata.fifo = annotation.fifo();
        } else if (method.isAnnotationPresent(SendBatch.class)) {
            SendBatch annotation = method.getAnnotation(SendBatch.class);
            metadata.operation = OperationType.SEND_BATCH;
            metadata.queueName = getQueueName(annotation.queueName(), annotation.value());
            metadata.batchSize = annotation.batchSize();
        } else if (method.isAnnotationPresent(ReceiveMessages.class)) {
            ReceiveMessages annotation = method.getAnnotation(ReceiveMessages.class);
            metadata.operation = OperationType.RECEIVE_MESSAGES;
            metadata.queueName = getQueueName(annotation.queueName(), annotation.value());
            metadata.maxMessages = annotation.maxMessages();
            metadata.autoDelete = annotation.autoDelete();
            metadata.waitTimeSeconds = annotation.waitTimeSeconds();
        } else if (method.isAnnotationPresent(StartPolling.class)) {
            StartPolling annotation = method.getAnnotation(StartPolling.class);
            metadata.operation = OperationType.START_POLLING;
            metadata.queueName = getQueueName(annotation.queueName(), annotation.value());
            metadata.maxMessages = annotation.maxMessages();
            metadata.autoDelete = annotation.autoDelete();
            metadata.waitTimeSeconds = annotation.waitTimeSeconds();
        } else {
            throw new UnsupportedOperationException("Unsupported operation for method: " + method.getName());
        }

        // Parse parameters
        parseParameters(method, args, metadata);

        return metadata;
    }

    @SuppressWarnings("unchecked")
    private void parseParameters(Method method, Object[] args, MethodMetadata metadata) {
        Parameter[] parameters = method.getParameters();
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = args != null ? args[i] : null;

            if (parameter.isAnnotationPresent(QueueName.class)) {
                metadata.queueName = (String) arg;
            } else if (parameter.isAnnotationPresent(MessageBody.class)) {
                MessageBody annotation = parameter.getAnnotation(MessageBody.class);
                metadata.messageBody = serializeMessageBody(arg, annotation.json());
            } else if (parameter.isAnnotationPresent(MessageGroupId.class)) {
                metadata.messageGroupId = (String) arg;
            } else if (parameter.isAnnotationPresent(MaxMessages.class)) {
                metadata.maxMessages = (Integer) arg;
            } else if (parameter.isAnnotationPresent(MessageAttributes.class)) {
                metadata.messageAttributes = (Map<String, String>) arg;
            } else if (parameter.isAnnotationPresent(MessageProcessor.class)) {
                metadata.messageProcessor = (Consumer<SqsMessage>) arg;
            } else if (arg instanceof List && metadata.operation == OperationType.SEND_BATCH) {
                // Auto-detect batch messages
                metadata.batchMessages = (List<?>) arg;
            } else if (arg instanceof Consumer && metadata.operation == OperationType.RECEIVE_MESSAGES) {
                // Auto-detect message processor
                metadata.messageProcessor = (Consumer<SqsMessage>) arg;
            }
        }

        // Apply queue prefix if configured
        if (StringUtils.hasText(sqsClientAnnotation.queuePrefix()) && StringUtils.hasText(metadata.queueName)) {
            metadata.queueName = sqsClientAnnotation.queuePrefix() + metadata.queueName;
        }
    }

    private String serializeMessageBody(Object body, boolean json) {
        if (body == null) {
            return null;
        }

        if (body instanceof String) {
            return (String) body;
        }

        if (json) {
            return messageSerializer.serialize(body);
        } else {
            return body.toString();
        }
    }

    private Object routeToSqsOperation(MethodMetadata metadata, Method method, Object[] args) {
        validateMetadata(metadata);

        switch (metadata.operation) {
            case SEND_MESSAGE:
                return handleSendMessage(metadata);
            case SEND_BATCH:
                return handleSendBatch(metadata);
            case RECEIVE_MESSAGES:
                return handleReceiveMessages(metadata, method);
            case START_POLLING:
                return handleStartPolling(metadata);
            default:
                throw new UnsupportedOperationException("Unsupported operation: " + metadata.operation);
        }
    }

    private CompletableFuture<String> handleSendMessage(MethodMetadata metadata) {
        if (metadata.fifo) {
            if (metadata.messageGroupId == null) {
                throw new IllegalArgumentException("MessageGroupId is required for FIFO queue operations");
            }
            return sqsService.sendFifoMessage(metadata.queueName, metadata.messageBody, metadata.messageGroupId);
        }

        if (metadata.delaySeconds > 0) {
            return sqsService.sendDelayedMessage(
                    metadata.queueName, 
                    metadata.messageBody, 
                    Duration.ofSeconds(metadata.delaySeconds)
            );
        }

        if (metadata.messageAttributes != null && !metadata.messageAttributes.isEmpty()) {
            return sqsService.sendMessageWithAttributes(
                    metadata.queueName, 
                    metadata.messageBody, 
                    metadata.messageAttributes
            );
        }

        return sqsService.sendMessage(metadata.queueName, metadata.messageBody);
    }

    private CompletableFuture<List<String>> handleSendBatch(MethodMetadata metadata) {
        if (metadata.batchMessages == null || metadata.batchMessages.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<String> serializedMessages = metadata.batchMessages.stream()
                .map(msg -> serializeMessageBody(msg, true))
                .toList();

        return sqsService.sendMessageBatch(metadata.queueName, serializedMessages);
    }

    private Object handleReceiveMessages(MethodMetadata metadata, Method method) {
        if (metadata.autoDelete) {
            CompletableFuture<Void> future = sqsService.receiveProcessAndDelete(
                    metadata.queueName, 
                    metadata.messageProcessor
            );
            
            // Check if method returns CompletableFuture<Void> or void
            if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
                future.join(); // Block for void methods
                return null;
            }
            return future;
        } else {
            CompletableFuture<Void> future = sqsService.receiveAndProcessMessages(
                    metadata.queueName, 
                    metadata.messageProcessor
            );
            
            if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
                future.join(); // Block for void methods
                return null;
            }
            return future;
        }
    }

    private Object handleStartPolling(MethodMetadata metadata) {
        sqsService.startContinuousPolling(metadata.queueName, metadata.messageProcessor);
        return null; // Polling methods typically return void
    }

    private void validateMetadata(MethodMetadata metadata) {
        if (!StringUtils.hasText(metadata.queueName)) {
            throw new IllegalArgumentException("Queue name is required");
        }

        switch (metadata.operation) {
            case SEND_MESSAGE:
                if (!StringUtils.hasText(metadata.messageBody)) {
                    throw new IllegalArgumentException("Message body is required for send operations");
                }
                break;
            case SEND_BATCH:
                if (metadata.batchMessages == null || metadata.batchMessages.isEmpty()) {
                    throw new IllegalArgumentException("Batch messages are required for batch send operations");
                }
                break;
            case RECEIVE_MESSAGES:
            case START_POLLING:
                if (metadata.messageProcessor == null) {
                    throw new IllegalArgumentException("Message processor is required for receive operations");
                }
                break;
        }
    }

    private String getQueueName(String queueName, String value) {
        return StringUtils.hasText(queueName) ? queueName : value;
    }

    private Object handleObjectMethod(Method method, Object[] args) {
        String methodName = method.getName();
        switch (methodName) {
            case "toString":
                return "SqsClientProxy[" + sqsClientAnnotation.name() + "]";
            case "hashCode":
                return sqsClientAnnotation.hashCode();
            case "equals":
                return args != null && args.length == 1 && this.equals(args[0]);
            default:
                throw new UnsupportedOperationException("Method not supported: " + methodName);
        }
    }

    // Inner classes for metadata
    private static class MethodMetadata {
        OperationType operation;
        String queueName;
        String messageBody;
        String messageGroupId;
        Map<String, String> messageAttributes;
        List<?> batchMessages;
        Consumer<SqsMessage> messageProcessor;
        int delaySeconds;
        int maxMessages = 10;
        int batchSize = 10;
        int waitTimeSeconds;
        boolean fifo;
        boolean autoDelete;
    }

    private enum OperationType {
        SEND_MESSAGE,
        SEND_BATCH,
        RECEIVE_MESSAGES,
        START_POLLING
    }
}