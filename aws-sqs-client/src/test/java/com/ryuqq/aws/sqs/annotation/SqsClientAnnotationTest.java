package com.ryuqq.aws.sqs.annotation;

import com.ryuqq.aws.sqs.example.OrderDto;
import com.ryuqq.aws.sqs.example.OrderQueueService;
import com.ryuqq.aws.sqs.model.SqsMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * Integration test for annotation-based SQS client functionality.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class SqsClientAnnotationTest {

    @Container
    static LocalStackContainer localStack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SQS)
            .withEnv("DEBUG", "1");

    @Autowired(required = false)
    private OrderQueueService orderQueueService;

    @Test
    void shouldInjectSqsClientProxy() {
        assertThat(orderQueueService).isNotNull();
        assertThat(orderQueueService.getClass().getName()).contains("Proxy");
    }

    @Test
    void shouldSendSingleMessage() throws Exception {
        // Given
        OrderDto order = OrderDto.create("ORDER-001", "CUSTOMER-001", BigDecimal.valueOf(99.99));

        // When
        CompletableFuture<String> future = orderQueueService.sendOrderForProcessing(order);
        String messageId = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(messageId).isNotNull();
        assertThat(messageId).isNotEmpty();
    }

    @Test
    void shouldSendMessageWithAttributes() throws Exception {
        // Given
        OrderDto order = OrderDto.create("ORDER-002", "CUSTOMER-002", BigDecimal.valueOf(199.99));
        Map<String, String> attributes = Map.of(
                "priority", "high",
                "region", "us-east-1"
        );

        // When
        CompletableFuture<String> future = orderQueueService.sendOrderWithPriority(order, attributes);
        String messageId = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(messageId).isNotNull();
    }

    @Test
    void shouldSendFifoMessage() throws Exception {
        // Given
        OrderDto order = OrderDto.create("ORDER-003", "CUSTOMER-003", BigDecimal.valueOf(299.99));
        String messageGroupId = "customer-003";

        // When
        CompletableFuture<String> future = orderQueueService.sendOrderToFifoQueue(order, messageGroupId);
        String messageId = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(messageId).isNotNull();
    }

    @Test
    void shouldSendDelayedMessage() throws Exception {
        // Given
        OrderDto order = OrderDto.create("ORDER-004", "CUSTOMER-004", BigDecimal.valueOf(399.99));

        // When
        CompletableFuture<String> future = orderQueueService.sendDelayedNotification(order);
        String messageId = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(messageId).isNotNull();
    }

    @Test
    void shouldSendBatchMessages() throws Exception {
        // Given
        List<OrderDto> orders = List.of(
                OrderDto.create("ORDER-005", "CUSTOMER-005", BigDecimal.valueOf(50.00)),
                OrderDto.create("ORDER-006", "CUSTOMER-006", BigDecimal.valueOf(75.00)),
                OrderDto.create("ORDER-007", "CUSTOMER-007", BigDecimal.valueOf(100.00))
        );

        // When
        CompletableFuture<List<String>> future = orderQueueService.sendOrderBatch(orders);
        List<String> messageIds = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(messageIds).hasSize(3);
        assertThat(messageIds).allSatisfy(messageId -> {
            assertThat(messageId).isNotNull();
            assertThat(messageId).isNotEmpty();
        });
    }

    @Test
    void shouldReceiveAndProcessMessages() throws Exception {
        // Given
        OrderDto order = OrderDto.create("ORDER-008", "CUSTOMER-008", BigDecimal.valueOf(888.88));
        orderQueueService.sendOrderForProcessing(order).get(5, TimeUnit.SECONDS);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SqsMessage> receivedMessage = new AtomicReference<>();

        // When
        CompletableFuture<Void> future = orderQueueService.processOrders(message -> {
            receivedMessage.set(message);
            latch.countDown();
        });

        // Then
        boolean messageReceived = latch.await(15, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
        assertThat(receivedMessage.get()).isNotNull();
        assertThat(receivedMessage.get().getBody()).contains("ORDER-008");
    }

    @Test
    void shouldUseDynamicQueueName() throws Exception {
        // Given
        OrderDto order = OrderDto.create("ORDER-009", "CUSTOMER-009", BigDecimal.valueOf(999.99));
        String dynamicQueueName = "dynamic-queue";

        // When
        CompletableFuture<String> future = orderQueueService.sendToQueue(dynamicQueueName, order);
        String messageId = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(messageId).isNotNull();
    }

    @Test
    void shouldReceiveFromDynamicQueue() throws Exception {
        // Given
        String queueName = "receive-queue";
        OrderDto order = OrderDto.create("ORDER-010", "CUSTOMER-010", BigDecimal.valueOf(1000.00));
        orderQueueService.sendToQueue(queueName, order).get(5, TimeUnit.SECONDS);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SqsMessage> receivedMessage = new AtomicReference<>();

        // When
        CompletableFuture<Void> future = orderQueueService.receiveFromQueue(
                queueName,
                1,
                message -> {
                    receivedMessage.set(message);
                    latch.countDown();
                }
        );

        // Then
        boolean messageReceived = latch.await(15, TimeUnit.SECONDS);
        assertThat(messageReceived).isTrue();
        assertThat(receivedMessage.get()).isNotNull();
        assertThat(receivedMessage.get().getBody()).contains("ORDER-010");
    }
}