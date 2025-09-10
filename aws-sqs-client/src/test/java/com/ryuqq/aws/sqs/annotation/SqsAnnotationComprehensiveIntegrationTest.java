package com.ryuqq.aws.sqs.annotation;

import com.ryuqq.aws.sqs.example.OrderDto;
import com.ryuqq.aws.sqs.example.OrderQueueService;
import com.ryuqq.aws.sqs.model.SqsMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Comprehensive integration tests for annotation-based SQS client.
 * Tests end-to-end functionality with real LocalStack SQS instance.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SqsAnnotationComprehensiveIntegrationTest {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SQS)
            .withEnv("DEBUG", "1");

    @Autowired
    private OrderQueueService orderQueueService;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("cloud.aws.sqs.endpoint", () -> localStack.getEndpointOverride(SQS).toString());
        registry.add("cloud.aws.credentials.access-key", () -> "test");
        registry.add("cloud.aws.credentials.secret-key", () -> "test");
        registry.add("cloud.aws.region.static", () -> "us-east-1");
    }

    @BeforeAll
    static void setUp() {
        localStack.start();
    }

    @Nested
    @DisplayName("Basic Message Operations")
    class BasicMessageOperations {
        
        @Test
        @DisplayName("Should send and receive single message end-to-end")
        void shouldSendAndReceiveSingleMessage() throws Exception {
            // Given
            OrderDto order = OrderDto.create("INT-001", "CUSTOMER-001", BigDecimal.valueOf(99.99));
            AtomicReference<SqsMessage> receivedMessage = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            // When - Send message
            CompletableFuture<String> sendFuture = orderQueueService.sendOrderForProcessing(order);
            String messageId = sendFuture.get(10, TimeUnit.SECONDS);
            
            assertThat(messageId).isNotNull().isNotEmpty();
            
            // When - Receive message
            CompletableFuture<Void> receiveFuture = orderQueueService.processOrders(message -> {
                receivedMessage.set(message);
                latch.countDown();
            });
            
            // Then
            boolean messageReceived = latch.await(15, TimeUnit.SECONDS);
            assertThat(messageReceived).isTrue();
            assertThat(receivedMessage.get()).isNotNull();
            assertThat(receivedMessage.get().getBody()).contains("INT-001");
            assertThat(receivedMessage.get().getBody()).contains("CUSTOMER-001");
            assertThat(receivedMessage.get().getBody()).contains("99.99");
        }
        
        @Test
        @DisplayName("Should handle message with custom attributes")
        void shouldHandleMessageWithCustomAttributes() throws Exception {
            // Given
            OrderDto order = OrderDto.create("INT-002", "CUSTOMER-002", BigDecimal.valueOf(199.99));
            Map<String, String> attributes = Map.of(
                    "priority", "high",
                    "region", "us-east-1",
                    "source", "integration-test"
            );
            
            // When
            CompletableFuture<String> future = orderQueueService.sendOrderWithPriority(order, attributes);
            String messageId = future.get(10, TimeUnit.SECONDS);
            
            // Then
            assertThat(messageId).isNotNull().isNotEmpty();
        }
        
        @Test
        @DisplayName("Should handle delayed message sending")
        void shouldHandleDelayedMessageSending() throws Exception {
            // Given
            OrderDto order = OrderDto.create("INT-003", "CUSTOMER-003", BigDecimal.valueOf(299.99));
            
            // When
            long startTime = System.currentTimeMillis();
            CompletableFuture<String> future = orderQueueService.sendDelayedNotification(order);
            String messageId = future.get(10, TimeUnit.SECONDS);
            long sendTime = System.currentTimeMillis() - startTime;
            
            // Then
            assertThat(messageId).isNotNull().isNotEmpty();
            assertThat(sendTime).isLessThan(5000); // Send operation itself should be fast
        }
        
        @Test
        @DisplayName("Should handle dynamic queue operations")
        void shouldHandleDynamicQueueOperations() throws Exception {
            // Given
            String dynamicQueueName = "integration-test-queue-" + System.currentTimeMillis();
            OrderDto order = OrderDto.create("INT-004", "CUSTOMER-004", BigDecimal.valueOf(399.99));
            AtomicReference<SqsMessage> receivedMessage = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            // When - Send to dynamic queue
            CompletableFuture<String> sendFuture = orderQueueService.sendToQueue(dynamicQueueName, order);
            String messageId = sendFuture.get(10, TimeUnit.SECONDS);
            
            assertThat(messageId).isNotNull();
            
            // When - Receive from dynamic queue
            CompletableFuture<Void> receiveFuture = orderQueueService.receiveFromQueue(
                    dynamicQueueName, 
                    1, 
                    message -> {
                        receivedMessage.set(message);
                        latch.countDown();
                    }
            );
            
            // Then
            boolean messageReceived = latch.await(15, TimeUnit.SECONDS);
            assertThat(messageReceived).isTrue();
            assertThat(receivedMessage.get().getBody()).contains("INT-004");
        }
    }

    @Nested
    @DisplayName("Batch Operations")
    class BatchOperations {
        
        @Test
        @DisplayName("Should handle batch message sending")
        void shouldHandleBatchMessageSending() throws Exception {
            // Given
            List<OrderDto> orders = List.of(
                    OrderDto.create("BATCH-001", "CUSTOMER-001", BigDecimal.valueOf(50.00)),
                    OrderDto.create("BATCH-002", "CUSTOMER-002", BigDecimal.valueOf(75.00)),
                    OrderDto.create("BATCH-003", "CUSTOMER-003", BigDecimal.valueOf(100.00)),
                    OrderDto.create("BATCH-004", "CUSTOMER-004", BigDecimal.valueOf(125.00)),
                    OrderDto.create("BATCH-005", "CUSTOMER-005", BigDecimal.valueOf(150.00))
            );
            
            // When
            CompletableFuture<List<String>> future = orderQueueService.sendOrderBatch(orders);
            List<String> messageIds = future.get(15, TimeUnit.SECONDS);
            
            // Then
            assertThat(messageIds).hasSize(5);
            assertThat(messageIds).allSatisfy(id -> {
                assertThat(id).isNotNull();
                assertThat(id).isNotEmpty();
            });
        }
        
        @Test
        @DisplayName("Should handle large batch operations")
        void shouldHandleLargeBatchOperations() throws Exception {
            // Given - Create a large batch (more than SQS batch limit of 10)
            List<OrderDto> largeOrderBatch = new ArrayList<>();
            for (int i = 1; i <= 25; i++) {
                largeOrderBatch.add(OrderDto.create(
                        "LARGE-BATCH-" + String.format("%03d", i),
                        "CUSTOMER-BATCH",
                        BigDecimal.valueOf(10.00 * i)
                ));
            }
            
            // When
            CompletableFuture<List<String>> future = orderQueueService.sendOrderBatch(largeOrderBatch);
            List<String> messageIds = future.get(30, TimeUnit.SECONDS);
            
            // Then
            assertThat(messageIds).hasSize(25);
            assertThat(messageIds).allSatisfy(id -> {
                assertThat(id).isNotNull();
                assertThat(id).isNotEmpty();
            });
        }
        
        @Test
        @DisplayName("Should handle empty batch gracefully")
        void shouldHandleEmptyBatchGracefully() throws Exception {
            // Given
            List<OrderDto> emptyBatch = List.of();
            
            // When
            CompletableFuture<List<String>> future = orderQueueService.sendOrderBatch(emptyBatch);
            List<String> messageIds = future.get(5, TimeUnit.SECONDS);
            
            // Then
            assertThat(messageIds).isEmpty();
        }
    }

    @Nested
    @DisplayName("Concurrent Operations")
    class ConcurrentOperations {
        
        @Test
        @DisplayName("Should handle concurrent message sending")
        void shouldHandleConcurrentMessageSending() throws Exception {
            // Given
            int threadCount = 5;
            int messagesPerThread = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            List<CompletableFuture<String>> futures = new ArrayList<>();
            
            // When
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int m = 0; m < messagesPerThread; m++) {
                            OrderDto order = OrderDto.create(
                                    "CONCURRENT-T" + threadId + "-M" + m,
                                    "CUSTOMER-" + threadId,
                                    BigDecimal.valueOf(100.00 + m)
                            );
                            CompletableFuture<String> future = orderQueueService.sendOrderForProcessing(order);
                            synchronized (futures) {
                                futures.add(future);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            // Wait for all threads to complete
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            
            // Then - All messages should be sent successfully
            List<String> messageIds = new ArrayList<>();
            for (CompletableFuture<String> future : futures) {
                messageIds.add(future.get(5, TimeUnit.SECONDS));
            }
            
            assertThat(messageIds).hasSize(threadCount * messagesPerThread);
            assertThat(messageIds).allSatisfy(id -> {
                assertThat(id).isNotNull();
                assertThat(id).isNotEmpty();
            });
        }
        
        @Test
        @DisplayName("Should handle concurrent batch operations")
        void shouldHandleConcurrentBatchOperations() throws Exception {
            // Given
            int batchCount = 3;
            List<CompletableFuture<List<String>>> batchFutures = new ArrayList<>();
            
            // When
            for (int b = 0; b < batchCount; b++) {
                List<OrderDto> orders = List.of(
                        OrderDto.create("BATCH-" + b + "-001", "CUSTOMER", BigDecimal.valueOf(50.00)),
                        OrderDto.create("BATCH-" + b + "-002", "CUSTOMER", BigDecimal.valueOf(75.00)),
                        OrderDto.create("BATCH-" + b + "-003", "CUSTOMER", BigDecimal.valueOf(100.00))
                );
                batchFutures.add(orderQueueService.sendOrderBatch(orders));
            }
            
            // Then
            for (CompletableFuture<List<String>> future : batchFutures) {
                List<String> messageIds = future.get(15, TimeUnit.SECONDS);
                assertThat(messageIds).hasSize(3);
                assertThat(messageIds).allSatisfy(id -> {
                    assertThat(id).isNotNull();
                    assertThat(id).isNotEmpty();
                });
            }
        }
    }

    @Nested
    @DisplayName("Message Processing")
    class MessageProcessing {
        
        @Test
        @DisplayName("Should process messages with auto-delete")
        void shouldProcessMessagesWithAutoDelete() throws Exception {
            // Given
            OrderDto order = OrderDto.create("PROCESS-001", "CUSTOMER", BigDecimal.valueOf(99.99));
            AtomicInteger processedCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(1);
            
            // Send message first
            orderQueueService.sendOrderForProcessing(order).get(10, TimeUnit.SECONDS);
            
            // When - Process with auto-delete
            orderQueueService.processAndDeleteOrders(message -> {
                processedCount.incrementAndGet();
                latch.countDown();
            });
            
            // Then
            boolean messageProcessed = latch.await(15, TimeUnit.SECONDS);
            assertThat(messageProcessed).isTrue();
            assertThat(processedCount.get()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should handle message processing errors gracefully")
        void shouldHandleMessageProcessingErrorsGracefully() throws Exception {
            // Given
            OrderDto order = OrderDto.create("ERROR-001", "CUSTOMER", BigDecimal.valueOf(99.99));
            AtomicInteger attemptCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(1);
            
            // Send message first
            orderQueueService.sendOrderForProcessing(order).get(10, TimeUnit.SECONDS);
            
            // When - Process with error-prone processor
            orderQueueService.processOrders(message -> {
                int attempts = attemptCount.incrementAndGet();
                if (attempts == 1) {
                    // First attempt fails
                    throw new RuntimeException("Processing failed");
                }
                // Second attempt succeeds
                latch.countDown();
            });
            
            // Then - Should eventually process successfully (due to SQS retry mechanism)
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> latch.getCount() == 0);
        }
        
        @Test
        @DisplayName("Should handle high-throughput message processing")
        void shouldHandleHighThroughputMessageProcessing() throws Exception {
            // Given
            int messageCount = 50;
            AtomicInteger processedCount = new AtomicInteger(0);
            CountDownLatch sendLatch = new CountDownLatch(messageCount);
            CountDownLatch processLatch = new CountDownLatch(messageCount);
            
            // Send many messages
            ExecutorService sendExecutor = Executors.newFixedThreadPool(5);
            for (int i = 0; i < messageCount; i++) {
                final int messageId = i;
                sendExecutor.submit(() -> {
                    try {
                        OrderDto order = OrderDto.create(
                                "THROUGHPUT-" + String.format("%03d", messageId),
                                "CUSTOMER",
                                BigDecimal.valueOf(100.00 + messageId)
                        );
                        orderQueueService.sendOrderForProcessing(order).get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        sendLatch.countDown();
                    }
                });
            }
            
            // Wait for all messages to be sent
            assertThat(sendLatch.await(60, TimeUnit.SECONDS)).isTrue();
            sendExecutor.shutdown();
            
            // When - Process messages
            orderQueueService.processOrders(message -> {
                processedCount.incrementAndGet();
                processLatch.countDown();
            });
            
            // Then - Should process all messages
            await().atMost(Duration.ofSeconds(120))
                    .until(() -> processedCount.get() >= messageCount);
        }
    }

    @Nested
    @DisplayName("JSON Serialization Integration")
    class JsonSerializationIntegration {
        
        @Test
        @DisplayName("Should handle complex object serialization")
        void shouldHandleComplexObjectSerialization() throws Exception {
            // Given - Complex order with nested data
            OrderDto complexOrder = OrderDto.create("COMPLEX-001", "CUSTOMER-COMPLEX", BigDecimal.valueOf(999.99));
            AtomicReference<SqsMessage> receivedMessage = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            // When
            CompletableFuture<String> sendFuture = orderQueueService.sendOrderForProcessing(complexOrder);
            String messageId = sendFuture.get(10, TimeUnit.SECONDS);
            
            assertThat(messageId).isNotNull();
            
            // Receive and verify
            orderQueueService.processOrders(message -> {
                receivedMessage.set(message);
                latch.countDown();
            });
            
            // Then
            boolean messageReceived = latch.await(15, TimeUnit.SECONDS);
            assertThat(messageReceived).isTrue();
            
            String messageBody = receivedMessage.get().getBody();
            assertThat(messageBody).contains("\"id\":\"COMPLEX-001\"");
            assertThat(messageBody).contains("\"customerId\":\"CUSTOMER-COMPLEX\"");
            assertThat(messageBody).contains("\"price\":999.99");
        }
        
        @Test
        @DisplayName("Should handle special characters in serialization")
        void shouldHandleSpecialCharactersInSerialization() throws Exception {
            // Given - Order with special characters
            OrderDto specialOrder = OrderDto.create(
                    "SPECIAL-001", 
                    "CUSTOMER-Special: \"quoted\", \\escaped\\, 你好", 
                    BigDecimal.valueOf(123.45)
            );
            AtomicReference<SqsMessage> receivedMessage = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);
            
            // When
            CompletableFuture<String> sendFuture = orderQueueService.sendOrderForProcessing(specialOrder);
            String messageId = sendFuture.get(10, TimeUnit.SECONDS);
            
            assertThat(messageId).isNotNull();
            
            // Receive and verify
            orderQueueService.processOrders(message -> {
                receivedMessage.set(message);
                latch.countDown();
            });
            
            // Then
            boolean messageReceived = latch.await(15, TimeUnit.SECONDS);
            assertThat(messageReceived).isTrue();
            
            String messageBody = receivedMessage.get().getBody();
            assertThat(messageBody).contains("SPECIAL-001");
            assertThat(messageBody).contains("\\\"quoted\\\""); // JSON escaped quotes
            assertThat(messageBody).contains("你好"); // Unicode characters
        }
    }

    @Nested
    @DisplayName("End-to-End Workflow Tests")
    class EndToEndWorkflowTests {
        
        @Test
        @DisplayName("Should handle complete order processing workflow")
        void shouldHandleCompleteOrderProcessingWorkflow() throws Exception {
            // Given - Simulate a complete e-commerce order workflow
            List<OrderDto> orders = List.of(
                    OrderDto.create("WF-001", "PREMIUM-CUSTOMER", BigDecimal.valueOf(299.99)),
                    OrderDto.create("WF-002", "REGULAR-CUSTOMER", BigDecimal.valueOf(99.99)),
                    OrderDto.create("WF-003", "VIP-CUSTOMER", BigDecimal.valueOf(599.99))
            );
            
            AtomicInteger processedOrders = new AtomicInteger(0);
            CountDownLatch workflowLatch = new CountDownLatch(3);
            
            // When - Send orders for processing
            for (OrderDto order : orders) {
                orderQueueService.sendOrderForProcessing(order).get(10, TimeUnit.SECONDS);
            }
            
            // Process orders
            orderQueueService.processOrders(message -> {
                processedOrders.incrementAndGet();
                workflowLatch.countDown();
                
                // Simulate order processing logic
                String body = message.getBody();
                if (body.contains("PREMIUM") || body.contains("VIP")) {
                    System.out.println("Processing high-priority order: " + body);
                } else {
                    System.out.println("Processing regular order: " + body);
                }
            });
            
            // Then
            boolean workflowCompleted = workflowLatch.await(30, TimeUnit.SECONDS);
            assertThat(workflowCompleted).isTrue();
            assertThat(processedOrders.get()).isEqualTo(3);
        }
        
        @Test
        @DisplayName("Should handle mixed operation types in workflow")
        void shouldHandleMixedOperationTypesInWorkflow() throws Exception {
            // Given
            List<OrderDto> batchOrders = List.of(
                    OrderDto.create("MIXED-001", "CUSTOMER-A", BigDecimal.valueOf(50.00)),
                    OrderDto.create("MIXED-002", "CUSTOMER-B", BigDecimal.valueOf(75.00))
            );
            OrderDto singleOrder = OrderDto.create("MIXED-003", "CUSTOMER-C", BigDecimal.valueOf(100.00));
            
            AtomicInteger totalProcessed = new AtomicInteger(0);
            CountDownLatch mixedLatch = new CountDownLatch(3);
            
            // When - Mixed operations
            // 1. Send batch
            CompletableFuture<List<String>> batchFuture = orderQueueService.sendOrderBatch(batchOrders);
            List<String> batchIds = batchFuture.get(15, TimeUnit.SECONDS);
            assertThat(batchIds).hasSize(2);
            
            // 2. Send single message
            CompletableFuture<String> singleFuture = orderQueueService.sendOrderForProcessing(singleOrder);
            String singleId = singleFuture.get(10, TimeUnit.SECONDS);
            assertThat(singleId).isNotNull();
            
            // 3. Process all messages
            orderQueueService.processOrders(message -> {
                totalProcessed.incrementAndGet();
                mixedLatch.countDown();
            });
            
            // Then
            boolean allProcessed = mixedLatch.await(30, TimeUnit.SECONDS);
            assertThat(allProcessed).isTrue();
            assertThat(totalProcessed.get()).isEqualTo(3);
        }
    }
}