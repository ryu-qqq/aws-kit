package com.ryuqq.aws.sqs.example;

import com.ryuqq.aws.sqs.model.SqsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Example demonstrating how to use the annotation-based SQS client.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsUsageExample implements CommandLineRunner {

    private final OrderQueueService orderQueueService;
    private final NotificationService notificationService;

    @Override
    public void run(String... args) throws Exception {
        log.info("=== SQS Annotation Client Usage Examples ===");

        // Example 1: Send a single order
        sendSingleOrder();

        // Example 2: Send with custom attributes
        sendOrderWithAttributes();

        // Example 3: Send to FIFO queue
        sendToFifoQueue();

        // Example 4: Send batch messages
        sendBatchMessages();

        // Example 5: Send delayed notification
        sendDelayedNotification();

        // Example 6: Process messages with auto-delete
        processMessagesWithAutoDelete();

        // Example 7: Start continuous polling
        startContinuousPolling();

        // Example 8: Use dynamic queue names
        useDynamicQueueNames();

        log.info("=== All examples completed ===");
    }

    private void sendSingleOrder() throws Exception {
        log.info("--- Example 1: Sending single order ---");
        
        OrderDto order = OrderDto.create("ORD-001", "CUST-001", BigDecimal.valueOf(99.99));
        
        String messageId = orderQueueService.sendOrderForProcessing(order).get();
        log.info("Order sent with message ID: {}", messageId);
    }

    private void sendOrderWithAttributes() throws Exception {
        log.info("--- Example 2: Sending order with attributes ---");
        
        OrderDto order = OrderDto.create("ORD-002", "CUST-002", BigDecimal.valueOf(199.99));
        Map<String, String> attributes = Map.of(
                "priority", "high",
                "region", "us-east-1",
                "source", "web"
        );
        
        String messageId = orderQueueService.sendOrderWithPriority(order, attributes).get();
        log.info("Order with attributes sent with message ID: {}", messageId);
    }

    private void sendToFifoQueue() throws Exception {
        log.info("--- Example 3: Sending to FIFO queue ---");
        
        OrderDto order = OrderDto.create("ORD-003", "CUST-003", BigDecimal.valueOf(299.99));
        String messageGroupId = "customer-group-3";
        
        String messageId = orderQueueService.sendOrderToFifoQueue(order, messageGroupId).get();
        log.info("FIFO order sent with message ID: {}", messageId);
    }

    private void sendBatchMessages() throws Exception {
        log.info("--- Example 4: Sending batch messages ---");
        
        List<OrderDto> orders = List.of(
                OrderDto.create("ORD-004", "CUST-004", BigDecimal.valueOf(50.00)),
                OrderDto.create("ORD-005", "CUST-005", BigDecimal.valueOf(75.00)),
                OrderDto.create("ORD-006", "CUST-006", BigDecimal.valueOf(100.00))
        );
        
        List<String> messageIds = orderQueueService.sendOrderBatch(orders).get();
        log.info("Batch orders sent with {} message IDs: {}", messageIds.size(), messageIds);
    }

    private void sendDelayedNotification() throws Exception {
        log.info("--- Example 5: Sending delayed notification ---");
        
        OrderDto order = OrderDto.create("ORD-007", "CUST-007", BigDecimal.valueOf(399.99));
        
        String messageId = orderQueueService.sendDelayedNotification(order).get();
        log.info("Delayed notification sent with message ID: {}", messageId);
    }

    private void processMessagesWithAutoDelete() {
        log.info("--- Example 6: Processing messages with auto-delete ---");
        
        orderQueueService.processAndDeleteOrders(this::processOrderMessage);
        log.info("Started processing orders with auto-delete");
    }

    private void startContinuousPolling() {
        log.info("--- Example 7: Starting continuous polling ---");
        
        orderQueueService.startOrderPolling(this::processOrderMessage);
        log.info("Started continuous polling for orders");
        
        // Also start notification polling
        notificationService.processEmails(this::processEmailMessage);
        notificationService.processSms(this::processSmsMessage);
        log.info("Started notification processing");
    }

    private void useDynamicQueueNames() throws Exception {
        log.info("--- Example 8: Using dynamic queue names ---");
        
        OrderDto order = OrderDto.create("ORD-008", "CUST-008", BigDecimal.valueOf(500.00));
        String dynamicQueue = "special-orders";
        
        String messageId = orderQueueService.sendToQueue(dynamicQueue, order).get();
        log.info("Message sent to dynamic queue '{}' with ID: {}", dynamicQueue, messageId);
        
        // Receive from the same dynamic queue
        orderQueueService.receiveFromQueue(dynamicQueue, 1, this::processOrderMessage).get();
        log.info("Processed messages from dynamic queue: {}", dynamicQueue);
    }

    private void processOrderMessage(SqsMessage message) {
        try {
            log.info("Processing order message: ID={}, Body={}", 
                    message.getMessageId(), 
                    message.getBody().substring(0, Math.min(100, message.getBody().length())) + "...");
            
            // Simulate processing time
            Thread.sleep(100);
            
            log.debug("Order message processed successfully");
        } catch (Exception e) {
            log.error("Failed to process order message: {}", message.getMessageId(), e);
        }
    }

    private void processEmailMessage(SqsMessage message) {
        log.info("Processing email message: ID={}", message.getMessageId());
        // Email processing logic...
    }

    private void processSmsMessage(SqsMessage message) {
        log.info("Processing SMS message: ID={}", message.getMessageId());
        // SMS processing logic...
    }

    private void awaitCompletion() throws Exception {
        // Helper method for examples - in real code, handle CompletableFuture properly
        Thread.sleep(50); // Small delay to ensure operations complete
    }
}