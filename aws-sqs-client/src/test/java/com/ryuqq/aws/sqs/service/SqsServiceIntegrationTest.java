package com.ryuqq.aws.sqs.service;

import com.ryuqq.aws.sqs.properties.SqsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQS 서비스 LocalStack 통합 테스트
 */
@Testcontainers
class SqsServiceIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.SQS)
            .withEnv("DEFAULT_REGION", "us-east-1");

    private SqsService sqsService;
    private String testQueueUrl;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.region", () -> "us-east-1");
    }

    @BeforeEach
    void setUp() throws Exception {
        // LocalStack용 SqsAsyncClient 구성
        SqsAsyncClient sqsAsyncClient = SqsAsyncClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .region(Region.US_EAST_1)
                .httpClient(NettyNioAsyncHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5))
                        .build())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(30))
                        .build())
                .build();

        SqsProperties sqsProperties = new SqsProperties();
        sqsService = new SqsService(sqsAsyncClient, sqsProperties);

        // 테스트용 큐 생성
        testQueueUrl = sqsService.createQueue("test-queue", Map.of()).get();
    }

    @Test
    void 메시지_전송_및_수신_테스트() throws Exception {
        // Given
        String messageBody = "Hello from LocalStack SQS!";

        // When - 메시지 전송
        String messageId = sqsService.sendMessage(testQueueUrl, messageBody).get();

        // Then - 메시지 ID가 반환됨
        assertThat(messageId).isNotNull();

        // When - 메시지 수신
        List<Message> messages = sqsService.receiveMessages(testQueueUrl, 1).get();

        // Then - 메시지가 수신됨
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).body()).isEqualTo(messageBody);
        assertThat(messages.get(0).messageId()).isEqualTo(messageId);
    }

    @Test
    void 배치_메시지_전송_테스트() throws Exception {
        // Given
        List<String> messages = Arrays.asList(
                "Batch message 1",
                "Batch message 2", 
                "Batch message 3"
        );

        // When - 배치 메시지 전송
        List<String> messageIds = sqsService.sendMessageBatch(testQueueUrl, messages).get();

        // Then - 모든 메시지 ID가 반환됨
        assertThat(messageIds).hasSize(3);
        assertThat(messageIds).allMatch(id -> id != null && !id.isEmpty());

        // When - 메시지 수신
        List<Message> receivedMessages = sqsService.receiveMessages(testQueueUrl, 10).get();

        // Then - 모든 메시지가 수신됨
        assertThat(receivedMessages).hasSize(3);
        List<String> receivedBodies = receivedMessages.stream()
                .map(Message::body)
                .toList();
        assertThat(receivedBodies).containsExactlyInAnyOrderElementsOf(messages);
    }

    @Test
    void 메시지_삭제_테스트() throws Exception {
        // Given - 메시지 전송
        String messageBody = "Message to delete";
        sqsService.sendMessage(testQueueUrl, messageBody).get();

        // When - 메시지 수신
        List<Message> messages = sqsService.receiveMessages(testQueueUrl, 1).get();
        assertThat(messages).hasSize(1);
        
        Message message = messages.get(0);
        String receiptHandle = message.receiptHandle();

        // When - 메시지 삭제
        sqsService.deleteMessage(testQueueUrl, receiptHandle).get();

        // Then - 메시지가 더 이상 수신되지 않음
        List<Message> afterDelete = sqsService.receiveMessages(testQueueUrl, 1).get();
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void 배치_메시지_삭제_테스트() throws Exception {
        // Given - 배치 메시지 전송
        List<String> messages = Arrays.asList("Msg1", "Msg2", "Msg3");
        sqsService.sendMessageBatch(testQueueUrl, messages).get();

        // When - 메시지 수신 및 삭제할 receiptHandle 수집
        List<Message> receivedMessages = sqsService.receiveMessages(testQueueUrl, 10).get();
        assertThat(receivedMessages).hasSize(3);
        
        List<String> receiptHandles = receivedMessages.stream()
                .map(Message::receiptHandle)
                .toList();

        // When - 배치 삭제
        sqsService.deleteMessageBatch(testQueueUrl, receiptHandles).get();

        // Then - 메시지가 더 이상 수신되지 않음
        List<Message> afterDelete = sqsService.receiveMessages(testQueueUrl, 10).get();
        assertThat(afterDelete).isEmpty();
    }

    @Test
    void 큐_URL_조회_테스트() throws Exception {
        // When
        String queueUrl = sqsService.getQueueUrl("test-queue").get();

        // Then
        assertThat(queueUrl).isEqualTo(testQueueUrl);
    }

    @Test
    void 새로운_큐_생성_테스트() throws Exception {
        // Given
        String newQueueName = "integration-test-queue";
        Map<String, String> attributes = Map.of(
                "VisibilityTimeout", "60",
                "MessageRetentionPeriod", "86400"
        );

        // When
        String newQueueUrl = sqsService.createQueue(newQueueName, attributes).get();

        // Then
        assertThat(newQueueUrl).contains(newQueueName);

        // Verify queue can be used
        String testMessage = "Test message in new queue";
        String messageId = sqsService.sendMessage(newQueueUrl, testMessage).get();
        assertThat(messageId).isNotNull();

        List<Message> messages = sqsService.receiveMessages(newQueueUrl, 1).get();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).body()).isEqualTo(testMessage);
    }
}