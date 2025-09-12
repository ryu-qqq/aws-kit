package com.ryuqq.aws.sns.service;

import com.ryuqq.aws.sns.adapter.SnsTypeAdapter;
import com.ryuqq.aws.sns.types.SnsMessage;
import com.ryuqq.aws.sns.types.SnsPublishResult;
import com.ryuqq.aws.sns.types.SnsSubscription;
import com.ryuqq.aws.sns.types.SnsTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SnsService Unit Tests")
class SnsServiceTest {

    @Mock
    private SnsAsyncClient snsClient;

    @Mock
    private SnsTypeAdapter typeAdapter;

    private SnsService snsService;

    private static final String TEST_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:test-topic";
    private static final String TEST_MESSAGE_ID = "message-id-123";
    private static final String TEST_SEQUENCE_NUMBER = "1000000000000000001";
    private static final String TEST_PHONE_NUMBER = "+1234567890";
    private static final String TEST_SUBSCRIPTION_ARN = "arn:aws:sns:us-east-1:123456789012:test-topic:subscription-id";

    @BeforeEach
    void setUp() {
        snsService = new SnsService(snsClient, typeAdapter);
    }

    @Test
    @DisplayName("Should publish message to topic successfully")
    void shouldPublishMessageSuccessfully() {
        // Given
        SnsMessage message = SnsMessage.of("test body");
        PublishRequest publishRequest = PublishRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .message("test body")
                .build();
        PublishResponse publishResponse = PublishResponse.builder()
                .messageId(TEST_MESSAGE_ID)
                .sequenceNumber(TEST_SEQUENCE_NUMBER)
                .build();
        SnsPublishResult expectedResult = SnsPublishResult.builder()
                .messageId(TEST_MESSAGE_ID)
                .sequenceNumber(TEST_SEQUENCE_NUMBER)
                .build();

        when(typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message)).thenReturn(publishRequest);
        when(snsClient.publish(publishRequest)).thenReturn(CompletableFuture.completedFuture(publishResponse));
        when(typeAdapter.toPublishResult(publishResponse)).thenReturn(expectedResult);

        // When
        CompletableFuture<SnsPublishResult> result = snsService.publish(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(expectedResult);
        verify(typeAdapter).toPublishRequest(TEST_TOPIC_ARN, message);
        verify(snsClient).publish(publishRequest);
        verify(typeAdapter).toPublishResult(publishResponse);
    }

    @Test
    @DisplayName("Should handle publish failure with exception")
    void shouldHandlePublishFailure() {
        // Given
        SnsMessage message = SnsMessage.of("test body");
        PublishRequest publishRequest = PublishRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .message("test body")
                .build();
        RuntimeException cause = new RuntimeException("AWS SDK error");

        when(typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message)).thenReturn(publishRequest);
        when(snsClient.publish(publishRequest)).thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<SnsPublishResult> result = snsService.publish(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsPublishException.class)
                .hasMessage("Failed to publish message")
                .hasCause(cause);
    }

    @Test
    @DisplayName("Should publish SMS message successfully")
    void shouldPublishSmsSuccessfully() {
        // Given
        String message = "SMS test message";
        SnsMessage smsMessage = SnsMessage.builder()
                .body(message)
                .phoneNumber(TEST_PHONE_NUMBER)
                .build();
        PublishRequest publishRequest = PublishRequest.builder()
                .phoneNumber(TEST_PHONE_NUMBER)
                .message(message)
                .build();
        PublishResponse publishResponse = PublishResponse.builder()
                .messageId(TEST_MESSAGE_ID)
                .build();
        SnsPublishResult expectedResult = SnsPublishResult.builder()
                .messageId(TEST_MESSAGE_ID)
                .build();

        when(typeAdapter.toPublishRequest(null, any(SnsMessage.class))).thenReturn(publishRequest);
        when(snsClient.publish(publishRequest)).thenReturn(CompletableFuture.completedFuture(publishResponse));
        when(typeAdapter.toPublishResult(publishResponse)).thenReturn(expectedResult);

        // When
        CompletableFuture<SnsPublishResult> result = snsService.publishSms(TEST_PHONE_NUMBER, message);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(expectedResult);
        verify(typeAdapter).toPublishRequest(eq(null), any(SnsMessage.class));
        verify(snsClient).publish(publishRequest);
        verify(typeAdapter).toPublishResult(publishResponse);
    }

    @Test
    @DisplayName("Should handle SMS publish failure")
    void shouldHandleSmsPublishFailure() {
        // Given
        String message = "SMS test message";
        RuntimeException cause = new RuntimeException("SMS send failed");

        when(typeAdapter.toPublishRequest(eq(null), any(SnsMessage.class))).thenReturn(any(PublishRequest.class));
        when(snsClient.publish(any(PublishRequest.class))).thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<SnsPublishResult> result = snsService.publishSms(TEST_PHONE_NUMBER, message);

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsPublishException.class)
                .hasMessage("Failed to send SMS");
    }

    @Test
    @DisplayName("Should publish batch messages successfully")
    void shouldPublishBatchSuccessfully() {
        // Given
        List<SnsMessage> messages = Arrays.asList(
                SnsMessage.of("message 1"),
                SnsMessage.of("message 2")
        );
        PublishBatchRequest batchRequest = PublishBatchRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .build();
        
        PublishBatchResultEntry entry1 = PublishBatchResultEntry.builder()
                .messageId("msg-1")
                .sequenceNumber("seq-1")
                .build();
        PublishBatchResultEntry entry2 = PublishBatchResultEntry.builder()
                .messageId("msg-2")
                .sequenceNumber("seq-2")
                .build();

        PublishBatchResponse batchResponse = PublishBatchResponse.builder()
                .successful(Arrays.asList(entry1, entry2))
                .failed(Arrays.asList())
                .build();

        SnsPublishResult result1 = SnsPublishResult.builder().messageId("msg-1").sequenceNumber("seq-1").build();
        SnsPublishResult result2 = SnsPublishResult.builder().messageId("msg-2").sequenceNumber("seq-2").build();

        when(typeAdapter.toPublishBatchRequest(TEST_TOPIC_ARN, messages)).thenReturn(batchRequest);
        when(snsClient.publishBatch(batchRequest)).thenReturn(CompletableFuture.completedFuture(batchResponse));
        when(typeAdapter.toBatchPublishResult(entry1)).thenReturn(result1);
        when(typeAdapter.toBatchPublishResult(entry2)).thenReturn(result2);

        // When
        CompletableFuture<List<SnsPublishResult>> result = snsService.publishBatch(TEST_TOPIC_ARN, messages);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .hasSize(2)
                .containsExactly(result1, result2);
        verify(typeAdapter).toPublishBatchRequest(TEST_TOPIC_ARN, messages);
        verify(snsClient).publishBatch(batchRequest);
    }

    @Test
    @DisplayName("Should handle batch publish with partial failures")
    void shouldHandleBatchPublishWithPartialFailures() {
        // Given
        List<SnsMessage> messages = Arrays.asList(
                SnsMessage.of("message 1"),
                SnsMessage.of("message 2")
        );
        
        PublishBatchResultEntry successEntry = PublishBatchResultEntry.builder()
                .messageId("msg-1")
                .build();
        BatchResultErrorEntry failedEntry = BatchResultErrorEntry.builder()
                .id("failed-id")
                .code("InvalidParameter")
                .message("Invalid message")
                .build();

        PublishBatchResponse batchResponse = PublishBatchResponse.builder()
                .successful(Arrays.asList(successEntry))
                .failed(Arrays.asList(failedEntry))
                .build();

        SnsPublishResult successResult = SnsPublishResult.builder().messageId("msg-1").build();

        when(typeAdapter.toPublishBatchRequest(TEST_TOPIC_ARN, messages)).thenReturn(any(PublishBatchRequest.class));
        when(snsClient.publishBatch(any(PublishBatchRequest.class))).thenReturn(CompletableFuture.completedFuture(batchResponse));
        when(typeAdapter.toBatchPublishResult(successEntry)).thenReturn(successResult);

        // When
        CompletableFuture<List<SnsPublishResult>> result = snsService.publishBatch(TEST_TOPIC_ARN, messages);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .hasSize(1)
                .containsExactly(successResult);
    }

    @Test
    @DisplayName("Should handle batch publish failure")
    void shouldHandleBatchPublishFailure() {
        // Given
        List<SnsMessage> messages = Arrays.asList(SnsMessage.of("message"));
        RuntimeException cause = new RuntimeException("Batch publish failed");

        when(typeAdapter.toPublishBatchRequest(TEST_TOPIC_ARN, messages)).thenReturn(any(PublishBatchRequest.class));
        when(snsClient.publishBatch(any(PublishBatchRequest.class))).thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<List<SnsPublishResult>> result = snsService.publishBatch(TEST_TOPIC_ARN, messages);

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsPublishException.class)
                .hasMessage("Failed to publish batch");
    }

    @Test
    @DisplayName("Should create topic successfully")
    void shouldCreateTopicSuccessfully() {
        // Given
        String topicName = "test-topic";
        CreateTopicRequest createRequest = CreateTopicRequest.builder()
                .name(topicName)
                .build();
        CreateTopicResponse createResponse = CreateTopicResponse.builder()
                .topicArn(TEST_TOPIC_ARN)
                .build();
        SnsTopic expectedTopic = SnsTopic.builder()
                .topicArn(TEST_TOPIC_ARN)
                .build();

        when(snsClient.createTopic(createRequest)).thenReturn(CompletableFuture.completedFuture(createResponse));
        when(typeAdapter.toSnsTopic(createResponse)).thenReturn(expectedTopic);

        // When
        CompletableFuture<SnsTopic> result = snsService.createTopic(topicName);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(expectedTopic);
        verify(snsClient).createTopic(createRequest);
        verify(typeAdapter).toSnsTopic(createResponse);
    }

    @Test
    @DisplayName("Should handle create topic failure")
    void shouldHandleCreateTopicFailure() {
        // Given
        String topicName = "test-topic";
        RuntimeException cause = new RuntimeException("Topic creation failed");

        when(snsClient.createTopic(any(CreateTopicRequest.class))).thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<SnsTopic> result = snsService.createTopic(topicName);

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsOperationException.class)
                .hasMessage("Failed to create topic");
    }

    @Test
    @DisplayName("Should delete topic successfully")
    void shouldDeleteTopicSuccessfully() {
        // Given
        DeleteTopicRequest deleteRequest = DeleteTopicRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .build();
        DeleteTopicResponse deleteResponse = DeleteTopicResponse.builder().build();

        when(snsClient.deleteTopic(deleteRequest)).thenReturn(CompletableFuture.completedFuture(deleteResponse));

        // When
        CompletableFuture<Void> result = snsService.deleteTopic(TEST_TOPIC_ARN);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        verify(snsClient).deleteTopic(deleteRequest);
    }

    @Test
    @DisplayName("Should handle delete topic failure")
    void shouldHandleDeleteTopicFailure() {
        // Given
        RuntimeException cause = new RuntimeException("Delete failed");

        when(snsClient.deleteTopic(any(DeleteTopicRequest.class))).thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<Void> result = snsService.deleteTopic(TEST_TOPIC_ARN);

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsOperationException.class)
                .hasMessage("Failed to delete topic");
    }

    @Test
    @DisplayName("Should subscribe to topic successfully")
    void shouldSubscribeSuccessfully() {
        // Given
        String protocol = "email";
        String endpoint = "test@example.com";
        SubscribeRequest subscribeRequest = SubscribeRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .protocol(protocol)
                .endpoint(endpoint)
                .build();
        SubscribeResponse subscribeResponse = SubscribeResponse.builder()
                .subscriptionArn(TEST_SUBSCRIPTION_ARN)
                .build();
        SnsSubscription expectedSubscription = SnsSubscription.builder()
                .subscriptionArn(TEST_SUBSCRIPTION_ARN)
                .build();

        when(snsClient.subscribe(subscribeRequest)).thenReturn(CompletableFuture.completedFuture(subscribeResponse));
        when(typeAdapter.toSnsSubscription(subscribeResponse)).thenReturn(expectedSubscription);

        // When
        CompletableFuture<SnsSubscription> result = snsService.subscribe(TEST_TOPIC_ARN, protocol, endpoint);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(expectedSubscription);
        verify(snsClient).subscribe(subscribeRequest);
        verify(typeAdapter).toSnsSubscription(subscribeResponse);
    }

    @Test
    @DisplayName("Should handle subscribe failure")
    void shouldHandleSubscribeFailure() {
        // Given
        RuntimeException cause = new RuntimeException("Subscribe failed");

        when(snsClient.subscribe(any(SubscribeRequest.class))).thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<SnsSubscription> result = snsService.subscribe(TEST_TOPIC_ARN, "email", "test@example.com");

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsOperationException.class)
                .hasMessage("Failed to subscribe");
    }

    @Test
    @DisplayName("Should unsubscribe successfully")
    void shouldUnsubscribeSuccessfully() {
        // Given
        UnsubscribeRequest unsubscribeRequest = UnsubscribeRequest.builder()
                .subscriptionArn(TEST_SUBSCRIPTION_ARN)
                .build();
        UnsubscribeResponse unsubscribeResponse = UnsubscribeResponse.builder().build();

        when(snsClient.unsubscribe(unsubscribeRequest)).thenReturn(CompletableFuture.completedFuture(unsubscribeResponse));

        // When
        CompletableFuture<Void> result = snsService.unsubscribe(TEST_SUBSCRIPTION_ARN);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        verify(snsClient).unsubscribe(unsubscribeRequest);
    }

    @Test
    @DisplayName("Should handle unsubscribe failure")
    void shouldHandleUnsubscribeFailure() {
        // Given
        RuntimeException cause = new RuntimeException("Unsubscribe failed");

        when(snsClient.unsubscribe(any(UnsubscribeRequest.class))).thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<Void> result = snsService.unsubscribe(TEST_SUBSCRIPTION_ARN);

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsOperationException.class)
                .hasMessage("Failed to unsubscribe");
    }

    @Test
    @DisplayName("Should list topics successfully")
    void shouldListTopicsSuccessfully() {
        // Given
        Topic topic1 = Topic.builder().topicArn("arn:topic:1").build();
        Topic topic2 = Topic.builder().topicArn("arn:topic:2").build();
        ListTopicsResponse listResponse = ListTopicsResponse.builder()
                .topics(Arrays.asList(topic1, topic2))
                .build();
        
        SnsTopic snsTopic1 = SnsTopic.builder().topicArn("arn:topic:1").build();
        SnsTopic snsTopic2 = SnsTopic.builder().topicArn("arn:topic:2").build();

        when(snsClient.listTopics()).thenReturn(CompletableFuture.completedFuture(listResponse));
        when(typeAdapter.toSnsTopic(topic1)).thenReturn(snsTopic1);
        when(typeAdapter.toSnsTopic(topic2)).thenReturn(snsTopic2);

        // When
        CompletableFuture<List<SnsTopic>> result = snsService.listTopics();

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .hasSize(2)
                .containsExactly(snsTopic1, snsTopic2);
        verify(snsClient).listTopics();
    }

    @Test
    @DisplayName("Should handle list topics failure")
    void shouldHandleListTopicsFailure() {
        // Given
        RuntimeException cause = new RuntimeException("List failed");

        when(snsClient.listTopics()).thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<List<SnsTopic>> result = snsService.listTopics();

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsOperationException.class)
                .hasMessage("Failed to list topics");
    }

    @Test
    @DisplayName("Should list subscriptions successfully")
    void shouldListSubscriptionsSuccessfully() {
        // Given
        Subscription subscription1 = Subscription.builder()
                .subscriptionArn("arn:sub:1")
                .topicArn(TEST_TOPIC_ARN)
                .protocol("email")
                .endpoint("test1@example.com")
                .build();
        Subscription subscription2 = Subscription.builder()
                .subscriptionArn("arn:sub:2")
                .topicArn(TEST_TOPIC_ARN)
                .protocol("sms")
                .endpoint("+1234567890")
                .build();
        
        ListSubscriptionsByTopicRequest listRequest = ListSubscriptionsByTopicRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .build();
        ListSubscriptionsByTopicResponse listResponse = ListSubscriptionsByTopicResponse.builder()
                .subscriptions(Arrays.asList(subscription1, subscription2))
                .build();
        
        SnsSubscription snsSubscription1 = SnsSubscription.builder()
                .subscriptionArn("arn:sub:1")
                .topicArn(TEST_TOPIC_ARN)
                .protocol("email")
                .endpoint("test1@example.com")
                .build();
        SnsSubscription snsSubscription2 = SnsSubscription.builder()
                .subscriptionArn("arn:sub:2")
                .topicArn(TEST_TOPIC_ARN)
                .protocol("sms")
                .endpoint("+1234567890")
                .build();

        when(snsClient.listSubscriptionsByTopic(listRequest)).thenReturn(CompletableFuture.completedFuture(listResponse));
        when(typeAdapter.toSnsSubscription(subscription1)).thenReturn(snsSubscription1);
        when(typeAdapter.toSnsSubscription(subscription2)).thenReturn(snsSubscription2);

        // When
        CompletableFuture<List<SnsSubscription>> result = snsService.listSubscriptions(TEST_TOPIC_ARN);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .hasSize(2)
                .containsExactly(snsSubscription1, snsSubscription2);
        verify(snsClient).listSubscriptionsByTopic(listRequest);
    }

    @Test
    @DisplayName("Should handle list subscriptions failure")
    void shouldHandleListSubscriptionsFailure() {
        // Given
        RuntimeException cause = new RuntimeException("List subscriptions failed");

        when(snsClient.listSubscriptionsByTopic(any(ListSubscriptionsByTopicRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<List<SnsSubscription>> result = snsService.listSubscriptions(TEST_TOPIC_ARN);

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsOperationException.class)
                .hasMessage("Failed to list subscriptions");
    }

    @Test
    @DisplayName("Should set topic attributes successfully")
    void shouldSetTopicAttributesSuccessfully() {
        // Given
        String attributeName = "DisplayName";
        String attributeValue = "My Topic";
        SetTopicAttributesRequest setRequest = SetTopicAttributesRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .attributeName(attributeName)
                .attributeValue(attributeValue)
                .build();
        SetTopicAttributesResponse setResponse = SetTopicAttributesResponse.builder().build();

        when(snsClient.setTopicAttributes(setRequest)).thenReturn(CompletableFuture.completedFuture(setResponse));

        // When
        CompletableFuture<Void> result = snsService.setTopicAttributes(TEST_TOPIC_ARN, attributeName, attributeValue);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        verify(snsClient).setTopicAttributes(setRequest);
    }

    @Test
    @DisplayName("Should handle set topic attributes failure")
    void shouldHandleSetTopicAttributesFailure() {
        // Given
        RuntimeException cause = new RuntimeException("Set attributes failed");

        when(snsClient.setTopicAttributes(any(SetTopicAttributesRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(cause));

        // When
        CompletableFuture<Void> result = snsService.setTopicAttributes(TEST_TOPIC_ARN, "DisplayName", "My Topic");

        // Then
        assertThat(result).failsWithin(java.time.Duration.ofSeconds(1))
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isInstanceOf(SnsService.SnsOperationException.class)
                .hasMessage("Failed to set attributes");
    }

    @Test
    @DisplayName("Should handle null inputs gracefully")
    void shouldHandleNullInputs() {
        // Test null message
        assertThatThrownBy(() -> snsService.publish(TEST_TOPIC_ARN, null))
                .isInstanceOf(NullPointerException.class);

        // Test null topic ARN with valid message
        SnsMessage message = SnsMessage.of("test");
        assertThatCode(() -> snsService.publish(null, message))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle empty batch messages list")
    void shouldHandleEmptyBatchMessagesList() {
        // Given
        List<SnsMessage> emptyMessages = Arrays.asList();
        PublishBatchRequest batchRequest = PublishBatchRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .publishBatchRequestEntries(Arrays.asList())
                .build();
        PublishBatchResponse batchResponse = PublishBatchResponse.builder()
                .successful(Arrays.asList())
                .failed(Arrays.asList())
                .build();

        when(typeAdapter.toPublishBatchRequest(TEST_TOPIC_ARN, emptyMessages)).thenReturn(batchRequest);
        when(snsClient.publishBatch(batchRequest)).thenReturn(CompletableFuture.completedFuture(batchResponse));

        // When
        CompletableFuture<List<SnsPublishResult>> result = snsService.publishBatch(TEST_TOPIC_ARN, emptyMessages);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEmpty();
    }

    @Test
    @DisplayName("Custom exceptions should have proper message and cause")
    void shouldCreateCustomExceptionsCorrectly() {
        // Given
        RuntimeException cause = new RuntimeException("Original cause");
        
        // Test SnsPublishException
        SnsService.SnsPublishException publishException = new SnsService.SnsPublishException("Publish failed", cause);
        assertThat(publishException.getMessage()).isEqualTo("Publish failed");
        assertThat(publishException.getCause()).isEqualTo(cause);

        // Test SnsOperationException
        SnsService.SnsOperationException operationException = new SnsService.SnsOperationException("Operation failed", cause);
        assertThat(operationException.getMessage()).isEqualTo("Operation failed");
        assertThat(operationException.getCause()).isEqualTo(cause);
    }
}