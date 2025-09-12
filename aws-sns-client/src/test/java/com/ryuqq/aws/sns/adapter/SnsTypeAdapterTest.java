package com.ryuqq.aws.sns.adapter;

import com.ryuqq.aws.sns.types.SnsMessage;
import com.ryuqq.aws.sns.types.SnsPublishResult;
import com.ryuqq.aws.sns.types.SnsSubscription;
import com.ryuqq.aws.sns.types.SnsTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import software.amazon.awssdk.services.sns.model.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SnsTypeAdapter Unit Tests")
class SnsTypeAdapterTest {

    private SnsTypeAdapter typeAdapter;

    private static final String TEST_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:test-topic";
    private static final String TEST_MESSAGE_ID = "message-id-123";
    private static final String TEST_SEQUENCE_NUMBER = "1000000000000000001";
    private static final String TEST_PHONE_NUMBER = "+1234567890";
    private static final String TEST_TARGET_ARN = "arn:aws:sns:us-east-1:123456789012:endpoint/platform/endpoint-id";

    @BeforeEach
    void setUp() {
        typeAdapter = new SnsTypeAdapter();
    }

    @Test
    @DisplayName("Should convert simple SnsMessage to PublishRequest with topic ARN")
    void shouldConvertSimpleMessageToPublishRequest() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("Test message body")
                .subject("Test subject")
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result.topicArn()).isEqualTo(TEST_TOPIC_ARN);
        assertThat(result.message()).isEqualTo("Test message body");
        assertThat(result.subject()).isEqualTo("Test subject");
        assertThat(result.phoneNumber()).isNull();
        assertThat(result.targetArn()).isNull();
        assertThat(result.messageStructure()).isEqualTo("raw");
        assertThat(result.messageAttributes()).isEmpty();
    }

    @Test
    @DisplayName("Should convert SnsMessage to PublishRequest with phone number")
    void shouldConvertMessageToPublishRequestWithPhoneNumber() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("SMS message")
                .phoneNumber(TEST_PHONE_NUMBER)
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(null, message);

        // Then
        assertThat(result.topicArn()).isNull();
        assertThat(result.phoneNumber()).isEqualTo(TEST_PHONE_NUMBER);
        assertThat(result.message()).isEqualTo("SMS message");
        assertThat(result.subject()).isNull();
    }

    @Test
    @DisplayName("Should convert SnsMessage to PublishRequest with target ARN")
    void shouldConvertMessageToPublishRequestWithTargetArn() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("Push notification")
                .targetArn(TEST_TARGET_ARN)
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(null, message);

        // Then
        assertThat(result.topicArn()).isNull();
        assertThat(result.targetArn()).isEqualTo(TEST_TARGET_ARN);
        assertThat(result.phoneNumber()).isNull();
        assertThat(result.message()).isEqualTo("Push notification");
    }

    @Test
    @DisplayName("Should prefer topic ARN when both topic ARN and message target ARN are provided")
    void shouldPreferTopicArnOverMessageTargetArn() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .targetArn(TEST_TARGET_ARN)
                .phoneNumber(TEST_PHONE_NUMBER)
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result.topicArn()).isEqualTo(TEST_TOPIC_ARN);
        assertThat(result.targetArn()).isNull();
        assertThat(result.phoneNumber()).isNull();
    }

    @ParameterizedTest
    @EnumSource(SnsMessage.MessageStructure.class)
    @DisplayName("Should handle all message structure types")
    void shouldHandleAllMessageStructureTypes(SnsMessage.MessageStructure structure) {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .structure(structure)
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result.messageStructure()).isEqualTo(structure.getValue());
    }

    @Test
    @DisplayName("Should convert message attributes to AWS format")
    void shouldConvertMessageAttributesToAwsFormat() {
        // Given
        Map<String, String> attributes = new HashMap<>();
        attributes.put("correlationId", "12345");
        attributes.put("messageType", "ORDER");
        attributes.put("timestamp", "2023-01-01T10:00:00Z");

        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .attributes(attributes)
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result.messageAttributes()).hasSize(3);
        assertThat(result.messageAttributes()).containsKeys("correlationId", "messageType", "timestamp");
        
        MessageAttributeValue correlationIdAttr = result.messageAttributes().get("correlationId");
        assertThat(correlationIdAttr.dataType()).isEqualTo("String");
        assertThat(correlationIdAttr.stringValue()).isEqualTo("12345");
        
        MessageAttributeValue messageTypeAttr = result.messageAttributes().get("messageType");
        assertThat(messageTypeAttr.dataType()).isEqualTo("String");
        assertThat(messageTypeAttr.stringValue()).isEqualTo("ORDER");
    }

    @Test
    @DisplayName("Should handle FIFO message attributes")
    void shouldHandleFifoMessageAttributes() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("FIFO message")
                .messageGroupId("group-123")
                .messageDeduplicationId("dedup-456")
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result.messageGroupId()).isEqualTo("group-123");
        assertThat(result.messageDeduplicationId()).isEqualTo("dedup-456");
    }

    @Test
    @DisplayName("Should convert messages to PublishBatchRequest")
    void shouldConvertMessagesToPublishBatchRequest() {
        // Given
        SnsMessage message1 = SnsMessage.builder()
                .body("Message 1")
                .subject("Subject 1")
                .build();
        SnsMessage message2 = SnsMessage.builder()
                .body("Message 2")
                .messageGroupId("group-1")
                .messageDeduplicationId("dedup-1")
                .build();
        List<SnsMessage> messages = Arrays.asList(message1, message2);

        // When
        PublishBatchRequest result = typeAdapter.toPublishBatchRequest(TEST_TOPIC_ARN, messages);

        // Then
        assertThat(result.topicArn()).isEqualTo(TEST_TOPIC_ARN);
        assertThat(result.publishBatchRequestEntries()).hasSize(2);

        PublishBatchRequestEntry entry1 = result.publishBatchRequestEntries().get(0);
        assertThat(entry1.message()).isEqualTo("Message 1");
        assertThat(entry1.subject()).isEqualTo("Subject 1");
        assertThat(entry1.id()).isNotNull();
        assertThat(entry1.messageGroupId()).isNull();

        PublishBatchRequestEntry entry2 = result.publishBatchRequestEntries().get(1);
        assertThat(entry2.message()).isEqualTo("Message 2");
        assertThat(entry2.subject()).isNull();
        assertThat(entry2.id()).isNotNull();
        assertThat(entry2.messageGroupId()).isEqualTo("group-1");
        assertThat(entry2.messageDeduplicationId()).isEqualTo("dedup-1");
    }

    @Test
    @DisplayName("Should generate unique IDs for batch entries")
    void shouldGenerateUniqueIdsForBatchEntries() {
        // Given
        List<SnsMessage> messages = Arrays.asList(
                SnsMessage.of("Message 1"),
                SnsMessage.of("Message 2"),
                SnsMessage.of("Message 3")
        );

        // When
        PublishBatchRequest result = typeAdapter.toPublishBatchRequest(TEST_TOPIC_ARN, messages);

        // Then
        List<String> ids = result.publishBatchRequestEntries().stream()
                .map(PublishBatchRequestEntry::id)
                .toList();
        
        assertThat(ids).hasSize(3);
        assertThat(ids).doesNotHaveDuplicates();
        assertThat(ids).allSatisfy(id -> assertThat(id).isNotBlank());
    }

    @Test
    @DisplayName("Should handle batch request with message attributes")
    void shouldHandleBatchRequestWithMessageAttributes() {
        // Given
        Map<String, String> attributes1 = new HashMap<>();
        attributes1.put("priority", "high");
        
        Map<String, String> attributes2 = new HashMap<>();
        attributes2.put("category", "notification");
        attributes2.put("source", "system");

        SnsMessage message1 = SnsMessage.builder()
                .body("Message 1")
                .attributes(attributes1)
                .build();
        SnsMessage message2 = SnsMessage.builder()
                .body("Message 2")
                .attributes(attributes2)
                .build();

        // When
        PublishBatchRequest result = typeAdapter.toPublishBatchRequest(TEST_TOPIC_ARN, Arrays.asList(message1, message2));

        // Then
        PublishBatchRequestEntry entry1 = result.publishBatchRequestEntries().get(0);
        assertThat(entry1.messageAttributes()).hasSize(1);
        assertThat(entry1.messageAttributes().get("priority").stringValue()).isEqualTo("high");

        PublishBatchRequestEntry entry2 = result.publishBatchRequestEntries().get(1);
        assertThat(entry2.messageAttributes()).hasSize(2);
        assertThat(entry2.messageAttributes().get("category").stringValue()).isEqualTo("notification");
        assertThat(entry2.messageAttributes().get("source").stringValue()).isEqualTo("system");
    }

    @Test
    @DisplayName("Should handle empty messages list for batch request")
    void shouldHandleEmptyMessagesListForBatchRequest() {
        // Given
        List<SnsMessage> emptyMessages = Arrays.asList();

        // When
        PublishBatchRequest result = typeAdapter.toPublishBatchRequest(TEST_TOPIC_ARN, emptyMessages);

        // Then
        assertThat(result.topicArn()).isEqualTo(TEST_TOPIC_ARN);
        assertThat(result.publishBatchRequestEntries()).isEmpty();
    }

    @Test
    @DisplayName("Should convert PublishResponse to SnsPublishResult")
    void shouldConvertPublishResponseToSnsPublishResult() {
        // Given
        PublishResponse response = PublishResponse.builder()
                .messageId(TEST_MESSAGE_ID)
                .sequenceNumber(TEST_SEQUENCE_NUMBER)
                .build();

        // When
        SnsPublishResult result = typeAdapter.toPublishResult(response);

        // Then
        assertThat(result.getMessageId()).isEqualTo(TEST_MESSAGE_ID);
        assertThat(result.getSequenceNumber()).isEqualTo(TEST_SEQUENCE_NUMBER);
    }

    @Test
    @DisplayName("Should handle PublishResponse without sequence number")
    void shouldHandlePublishResponseWithoutSequenceNumber() {
        // Given
        PublishResponse response = PublishResponse.builder()
                .messageId(TEST_MESSAGE_ID)
                .build();

        // When
        SnsPublishResult result = typeAdapter.toPublishResult(response);

        // Then
        assertThat(result.getMessageId()).isEqualTo(TEST_MESSAGE_ID);
        assertThat(result.getSequenceNumber()).isNull();
    }

    @Test
    @DisplayName("Should convert PublishBatchResultEntry to SnsPublishResult")
    void shouldConvertBatchResultEntryToSnsPublishResult() {
        // Given
        PublishBatchResultEntry entry = PublishBatchResultEntry.builder()
                .messageId("batch-msg-123")
                .sequenceNumber("batch-seq-456")
                .build();

        // When
        SnsPublishResult result = typeAdapter.toBatchPublishResult(entry);

        // Then
        assertThat(result.getMessageId()).isEqualTo("batch-msg-123");
        assertThat(result.getSequenceNumber()).isEqualTo("batch-seq-456");
    }

    @Test
    @DisplayName("Should handle batch result entry without sequence number")
    void shouldHandleBatchResultEntryWithoutSequenceNumber() {
        // Given
        PublishBatchResultEntry entry = PublishBatchResultEntry.builder()
                .messageId("batch-msg-123")
                .build();

        // When
        SnsPublishResult result = typeAdapter.toBatchPublishResult(entry);

        // Then
        assertThat(result.getMessageId()).isEqualTo("batch-msg-123");
        assertThat(result.getSequenceNumber()).isNull();
    }

    @Test
    @DisplayName("Should convert Topic to SnsTopic")
    void shouldConvertTopicToSnsTopic() {
        // Given
        Topic topic = Topic.builder()
                .topicArn(TEST_TOPIC_ARN)
                .build();

        // When
        SnsTopic result = typeAdapter.toSnsTopic(topic);

        // Then
        assertThat(result.getTopicArn()).isEqualTo(TEST_TOPIC_ARN);
    }

    @Test
    @DisplayName("Should convert CreateTopicResponse to SnsTopic")
    void shouldConvertCreateTopicResponseToSnsTopic() {
        // Given
        CreateTopicResponse response = CreateTopicResponse.builder()
                .topicArn(TEST_TOPIC_ARN)
                .build();

        // When
        SnsTopic result = typeAdapter.toSnsTopic(response);

        // Then
        assertThat(result.getTopicArn()).isEqualTo(TEST_TOPIC_ARN);
    }

    @Test
    @DisplayName("Should convert Subscription to SnsSubscription")
    void shouldConvertSubscriptionToSnsSubscription() {
        // Given
        String subscriptionArn = "arn:aws:sns:us-east-1:123456789012:test-topic:subscription-id";
        String protocol = "email";
        String endpoint = "test@example.com";
        String owner = "123456789012";

        Subscription subscription = Subscription.builder()
                .subscriptionArn(subscriptionArn)
                .topicArn(TEST_TOPIC_ARN)
                .protocol(protocol)
                .endpoint(endpoint)
                .owner(owner)
                .build();

        // When
        SnsSubscription result = typeAdapter.toSnsSubscription(subscription);

        // Then
        assertThat(result.getSubscriptionArn()).isEqualTo(subscriptionArn);
        assertThat(result.getTopicArn()).isEqualTo(TEST_TOPIC_ARN);
        assertThat(result.getProtocol()).isEqualTo(protocol);
        assertThat(result.getEndpoint()).isEqualTo(endpoint);
        assertThat(result.getOwner()).isEqualTo(owner);
    }

    @Test
    @DisplayName("Should convert SubscribeResponse to SnsSubscription")
    void shouldConvertSubscribeResponseToSnsSubscription() {
        // Given
        String subscriptionArn = "arn:aws:sns:us-east-1:123456789012:test-topic:subscription-id";
        SubscribeResponse response = SubscribeResponse.builder()
                .subscriptionArn(subscriptionArn)
                .build();

        // When
        SnsSubscription result = typeAdapter.toSnsSubscription(response);

        // Then
        assertThat(result.getSubscriptionArn()).isEqualTo(subscriptionArn);
        assertThat(result.getTopicArn()).isNull();
        assertThat(result.getProtocol()).isNull();
        assertThat(result.getEndpoint()).isNull();
        assertThat(result.getOwner()).isNull();
    }

    @Test
    @DisplayName("Should handle null values gracefully in conversions")
    void shouldHandleNullValuesGracefully() {
        // Test SnsMessage with null optional fields
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .subject(null)
                .messageGroupId(null)
                .messageDeduplicationId(null)
                .build();

        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        assertThat(result.subject()).isNull();
        assertThat(result.messageGroupId()).isNull();
        assertThat(result.messageDeduplicationId()).isNull();
    }

    @Test
    @DisplayName("Should handle message with empty attributes map")
    void shouldHandleMessageWithEmptyAttributesMap() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .attributes(new HashMap<>())
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result.messageAttributes()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve message structure enum values")
    void shouldPreserveMessageStructureEnumValues() {
        // Test RAW structure
        SnsMessage rawMessage = SnsMessage.builder()
                .body("Raw message")
                .structure(SnsMessage.MessageStructure.RAW)
                .build();

        PublishRequest rawResult = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, rawMessage);
        assertThat(rawResult.messageStructure()).isEqualTo("raw");

        // Test JSON structure
        SnsMessage jsonMessage = SnsMessage.builder()
                .body("JSON message")
                .structure(SnsMessage.MessageStructure.JSON)
                .build();

        PublishRequest jsonResult = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, jsonMessage);
        assertThat(jsonResult.messageStructure()).isEqualTo("json");
    }

    @Test
    @DisplayName("Should handle message attributes with special characters")
    void shouldHandleMessageAttributesWithSpecialCharacters() {
        // Given
        Map<String, String> attributes = new HashMap<>();
        attributes.put("special-key", "value with spaces & symbols");
        attributes.put("unicode-key", "Héllo Wörld! 🎉");
        attributes.put("json-key", "{\"nested\": \"value\"}");

        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .attributes(attributes)
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result.messageAttributes()).hasSize(3);
        assertThat(result.messageAttributes().get("special-key").stringValue())
                .isEqualTo("value with spaces & symbols");
        assertThat(result.messageAttributes().get("unicode-key").stringValue())
                .isEqualTo("Héllo Wörld! 🎉");
        assertThat(result.messageAttributes().get("json-key").stringValue())
                .isEqualTo("{\"nested\": \"value\"}");
    }

    @Test
    @DisplayName("Should handle message destination priority correctly")
    void shouldHandleMessageDestinationPriorityCorrectly() {
        // Topic ARN has highest priority
        SnsMessage messageWithAll = SnsMessage.builder()
                .body("Test message")
                .targetArn(TEST_TARGET_ARN)
                .phoneNumber(TEST_PHONE_NUMBER)
                .build();

        PublishRequest resultWithTopic = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, messageWithAll);
        assertThat(resultWithTopic.topicArn()).isEqualTo(TEST_TOPIC_ARN);
        assertThat(resultWithTopic.targetArn()).isNull();
        assertThat(resultWithTopic.phoneNumber()).isNull();

        // Target ARN has second priority when topic ARN is null
        PublishRequest resultWithTarget = typeAdapter.toPublishRequest(null, messageWithAll);
        assertThat(resultWithTarget.topicArn()).isNull();
        assertThat(resultWithTarget.targetArn()).isEqualTo(TEST_TARGET_ARN);
        assertThat(resultWithTarget.phoneNumber()).isNull();

        // Phone number has third priority
        SnsMessage messageWithPhone = SnsMessage.builder()
                .body("Test message")
                .phoneNumber(TEST_PHONE_NUMBER)
                .build();

        PublishRequest resultWithPhone = typeAdapter.toPublishRequest(null, messageWithPhone);
        assertThat(resultWithPhone.topicArn()).isNull();
        assertThat(resultWithPhone.targetArn()).isNull();
        assertThat(resultWithPhone.phoneNumber()).isEqualTo(TEST_PHONE_NUMBER);
    }

    @Test
    @DisplayName("Should handle large number of message attributes")
    void shouldHandleLargeNumberOfMessageAttributes() {
        // Given
        Map<String, String> attributes = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            attributes.put("key" + i, "value" + i);
        }

        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .attributes(attributes)
                .build();

        // When
        PublishRequest result = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);

        // Then
        assertThat(result.messageAttributes()).hasSize(10);
        for (int i = 1; i <= 10; i++) {
            String key = "key" + i;
            String expectedValue = "value" + i;
            assertThat(result.messageAttributes()).containsKey(key);
            assertThat(result.messageAttributes().get(key).stringValue()).isEqualTo(expectedValue);
            assertThat(result.messageAttributes().get(key).dataType()).isEqualTo("String");
        }
    }
}