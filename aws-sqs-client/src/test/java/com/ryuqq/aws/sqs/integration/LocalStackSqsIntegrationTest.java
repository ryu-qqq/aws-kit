package com.ryuqq.aws.sqs.integration;

import com.ryuqq.aws.sqs.properties.SqsProperties;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import com.ryuqq.aws.sqs.util.BatchEntryFactory;
import com.ryuqq.aws.sqs.util.QueueAttributeUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SQS;

/**
 * LocalStack을 사용한 AWS SQS 통합 테스트
 * 
 * 실제 SQS 환경과 유사한 LocalStack 컨테이너를 사용하여
 * SQS Client의 모든 기능을 검증하는 통합 테스트입니다.
 */
@Testcontainers
@DisplayName("LocalStack SQS 통합 테스트")
@EnabledIfSystemProperty(named = "run.integration.tests", matches = "true")
class LocalStackSqsIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(SQS)
            .withEnv("DEBUG", "1")
            .withEnv("LAMBDA_EXECUTOR", "local");
    
    private SqsAsyncClient sqsClient;
    private SqsService sqsService;
    private SqsProperties sqsProperties;
    
    private static final String TEST_QUEUE_NAME = "test-queue";
    private static final String TEST_DLQ_NAME = "test-dlq";
    private static final String TEST_FIFO_QUEUE_NAME = "test-queue.fifo";
    
    @BeforeEach
    void setUp() {
        // SQS 클라이언트 설정
        sqsClient = SqsAsyncClient.builder()
                .endpointOverride(localstack.getEndpointOverride(SQS))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
                ))
                .region(Region.of(localstack.getRegion()))
                .build();
        
        // SQS Properties 설정
        sqsProperties = new SqsProperties();
        sqsProperties.setMaxBatchSize(10);
        sqsProperties.setVisibilityTimeout(30);
        sqsProperties.setLongPollingWaitSeconds(1); // 테스트를 위해 짧게 설정
        
        // SQS Service 생성
        sqsService = new SqsService(sqsClient, sqsProperties);
    }
    
    @AfterEach
    void tearDown() {
        if (sqsClient != null) {
            sqsClient.close();
        }
    }
    
    @Test
    @DisplayName("표준 큐를 생성하고 메시지를 송수신할 수 있어야 한다")
    void shouldCreateQueueAndSendReceiveMessages() throws Exception {
        // given
        Map<String, String> attributes = Map.of(
            "VisibilityTimeout", "30",
            "MessageRetentionPeriod", "345600",
            "ReceiveMessageWaitTimeSeconds", "1"
        );
        
        // when - 큐 생성
        String queueUrl = createQueue(TEST_QUEUE_NAME, attributes);
        
        // 메시지 전송
        String messageBody = "{\"orderId\": 12345, \"status\": \"pending\"}";
        
        CompletableFuture<String> sendResponse = sqsService.sendMessage(queueUrl, messageBody);
        String messageId = sendResponse.get(5, TimeUnit.SECONDS);
        
        // then - 전송 검증
        assertThat(messageId).isNotNull();
        
        // 메시지 수신
        CompletableFuture<List<SqsMessage>> receiveResponse = sqsService.receiveMessages(queueUrl, 1);
        List<SqsMessage> receivedMessages = receiveResponse.get(5, TimeUnit.SECONDS);
        
        assertThat(receivedMessages).hasSize(1);
        SqsMessage receivedMessage = receivedMessages.get(0);
        assertThat(receivedMessage.getBody()).isEqualTo(messageBody);
        assertThat(receivedMessage.getReceiptHandle()).isNotNull();
        
        // 메시지 삭제
        CompletableFuture<Void> deleteResponse = 
                sqsService.deleteMessage(queueUrl, receivedMessage.getReceiptHandle());
        deleteResponse.get(5, TimeUnit.SECONDS); // Just wait for completion
    }
    
    @Test
    @DisplayName("배치 메시지 전송과 삭제가 올바르게 동작해야 한다")
    void shouldHandleBatchOperations() throws Exception {
        // given
        String queueUrl = createQueue(TEST_QUEUE_NAME + "-batch");
        
        List<String> messageBodies = Arrays.asList(
            "{\"id\": 1, \"action\": \"create\"}",
            "{\"id\": 2, \"action\": \"update\"}",
            "{\"id\": 3, \"action\": \"delete\"}"
        );
        
        // when - 배치 전송
        CompletableFuture<List<String>> sendResponse = sqsService.sendMessageBatch(queueUrl, messageBodies);
        List<String> messageIds = sendResponse.get(5, TimeUnit.SECONDS);
        
        // then - 전송 검증
        assertThat(messageIds).hasSize(3);
        
        // 메시지 수신
        CompletableFuture<List<SqsMessage>> receiveResponse = sqsService.receiveMessages(queueUrl, 10);
        List<SqsMessage> receivedMessages = receiveResponse.get(5, TimeUnit.SECONDS);
        
        assertThat(receivedMessages).hasSize(3);
        
        // 배치 삭제
        List<String> receiptHandles = receivedMessages.stream()
                .map(SqsMessage::getReceiptHandle)
                .toList();
        
        CompletableFuture<Void> deleteResponse = sqsService.deleteMessageBatch(queueUrl, receiptHandles);
        deleteResponse.get(5, TimeUnit.SECONDS); // Wait for completion
    }
    
    @Test
    @DisplayName("FIFO 큐에서 메시지 순서가 보장되어야 한다")
    void shouldPreserveMessageOrderInFifoQueue() throws Exception {
        // given - FIFO 큐 생성
        Map<String, String> fifoAttributes = Map.of(
            "FifoQueue", "true",
            "ContentBasedDeduplication", "true"
        );
        
        String fifoQueueUrl = createQueue(TEST_FIFO_QUEUE_NAME, fifoAttributes);
        
        // when - 순서대로 메시지 전송
        List<String> orderedMessages = Arrays.asList("첫번째", "두번째", "세번째", "네번째");
        
        for (String messageBody : orderedMessages) {
            sqsService.sendMessage(fifoQueueUrl, messageBody).get(5, TimeUnit.SECONDS);
        }
        
        // then - 수신 순서 확인
        List<String> receivedBodies = new ArrayList<>();
        for (int i = 0; i < orderedMessages.size(); i++) {
            List<SqsMessage> messages = sqsService.receiveMessages(fifoQueueUrl, 1)
                    .get(5, TimeUnit.SECONDS);
            
            if (!messages.isEmpty()) {
                receivedBodies.add(messages.get(0).getBody());
                // 메시지 삭제
                sqsService.deleteMessage(fifoQueueUrl, messages.get(0).getReceiptHandle())
                        .get(5, TimeUnit.SECONDS);
            }
        }
        
        assertThat(receivedBodies).containsExactlyElementsOf(orderedMessages);
    }
    
    @Test
    @DisplayName("Dead Letter Queue 설정이 올바르게 동작해야 한다")
    void shouldHandleDeadLetterQueue() throws Exception {
        // given - DLQ 생성
        String dlqUrl = createQueue(TEST_DLQ_NAME);
        GetQueueAttributesResponse dlqAttributes = sqsClient.getQueueAttributes(
                GetQueueAttributesRequest.builder()
                        .queueUrl(dlqUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build()
        ).get(5, TimeUnit.SECONDS);
        
        String dlqArn = dlqAttributes.attributes().get(QueueAttributeName.QUEUE_ARN);
        
        // Redrive Policy 설정
        String redrivePolicy = String.format(
            "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":2}", dlqArn
        );
        
        Map<String, String> mainQueueAttributes = Map.of(
            "RedrivePolicy", redrivePolicy,
            "VisibilityTimeout", "1" // 빠른 테스트를 위해 짧게 설정
        );
        
        String mainQueueUrl = createQueue(TEST_QUEUE_NAME + "-with-dlq", mainQueueAttributes);
        
        // when - 메시지 전송
        String testMessageBody = "테스트 메시지 for DLQ";
        
        sqsService.sendMessage(mainQueueUrl, testMessageBody).get(5, TimeUnit.SECONDS);
        
        // 메시지를 여러 번 수신하여 maxReceiveCount 초과 시키기
        for (int i = 0; i < 3; i++) {
            List<SqsMessage> messages = sqsService.receiveMessages(mainQueueUrl, 1)
                    .get(5, TimeUnit.SECONDS);
            
            if (!messages.isEmpty()) {
                // 메시지를 삭제하지 않고 visibility timeout이 지나도록 대기
                Thread.sleep(1500); // visibility timeout = 1초
            }
        }
        
        // then - DLQ에서 메시지 확인
        Thread.sleep(2000); // DLQ 이동 시간 대기
        List<SqsMessage> dlqMessages = sqsService.receiveMessages(dlqUrl, 1)
                .get(5, TimeUnit.SECONDS);
        
        assertThat(dlqMessages).hasSize(1);
        assertThat(dlqMessages.get(0).getBody()).isEqualTo("테스트 메시지 for DLQ");
    }
    
    @Test
    @DisplayName("Long Polling이 올바르게 동작해야 한다")
    void shouldHandleLongPolling() throws Exception {
        // given
        Map<String, String> longPollingAttributes = QueueAttributeUtils.getLongPollingAttributes(5);
        String queueUrl = createQueue(TEST_QUEUE_NAME + "-longpoll", longPollingAttributes);
        
        // when - 메시지가 없는 상태에서 Long Polling 수행
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<List<SqsMessage>> receiveTask = sqsService.receiveMessages(queueUrl, 1);
        
        // 2초 후에 메시지 전송
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> {
            String delayedMessage = "지연된 메시지";
            sqsService.sendMessage(queueUrl, delayedMessage);
        });
        
        List<SqsMessage> messages = receiveTask.get(10, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // then
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getBody()).isEqualTo("지연된 메시지");
        assertThat(duration).isGreaterThanOrEqualTo(2000); // 최소 2초는 기다렸어야 함
        assertThat(duration).isLessThan(10000); // Long Polling으로 인해 빠르게 수신
    }
    
    @Test
    @DisplayName("큐 속성 설정과 조회가 올바르게 동작해야 한다")
    void shouldHandleQueueAttributes() throws Exception {
        // given
        Map<String, String> customAttributes = Map.of(
            "VisibilityTimeout", "60",
            "MessageRetentionPeriod", "1209600", // 14일
            "MaxReceiveCount", "5",
            "DelaySeconds", "300" // 5분
        );
        
        Map<QueueAttributeName, String> convertedAttributes = 
                QueueAttributeUtils.convertToQueueAttributes(customAttributes);
        
        // when - 큐 생성
        CreateQueueRequest createRequest = CreateQueueRequest.builder()
                .queueName(TEST_QUEUE_NAME + "-custom-attrs")
                .attributes(convertedAttributes)
                .build();
        
        CreateQueueResponse createResponse = sqsClient.createQueue(createRequest)
                .get(5, TimeUnit.SECONDS);
        String queueUrl = createResponse.queueUrl();
        
        // then - 속성 조회 및 검증
        GetQueueAttributesRequest getAttrsRequest = GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.ALL)
                .build();
        
        GetQueueAttributesResponse getAttrsResponse = sqsClient.getQueueAttributes(getAttrsRequest)
                .get(5, TimeUnit.SECONDS);
        
        Map<QueueAttributeName, String> retrievedAttrs = getAttrsResponse.attributes();
        
        assertThat(retrievedAttrs.get(QueueAttributeName.VISIBILITY_TIMEOUT)).isEqualTo("60");
        assertThat(retrievedAttrs.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)).isEqualTo("1209600");
        assertThat(retrievedAttrs.get(QueueAttributeName.DELAY_SECONDS)).isEqualTo("300");
        assertThat(retrievedAttrs.get(QueueAttributeName.QUEUE_ARN)).startsWith("arn:aws:sqs:");
    }
    
    @Test
    @DisplayName("메시지 가시성 타임아웃 변경이 동작해야 한다")
    void shouldChangeMessageVisibilityTimeout() throws Exception {
        // given
        String queueUrl = createQueue(TEST_QUEUE_NAME + "-visibility");
        
        String testMessage = "가시성 테스트 메시지";
        
        sqsService.sendMessage(queueUrl, testMessage).get(5, TimeUnit.SECONDS);
        
        // when - 메시지 수신
        List<SqsMessage> messages = sqsService.receiveMessages(queueUrl, 1)
                .get(5, TimeUnit.SECONDS);
        
        assertThat(messages).hasSize(1);
        String receiptHandle = messages.get(0).getReceiptHandle();
        
        // 가시성 타임아웃을 2초로 변경
        ChangeMessageVisibilityRequest changeVisibilityRequest = ChangeMessageVisibilityRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .visibilityTimeout(2)
                .build();
        
        sqsClient.changeMessageVisibility(changeVisibilityRequest).get(5, TimeUnit.SECONDS);
        
        // then - 2초 후에 메시지가 다시 보여야 함
        Thread.sleep(2500);
        
        List<SqsMessage> reappearMessages = sqsService.receiveMessages(queueUrl, 1)
                .get(5, TimeUnit.SECONDS);
        
        assertThat(reappearMessages).hasSize(1);
        assertThat(reappearMessages.get(0).getBody()).isEqualTo("가시성 테스트 메시지");
        
        // 정리
        sqsService.deleteMessage(queueUrl, reappearMessages.get(0).getReceiptHandle())
                .get(5, TimeUnit.SECONDS);
    }
    
    @Test
    @DisplayName("큐 퍼지가 올바르게 동작해야 한다")
    void shouldPurgeQueue() throws Exception {
        // given
        String queueUrl = createQueue(TEST_QUEUE_NAME + "-purge");
        
        // 여러 메시지 전송
        for (int i = 0; i < 5; i++) {
            String messageBody = "메시지 " + i;
            sqsService.sendMessage(queueUrl, messageBody).get(5, TimeUnit.SECONDS);
        }
        
        // when - 큐 퍼지
        PurgeQueueRequest purgeRequest = PurgeQueueRequest.builder()
                .queueUrl(queueUrl)
                .build();
        
        sqsClient.purgeQueue(purgeRequest).get(5, TimeUnit.SECONDS);
        
        // then - 메시지가 모두 삭제되었는지 확인
        Thread.sleep(1000); // 퍼지 처리 시간 대기
        
        List<SqsMessage> remainingMessages = sqsService.receiveMessages(queueUrl, 10)
                .get(5, TimeUnit.SECONDS);
        
        assertThat(remainingMessages).isEmpty();
    }
    
    /**
     * 테스트용 큐 생성 헬퍼 메서드
     */
    private String createQueue(String queueName) throws Exception {
        return createQueue(queueName, Collections.emptyMap());
    }
    
    private String createQueue(String queueName, Map<String, String> attributes) throws Exception {
        Map<QueueAttributeName, String> queueAttributes = attributes.isEmpty() ? 
                Collections.emptyMap() : QueueAttributeUtils.convertToQueueAttributes(attributes);
        
        CreateQueueRequest createRequest = CreateQueueRequest.builder()
                .queueName(queueName)
                .attributes(queueAttributes)
                .build();
        
        CreateQueueResponse response = sqsClient.createQueue(createRequest)
                .get(5, TimeUnit.SECONDS);
        
        return response.queueUrl();
    }
}