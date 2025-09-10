package com.ryuqq.aws.sqs.example;

import com.ryuqq.aws.sqs.annotation.*;
import com.ryuqq.aws.sqs.model.SqsMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Example SQS client interface demonstrating annotation-based usage.
 * This interface will be automatically implemented by the SQS proxy.
 */
@SqsClient(value = "order-service", queuePrefix = "order-")
public interface OrderQueueService {

    /**
     * Send a single order to the processing queue.
     */
    @SendMessage("processing")
    CompletableFuture<String> sendOrderForProcessing(@MessageBody OrderDto order);

    /**
     * Send an order with custom attributes.
     */
    @SendMessage("processing")
    CompletableFuture<String> sendOrderWithPriority(
            @MessageBody OrderDto order,
            @MessageAttributes java.util.Map<String, String> attributes
    );

    /**
     * Send order to FIFO queue with message group ID.
     */
    @SendMessage(value = "processing-fifo.fifo", fifo = true)
    CompletableFuture<String> sendOrderToFifoQueue(
            @MessageBody OrderDto order,
            @MessageGroupId String customerId
    );

    /**
     * Send delayed order notification.
     */
    @SendMessage(value = "notifications", delaySeconds = 300)
    CompletableFuture<String> sendDelayedNotification(@MessageBody OrderDto order);

    /**
     * Send multiple orders in batch.
     */
    @SendBatch("processing")
    CompletableFuture<List<String>> sendOrderBatch(@MessageBody List<OrderDto> orders);

    /**
     * Receive and process orders.
     */
    @ReceiveMessages(value = "processing", maxMessages = 5)
    CompletableFuture<Void> processOrders(@MessageProcessor Consumer<SqsMessage> processor);

    /**
     * Receive and auto-delete processed orders.
     */
    @ReceiveMessages(value = "processing", autoDelete = true)
    void processAndDeleteOrders(@MessageProcessor Consumer<SqsMessage> processor);

    /**
     * Start continuous polling for order processing.
     */
    @StartPolling(value = "processing", autoDelete = true, waitTimeSeconds = 20)
    void startOrderPolling(@MessageProcessor Consumer<SqsMessage> processor);

    /**
     * Dynamic queue name with parameter.
     */
    @SendMessage
    CompletableFuture<String> sendToQueue(
            @QueueName String queueName,
            @MessageBody OrderDto order
    );

    /**
     * Flexible receive with dynamic configuration.
     */
    @ReceiveMessages
    CompletableFuture<Void> receiveFromQueue(
            @QueueName String queueName,
            @MaxMessages int maxMessages,
            @MessageProcessor Consumer<SqsMessage> processor
    );
}