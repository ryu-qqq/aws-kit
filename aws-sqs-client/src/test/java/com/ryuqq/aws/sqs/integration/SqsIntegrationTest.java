package com.ryuqq.aws.sqs.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQS 통합 테스트 - LocalStack 사용
 */
@Testcontainers
class SqsIntegrationTest {

    @Container
    static final LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.SQS)
            .withEnv("DEFAULT_REGION", "us-east-1");

    private static SqsAsyncClient sqsClient;
    private static final String TEST_QUEUE = "test-queue";

    @BeforeAll
    static void setUp() {
        sqsClient = SqsAsyncClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .region(Region.US_EAST_1)
                .build();
    }

    @Test
    void shouldCreateQueueAndSendMessage() throws Exception {
        // Given - Create queue
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(TEST_QUEUE)
                .build();

        CompletableFuture<CreateQueueResponse> createResponse = sqsClient.createQueue(createQueueRequest);
        String queueUrl = createResponse.get().queueUrl();
        assertThat(queueUrl).isNotBlank();

        // When - Send message
        String messageBody = "Hello LocalStack!";
        SendMessageRequest sendRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        CompletableFuture<SendMessageResponse> sendResponse = sqsClient.sendMessage(sendRequest);
        SendMessageResponse response = sendResponse.get();

        // Then
        assertThat(response.messageId()).isNotBlank();
    }

    @Test
    void shouldSendAndReceiveMessage() throws Exception {
        // Given - Create queue
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(TEST_QUEUE + "-receive")
                .build();

        String queueUrl = sqsClient.createQueue(createQueueRequest).get().queueUrl();

        // Send message
        String messageBody = "Test message for receiving";
        SendMessageRequest sendRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();
        sqsClient.sendMessage(sendRequest).get();

        // When - Receive message
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(1)
                .build();

        CompletableFuture<ReceiveMessageResponse> receiveResponse = sqsClient.receiveMessage(receiveRequest);
        ReceiveMessageResponse response = receiveResponse.get();

        // Then
        assertThat(response.messages()).hasSize(1);
        Message message = response.messages().get(0);
        assertThat(message.body()).isEqualTo(messageBody);
        assertThat(message.messageId()).isNotBlank();
        assertThat(message.receiptHandle()).isNotBlank();
    }

    @Test
    void shouldSendMessageWithAttributes() throws Exception {
        // Given - Create queue
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(TEST_QUEUE + "-attributes")
                .build();

        String queueUrl = sqsClient.createQueue(createQueueRequest).get().queueUrl();

        // When - Send message with attributes
        String messageBody = "Message with attributes";
        SendMessageRequest sendRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .messageAttributes(Map.of(
                        "priority", MessageAttributeValue.builder()
                                .stringValue("high")
                                .dataType("String")
                                .build(),
                        "timestamp", MessageAttributeValue.builder()
                                .stringValue(String.valueOf(System.currentTimeMillis()))
                                .dataType("Number")
                                .build()
                ))
                .build();

        CompletableFuture<SendMessageResponse> sendResponse = sqsClient.sendMessage(sendRequest);
        assertThat(sendResponse.get().messageId()).isNotBlank();

        // Then - Receive and verify attributes
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributeNames("All")
                .build();

        ReceiveMessageResponse receiveResponse = sqsClient.receiveMessage(receiveRequest).get();
        assertThat(receiveResponse.messages()).hasSize(1);
        
        Message message = receiveResponse.messages().get(0);
        assertThat(message.messageAttributes()).containsKeys("priority", "timestamp");
        assertThat(message.messageAttributes().get("priority").stringValue()).isEqualTo("high");
    }

    @Test
    void shouldDeleteMessage() throws Exception {
        // Given - Create queue and send message
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(TEST_QUEUE + "-delete")
                .build();

        String queueUrl = sqsClient.createQueue(createQueueRequest).get().queueUrl();

        SendMessageRequest sendRequest = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody("Message to be deleted")
                .build();
        sqsClient.sendMessage(sendRequest).get();

        // Receive message
        ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .build();

        Message message = sqsClient.receiveMessage(receiveRequest).get().messages().get(0);

        // When - Delete message
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(message.receiptHandle())
                .build();

        CompletableFuture<DeleteMessageResponse> deleteResponse = sqsClient.deleteMessage(deleteRequest);
        assertThat(deleteResponse.get()).isNotNull();

        // Then - Verify message is deleted (no messages when receiving again)
        ReceiveMessageResponse secondReceive = sqsClient.receiveMessage(receiveRequest).get();
        assertThat(secondReceive.messages()).isEmpty();
    }

    @Test
    void shouldGetQueueAttributes() throws Exception {
        // Given - Create queue
        CreateQueueRequest createQueueRequest = CreateQueueRequest.builder()
                .queueName(TEST_QUEUE + "-attributes-test")
                .build();

        String queueUrl = sqsClient.createQueue(createQueueRequest).get().queueUrl();

        // When - Get queue attributes
        GetQueueAttributesRequest attributesRequest = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.ALL)
                .build();

        CompletableFuture<GetQueueAttributesResponse> attributesResponse = 
                sqsClient.getQueueAttributes(attributesRequest);
        GetQueueAttributesResponse response = attributesResponse.get();

        // Then
        assertThat(response.attributes()).isNotEmpty();
        assertThat(response.attributes()).containsKey(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
    }
}