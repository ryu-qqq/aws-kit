package com.ryuqq.aws.sqs.service;

import com.ryuqq.aws.sqs.client.SqsClient;
import com.ryuqq.aws.sqs.model.SqsMessage;
import com.ryuqq.aws.sqs.properties.SqsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * SQS 서비스 통합 테스트
 */
@ExtendWith(MockitoExtension.class)
@Testcontainers
class SqsServiceIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.SQS)
            .withEnv("DEFAULT_REGION", "us-east-1");

    @Mock
    private SqsClient sqsClient;

    private SqsService sqsService;
    private SqsProperties sqsProperties;

    @BeforeEach
    void setUp() {
        sqsProperties = new SqsProperties();
        sqsProperties.setDefaultQueueUrlPrefix(localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString() + "/000000000000/");
        
        sqsService = new SqsServiceImpl(sqsClient, sqsProperties);
    }

    @Test
    void shouldSendMessage() {
        // Given
        String queueName = "test-queue";
        String messageBody = "Test message";
        String messageId = UUID.randomUUID().toString();

        when(sqsClient.getQueueUrl(queueName))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));
        when(sqsClient.sendMessage(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(messageId));

        // When
        CompletableFuture<String> result = sqsService.sendMessage(queueName, messageBody);

        // Then
        assertThat(result).isCompletedWithValue(messageId);
        verify(sqsClient).sendMessage("queue-url", messageBody);
    }

    @Test
    void shouldSendDelayedMessage() {
        // Given
        String queueName = "test-queue";
        String messageBody = "Delayed message";
        Duration delay = Duration.ofSeconds(10);
        String messageId = UUID.randomUUID().toString();

        when(sqsClient.getQueueUrl(queueName))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));
        when(sqsClient.sendMessage(anyString(), anyString(), any(Map.class)))
                .thenReturn(CompletableFuture.completedFuture(messageId));

        // When
        CompletableFuture<String> result = sqsService.sendDelayedMessage(queueName, messageBody, delay);

        // Then
        assertThat(result).isCompletedWithValue(messageId);
        verify(sqsClient).sendMessage(eq("queue-url"), eq(messageBody), argThat(attrs ->
                attrs.containsKey("DelaySeconds") && attrs.get("DelaySeconds").equals("10")
        ));
    }

    @Test
    void shouldSendMessageBatch() {
        // Given
        String queueName = "test-queue";
        List<String> messages = List.of("Message 1", "Message 2", "Message 3");
        List<String> messageIds = List.of("id1", "id2", "id3");

        when(sqsClient.getQueueUrl(queueName))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));
        when(sqsClient.sendMessages(anyString(), any(List.class)))
                .thenReturn(CompletableFuture.completedFuture(messageIds));

        // When
        CompletableFuture<List<String>> result = sqsService.sendMessageBatch(queueName, messages);

        // Then
        assertThat(result).isCompletedWithValue(messageIds);
        verify(sqsClient).sendMessages("queue-url", messages);
    }

    @Test
    void shouldReceiveAndProcessMessages() throws InterruptedException {
        // Given
        String queueName = "test-queue";
        CountDownLatch latch = new CountDownLatch(2);
        AtomicReference<String> processedMessage = new AtomicReference<>();

        SqsMessage message1 = SqsMessage.builder()
                .messageId("msg1")
                .body("Message 1")
                .receiptHandle("receipt1")
                .build();

        SqsMessage message2 = SqsMessage.builder()
                .messageId("msg2")
                .body("Message 2")
                .receiptHandle("receipt2")
                .build();

        when(sqsClient.getQueueUrl(queueName))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));
        when(sqsClient.receiveMessages(anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(message1, message2)));

        // When
        sqsService.receiveAndProcessMessages(queueName, message -> {
            processedMessage.set(message.getBody());
            latch.countDown();
        });

        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        verify(sqsClient).receiveMessages("queue-url", 10);
    }

    @Test
    void shouldReceiveProcessAndDeleteMessages() {
        // Given
        String queueName = "test-queue";
        SqsMessage message = SqsMessage.builder()
                .messageId("msg1")
                .body("Message to delete")
                .receiptHandle("receipt1")
                .build();

        when(sqsClient.getQueueUrl(queueName))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));
        when(sqsClient.receiveMessages(anyString(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(message)));
        when(sqsClient.deleteMessage(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // When
        CompletableFuture<Void> result = sqsService.receiveProcessAndDelete(queueName, msg -> {
            assertThat(msg.getBody()).isEqualTo("Message to delete");
        });

        // Then
        assertThat(result).isCompleted();
        verify(sqsClient).deleteMessage("queue-url", "receipt1");
    }

    @Test
    void shouldHandleBatchSizeExceeding() {
        // Given
        String queueName = "test-queue";
        List<String> messages = List.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
                "11", "12", "13", "14", "15" // 15 messages, exceeds batch size of 10
        );

        when(sqsClient.getQueueUrl(queueName))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));
        when(sqsClient.sendMessages(anyString(), any(List.class)))
                .thenAnswer(invocation -> {
                    List<String> batch = invocation.getArgument(1);
                    return CompletableFuture.completedFuture(
                            batch.stream().map(msg -> "id-" + msg).toList()
                    );
                });

        // When
        CompletableFuture<List<String>> result = sqsService.sendMessageBatch(queueName, messages);

        // Then
        assertThat(result).isCompleted();
        assertThat(result.join()).hasSize(15);
        verify(sqsClient, times(2)).sendMessages(anyString(), any(List.class));
    }

    @Test
    void shouldCreateQueueIfNotExists() {
        // Given
        String queueName = "new-queue";
        Map<String, String> attributes = Map.of(
                "MessageRetentionPeriod", "86400"
        );

        when(sqsClient.getQueueUrl(queueName))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Queue not found")))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));
        when(sqsClient.createQueue(anyString(), any(Map.class)))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));

        // When
        CompletableFuture<String> result = sqsService.createQueueIfNotExists(queueName, attributes);

        // Then
        assertThat(result).isCompletedWithValue("queue-url");
        verify(sqsClient).createQueue(queueName, attributes);
    }

    @Test
    void shouldGetQueueMessageCount() {
        // Given
        String queueName = "test-queue";
        
        when(sqsClient.getQueueUrl(queueName))
                .thenReturn(CompletableFuture.completedFuture("queue-url"));
        when(sqsClient.getApproximateNumberOfMessages(anyString()))
                .thenReturn(CompletableFuture.completedFuture(42));

        // When
        CompletableFuture<Long> result = sqsService.getQueueMessageCount(queueName);

        // Then
        assertThat(result).isCompletedWithValue(42L);
        verify(sqsClient).getApproximateNumberOfMessages("queue-url");
    }
}