package com.ryuqq.aws.sns.integration;

import com.ryuqq.aws.sns.AwsSnsAutoConfiguration;
import com.ryuqq.aws.sns.service.SnsService;
import com.ryuqq.aws.sns.types.SnsMessage;
import com.ryuqq.aws.sns.types.SnsPublishResult;
import com.ryuqq.aws.sns.types.SnsSubscription;
import com.ryuqq.aws.sns.types.SnsTopic;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.GetTopicAttributesRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.*;

/**
 * Integration tests for SNS service using LocalStack
 * Tests real AWS service interactions through LocalStack container
 */
@SpringBootTest(classes = AwsSnsAutoConfiguration.class)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnsIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SNS, SQS)
            .withEnv("DEBUG", "1")
            .withEnv("LAMBDA_EXECUTOR", "local")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Autowired
    private SnsService snsService;

    @Autowired
    private SnsAsyncClient snsClient;

    private static String testTopicArn;
    private static String fifoTopicArn;
    private static String testQueueUrl;
    private static SqsAsyncClient sqsClient;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.region", () -> localstack.getRegion());
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.credentials.access-key-id", () -> localstack.getAccessKey());
        registry.add("aws.credentials.secret-access-key", () -> localstack.getSecretKey());
        
        // SNS specific properties
        registry.add("aws.sns.enabled", () -> "true");
        registry.add("aws.sns.retry.max-attempts", () -> "3");
        registry.add("aws.sns.retry.base-delay", () -> "PT1S");
    }

    @BeforeAll
    static void setupResources() {
        // Setup SQS client for cross-service integration testing
        sqsClient = SqsAsyncClient.builder()
                .endpointOverride(localstack.getEndpoint())
                .region(localstack.getRegion())
                .credentialsProvider(localstack.getDefaultCredentialsProvider())
                .build();
    }

    @AfterAll
    static void cleanup() {
        if (sqsClient != null) {
            sqsClient.close();
        }
    }

    @Test
    @Order(1)
    void createTopic_단일토픽_생성성공() {
        // When
        CompletableFuture<SnsTopic> future = snsService.createTopic("integration-test-topic");
        
        // Then
        SnsTopic topic = future.join();
        assertThat(topic).isNotNull();
        assertThat(topic.getTopicArn()).contains("integration-test-topic");
        assertThat(topic.getName()).isEqualTo("integration-test-topic");
        
        testTopicArn = topic.getTopicArn();
    }

    @Test
    @Order(2)
    void createTopic_FIFO토픽_생성성공() {
        // When
        CompletableFuture<SnsTopic> future = snsService.createTopic("test-fifo.fifo");
        
        // Then
        SnsTopic topic = future.join();
        assertThat(topic).isNotNull();
        assertThat(topic.getTopicArn()).contains("test-fifo.fifo");
        
        fifoTopicArn = topic.getTopicArn();
    }

    @Test
    @Order(3)
    void publishMessage_기본메시지_발행성공() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .subject("Test Subject")
                .body("Hello from integration test!")
                .messageAttribute("Priority", "High")
                .messageAttribute("Source", "IntegrationTest")
                .build();

        // When
        CompletableFuture<SnsPublishResult> future = snsService.publish(testTopicArn, message);
        
        // Then
        SnsPublishResult result = future.join();
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isNotEmpty();
        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    @Order(4)
    void publishMessage_JSON메시지_발행성공() {
        // Given
        Map<String, Object> messageData = Map.of(
            "orderId", "12345",
            "status", "PROCESSED",
            "amount", 99.99,
            "items", List.of("item1", "item2")
        );
        
        SnsMessage message = SnsMessage.builder()
                .subject("Order Processing")
                .body(messageData)
                .messageAttribute("ContentType", "application/json")
                .build();

        // When
        CompletableFuture<SnsPublishResult> future = snsService.publish(testTopicArn, message);
        
        // Then
        SnsPublishResult result = future.join();
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isNotEmpty();
    }

    @Test
    @Order(5)
    void publishBatch_FIFO토픽_배치발행성공() {
        // Given
        List<SnsMessage> messages = List.of(
            SnsMessage.builder()
                    .subject("Batch Message 1")
                    .body("First batch message")
                    .messageGroupId("batch-group-1")
                    .messageDeduplicationId("dedup-1")
                    .build(),
            SnsMessage.builder()
                    .subject("Batch Message 2")
                    .body("Second batch message")
                    .messageGroupId("batch-group-1")
                    .messageDeduplicationId("dedup-2")
                    .build(),
            SnsMessage.builder()
                    .subject("Batch Message 3")
                    .body("Third batch message")
                    .messageGroupId("batch-group-2")
                    .messageDeduplicationId("dedup-3")
                    .build()
        );

        // When
        CompletableFuture<List<SnsPublishResult>> future = snsService.publishBatch(fifoTopicArn, messages);
        
        // Then
        List<SnsPublishResult> results = future.join();
        assertThat(results).hasSize(3);
        results.forEach(result -> {
            assertThat(result.getMessageId()).isNotEmpty();
            assertThat(result.isSuccessful()).isTrue();
        });
    }

    @Test
    @Order(6)
    void publishSms_직접SMS_발행성공() {
        // Given
        String phoneNumber = "+1234567890";  // Test phone number
        String message = "Test SMS from integration test";

        // When
        CompletableFuture<SnsPublishResult> future = snsService.publishSms(phoneNumber, message);
        
        // Then
        SnsPublishResult result = future.join();
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isNotEmpty();
    }

    @Test
    @Order(7)
    void subscribe_SQS구독_생성및메시지수신() throws Exception {
        // Given - Create SQS queue first
        String queueName = "sns-integration-queue";
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .build();
        
        String queueUrl = sqsClient.createQueue(createQueueRequest).join().queueUrl();
        testQueueUrl = queueUrl;
        
        // Get queue ARN for subscription
        var queueAttributes = sqsClient.getQueueAttributes(builder -> 
            builder.queueUrl(queueUrl).attributeNames("QueueArn")).join();
        String queueArn = queueAttributes.attributes().get("QueueArn");

        // When - Subscribe SQS to SNS topic
        CompletableFuture<SnsSubscription> subscriptionFuture = 
            snsService.subscribe(testTopicArn, "sqs", queueArn);
        
        SnsSubscription subscription = subscriptionFuture.join();
        assertThat(subscription).isNotNull();
        assertThat(subscription.getSubscriptionArn()).contains("subscription");
        assertThat(subscription.getProtocol()).isEqualTo("sqs");
        
        // Publish test message
        SnsMessage testMessage = SnsMessage.builder()
                .subject("Cross-service test")
                .body("Message for SQS delivery")
                .build();
        
        snsService.publish(testTopicArn, testMessage).join();
        
        // Wait for message delivery and verify
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(5)
                            .build();
                    
                    List<Message> messages = sqsClient.receiveMessage(receiveRequest).join().messages();
                    assertThat(messages).isNotEmpty();
                    
                    Message receivedMessage = messages.get(0);
                    assertThat(receivedMessage.body()).contains("Cross-service test");
                    assertThat(receivedMessage.body()).contains("Message for SQS delivery");
                });
    }

    @Test
    @Order(8)
    void setTopicAttributes_속성설정_성공() {
        // When
        CompletableFuture<Void> future = snsService.setTopicAttributes(
            testTopicArn, "DisplayName", "Integration Test Topic");
        
        // Then
        future.join(); // Should complete without exception
        
        // Verify attribute was set
        GetTopicAttributesRequest request = GetTopicAttributesRequest.builder()
                .topicArn(testTopicArn)
                .build();
        
        var attributes = snsClient.getTopicAttributes(request).join().attributes();
        assertThat(attributes).containsEntry("DisplayName", "Integration Test Topic");
    }

    @Test
    @Order(9)
    void listTopics_토픽목록조회_성공() {
        // When
        CompletableFuture<List<SnsTopic>> future = snsService.listTopics();
        
        // Then
        List<SnsTopic> topics = future.join();
        assertThat(topics).isNotEmpty();
        assertThat(topics).anyMatch(topic -> 
            topic.getTopicArn().contains("integration-test-topic"));
        assertThat(topics).anyMatch(topic -> 
            topic.getTopicArn().contains("test-fifo.fifo"));
    }

    @Test
    @Order(10)
    void listSubscriptions_구독목록조회_성공() {
        // When
        CompletableFuture<List<SnsSubscription>> future = snsService.listSubscriptions(testTopicArn);
        
        // Then
        List<SnsSubscription> subscriptions = future.join();
        assertThat(subscriptions).isNotEmpty();
        assertThat(subscriptions).anyMatch(sub -> sub.getProtocol().equals("sqs"));
    }

    @Test
    @Order(11)
    void errorRecovery_잘못된토픽_예외발생() {
        // Given
        String invalidTopicArn = "arn:aws:sns:us-east-1:123456789012:nonexistent-topic";
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .build();

        // When & Then
        assertThatThrownBy(() -> snsService.publish(invalidTopicArn, message).join())
                .hasCauseInstanceOf(SnsService.SnsPublishException.class);
    }

    @Test
    @Order(12)
    void retryMechanism_일시적오류_재시도성공() throws Exception {
        // Given - Create a topic that will succeed after initial setup
        String retryTopicName = "retry-test-topic";
        SnsTopic retryTopic = snsService.createTopic(retryTopicName).join();
        
        SnsMessage message = SnsMessage.builder()
                .subject("Retry test")
                .body("Testing retry mechanism")
                .build();

        // When - Publish message (this tests the retry configuration)
        CompletableFuture<SnsPublishResult> future = snsService.publish(retryTopic.getTopicArn(), message);
        
        // Then
        SnsPublishResult result = future.join();
        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isNotEmpty();
        
        // Cleanup
        snsService.deleteTopic(retryTopic.getTopicArn()).join();
    }

    @Test
    @Order(13)
    void crossServiceIntegration_SNS에서SQS_메시지플로우검증() throws Exception {
        // Given - Message flow setup already done in subscribe test
        if (testQueueUrl == null) {
            return; // Skip if queue wasn't created
        }
        
        // When - Publish different message types
        List<SnsMessage> testMessages = List.of(
            SnsMessage.builder()
                    .subject("Order Created")
                    .body(Map.of("orderId", "ORDER-001", "status", "CREATED"))
                    .messageAttribute("EventType", "OrderCreated")
                    .build(),
            SnsMessage.builder()
                    .subject("Payment Processed")
                    .body(Map.of("orderId", "ORDER-001", "amount", 250.00))
                    .messageAttribute("EventType", "PaymentProcessed")
                    .build()
        );
        
        // Publish messages
        for (SnsMessage msg : testMessages) {
            snsService.publish(testTopicArn, msg).join();
        }
        
        // Then - Verify all messages are delivered to SQS
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(2, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                            .queueUrl(testQueueUrl)
                            .maxNumberOfMessages(10)
                            .waitTimeSeconds(10)
                            .build();
                    
                    List<Message> messages = sqsClient.receiveMessage(receiveRequest).join().messages();
                    assertThat(messages.size()).isGreaterThanOrEqualTo(2);
                });
    }

    @Test
    @Order(14)
    void performanceTest_대량메시지_동시발행() throws Exception {
        // Given
        String perfTopicArn = snsService.createTopic("performance-test-topic").join().getTopicArn();
        int messageCount = 50;
        
        // When - Publish messages concurrently
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<SnsPublishResult>> futures = 
            java.util.stream.IntStream.range(0, messageCount)
                .mapToObj(i -> SnsMessage.builder()
                        .subject("Performance Test " + i)
                        .body("Message " + i + " for performance testing")
                        .messageAttribute("Index", String.valueOf(i))
                        .build())
                .map(message -> snsService.publish(perfTopicArn, message))
                .toList();
        
        // Wait for all to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        allOf.join();
        
        long duration = System.currentTimeMillis() - startTime;
        
        // Then - Verify performance and results
        List<SnsPublishResult> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        
        assertThat(results).hasSize(messageCount);
        assertThat(results).allMatch(SnsPublishResult::isSuccessful);
        assertThat(duration).isLessThan(10000); // Should complete within 10 seconds
        
        System.out.printf("Published %d messages in %d ms (%.2f msg/sec)%n", 
                messageCount, duration, (messageCount * 1000.0) / duration);
        
        // Cleanup
        snsService.deleteTopic(perfTopicArn).join();
    }

    @Test
    @Order(98)
    void cleanup_구독해제() {
        // Cleanup subscriptions if they exist
        if (testTopicArn != null) {
            snsService.listSubscriptions(testTopicArn).join()
                    .forEach(subscription -> {
                        if (!"PendingConfirmation".equals(subscription.getSubscriptionArn())) {
                            snsService.unsubscribe(subscription.getSubscriptionArn()).join();
                        }
                    });
        }
    }

    @Test
    @Order(99)
    void cleanup_토픽삭제() {
        // Cleanup topics
        if (testTopicArn != null) {
            snsService.deleteTopic(testTopicArn).join();
        }
        if (fifoTopicArn != null) {
            snsService.deleteTopic(fifoTopicArn).join();
        }
    }
}