package com.ryuqq.aws.sqs.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SqsMessage.
 * Tests builder patterns, immutability, and accessor methods.
 */
class SqsMessageTest {

    @Nested
    class BuilderTests {

        @Test
        void shouldCreateMinimalMessage() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-123")
                    .body("test message")
                    .build();

            assertThat(message.getMessageId()).isEqualTo("msg-123");
            assertThat(message.getBody()).isEqualTo("test message");
            assertThat(message.getReceiptHandle()).isNull();
            assertThat(message.getMd5OfBody()).isNull();
            assertThat(message.getMd5OfMessageAttributes()).isNull();
            assertThat(message.getAttributes()).isEmpty();
            assertThat(message.getMessageAttributes()).isEmpty();
            assertThat(message.getTimestamp()).isNull();
        }

        @Test
        void shouldCreateCompleteMessage() {
            Instant timestamp = Instant.now();
            Map<String, String> attributes = new HashMap<>();
            attributes.put("SenderId", "sender-123");
            attributes.put("SentTimestamp", "1234567890");

            Map<String, SqsMessageAttribute> messageAttributes = new HashMap<>();
            messageAttributes.put("attr1", SqsMessageAttribute.stringAttribute("value1"));
            messageAttributes.put("attr2", SqsMessageAttribute.numberAttribute("42"));

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-456")
                    .body("complete test message")
                    .receiptHandle("receipt-handle-789")
                    .md5OfBody("body-md5-hash")
                    .md5OfMessageAttributes("attrs-md5-hash")
                    .attributes(attributes)
                    .messageAttributes(messageAttributes)
                    .timestamp(timestamp)
                    .build();

            assertThat(message.getMessageId()).isEqualTo("msg-456");
            assertThat(message.getBody()).isEqualTo("complete test message");
            assertThat(message.getReceiptHandle()).isEqualTo("receipt-handle-789");
            assertThat(message.getMd5OfBody()).isEqualTo("body-md5-hash");
            assertThat(message.getMd5OfMessageAttributes()).isEqualTo("attrs-md5-hash");
            assertThat(message.getAttributes()).hasSize(2);
            assertThat(message.getMessageAttributes()).hasSize(2);
            assertThat(message.getTimestamp()).isEqualTo(timestamp);
        }

        @Test
        void shouldAllowNullValues() {
            SqsMessage message = SqsMessage.builder()
                    .messageId(null)
                    .body(null)
                    .receiptHandle(null)
                    .md5OfBody(null)
                    .md5OfMessageAttributes(null)
                    .attributes(null)
                    .messageAttributes(null)
                    .timestamp(null)
                    .build();

            assertThat(message.getMessageId()).isNull();
            assertThat(message.getBody()).isNull();
            assertThat(message.getReceiptHandle()).isNull();
            assertThat(message.getMd5OfBody()).isNull();
            assertThat(message.getMd5OfMessageAttributes()).isNull();
            assertThat(message.getAttributes()).isEmpty(); // Null becomes empty map
            assertThat(message.getMessageAttributes()).isEmpty(); // Null becomes empty map
            assertThat(message.getTimestamp()).isNull();
        }

        @Test
        void shouldCreateBuilderFromScratch() {
            SqsMessage.Builder builder = SqsMessage.builder();
            
            SqsMessage message = builder
                    .messageId("test-id")
                    .body("test-body")
                    .build();

            assertThat(message.getMessageId()).isEqualTo("test-id");
            assertThat(message.getBody()).isEqualTo("test-body");
        }
    }

    @Nested
    class AccessorMethodTests {

        @Test
        void shouldProvideAllGetterMethods() {
            Instant timestamp = Instant.parse("2023-01-01T12:00:00Z");
            Map<String, String> attributes = Map.of("key", "value");
            Map<String, SqsMessageAttribute> messageAttributes = Map.of(
                    "attr", SqsMessageAttribute.stringAttribute("attrValue")
            );

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("message-body")
                    .receiptHandle("receipt")
                    .md5OfBody("md5-body")
                    .md5OfMessageAttributes("md5-attrs")
                    .attributes(attributes)
                    .messageAttributes(messageAttributes)
                    .timestamp(timestamp)
                    .build();

            assertThat(message.getMessageId()).isEqualTo("msg-id");
            assertThat(message.getBody()).isEqualTo("message-body");
            assertThat(message.getReceiptHandle()).isEqualTo("receipt");
            assertThat(message.getMd5OfBody()).isEqualTo("md5-body");
            assertThat(message.getMd5OfMessageAttributes()).isEqualTo("md5-attrs");
            assertThat(message.getAttributes()).isEqualTo(attributes);
            assertThat(message.getMessageAttributes()).isEqualTo(messageAttributes);
            assertThat(message.getTimestamp()).isEqualTo(timestamp);
        }

        @Test
        void shouldGetSpecificAttributeByName() {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("AttributeOne", "ValueOne");
            attributes.put("AttributeTwo", "ValueTwo");

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .attributes(attributes)
                    .build();

            assertThat(message.getAttribute("AttributeOne")).isEqualTo("ValueOne");
            assertThat(message.getAttribute("AttributeTwo")).isEqualTo("ValueTwo");
            assertThat(message.getAttribute("NonExistent")).isNull();
        }

        @Test
        void shouldGetSpecificMessageAttributeByName() {
            Map<String, SqsMessageAttribute> messageAttributes = new HashMap<>();
            messageAttributes.put("StringAttr", SqsMessageAttribute.stringAttribute("StringValue"));
            messageAttributes.put("NumberAttr", SqsMessageAttribute.numberAttribute("123"));

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .messageAttributes(messageAttributes)
                    .build();

            SqsMessageAttribute stringAttr = message.getMessageAttribute("StringAttr");
            assertThat(stringAttr.getStringValue()).isEqualTo("StringValue");

            SqsMessageAttribute numberAttr = message.getMessageAttribute("NumberAttr");
            assertThat(numberAttr.getStringValue()).isEqualTo("123");

            assertThat(message.getMessageAttribute("NonExistent")).isNull();
        }
    }

    @Nested
    class ConvenienceMethodTests {

        @Test
        void shouldProvideConvenienceMethodsForStandardAttributes() {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("ApproximateReceiveCount", "3");
            attributes.put("ApproximateFirstReceiveTimestamp", "1234567890123");
            attributes.put("SentTimestamp", "1234567890000");
            attributes.put("SenderId", "sender-id-123");

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .attributes(attributes)
                    .build();

            assertThat(message.getApproximateReceiveCount()).isEqualTo("3");
            assertThat(message.getApproximateFirstReceiveTimestamp()).isEqualTo("1234567890123");
            assertThat(message.getSentTimestamp()).isEqualTo("1234567890000");
            assertThat(message.getSenderId()).isEqualTo("sender-id-123");
        }

        @Test
        void shouldReturnNullForMissingStandardAttributes() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .build();

            assertThat(message.getApproximateReceiveCount()).isNull();
            assertThat(message.getApproximateFirstReceiveTimestamp()).isNull();
            assertThat(message.getSentTimestamp()).isNull();
            assertThat(message.getSenderId()).isNull();
        }

        @Test
        void shouldHandlePartialStandardAttributes() {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("SenderId", "sender-only");

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .attributes(attributes)
                    .build();

            assertThat(message.getSenderId()).isEqualTo("sender-only");
            assertThat(message.getApproximateReceiveCount()).isNull();
            assertThat(message.getApproximateFirstReceiveTimestamp()).isNull();
            assertThat(message.getSentTimestamp()).isNull();
        }
    }

    @Nested
    class ImmutabilityTests {

        @Test
        void shouldReturnImmutableAttributesMaps() {
            Map<String, String> originalAttributes = new HashMap<>();
            originalAttributes.put("key", "value");

            Map<String, SqsMessageAttribute> originalMessageAttributes = new HashMap<>();
            originalMessageAttributes.put("attr", SqsMessageAttribute.stringAttribute("value"));

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .attributes(originalAttributes)
                    .messageAttributes(originalMessageAttributes)
                    .build();

            // Verify we get immutable copies
            Map<String, String> retrievedAttributes = message.getAttributes();
            Map<String, SqsMessageAttribute> retrievedMessageAttributes = message.getMessageAttributes();

            // Should throw UnsupportedOperationException when trying to modify
            assertThat(retrievedAttributes).isNotSameAs(originalAttributes);
            assertThat(retrievedMessageAttributes).isNotSameAs(originalMessageAttributes);

            // Verify original modifications don't affect the message
            originalAttributes.put("new-key", "new-value");
            originalMessageAttributes.put("new-attr", SqsMessageAttribute.stringAttribute("new-value"));

            assertThat(message.getAttributes()).hasSize(1);
            assertThat(message.getMessageAttributes()).hasSize(1);
            assertThat(message.getAttribute("new-key")).isNull();
            assertThat(message.getMessageAttribute("new-attr")).isNull();
        }

        @Test
        void shouldCreateImmutableCopiesOfNullMaps() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .attributes(null)
                    .messageAttributes(null)
                    .build();

            assertThat(message.getAttributes()).isEmpty();
            assertThat(message.getMessageAttributes()).isEmpty();
        }
    }

    @Nested
    class EqualityAndHashCodeTests {

        @Test
        void shouldBeEqualForSameValues() {
            Instant timestamp = Instant.now();
            Map<String, String> attributes = Map.of("key", "value");
            Map<String, SqsMessageAttribute> messageAttributes = Map.of(
                    "attr", SqsMessageAttribute.stringAttribute("value")
            );

            SqsMessage message1 = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .receiptHandle("receipt")
                    .md5OfBody("md5-body")
                    .md5OfMessageAttributes("md5-attrs")
                    .attributes(attributes)
                    .messageAttributes(messageAttributes)
                    .timestamp(timestamp)
                    .build();

            SqsMessage message2 = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .receiptHandle("receipt")
                    .md5OfBody("md5-body")
                    .md5OfMessageAttributes("md5-attrs")
                    .attributes(attributes)
                    .messageAttributes(messageAttributes)
                    .timestamp(timestamp)
                    .build();

            assertThat(message1).isEqualTo(message2);
            assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
        }

        @Test
        void shouldNotBeEqualForDifferentMessageIds() {
            SqsMessage message1 = SqsMessage.builder()
                    .messageId("msg-1")
                    .body("body")
                    .build();

            SqsMessage message2 = SqsMessage.builder()
                    .messageId("msg-2")
                    .body("body")
                    .build();

            assertThat(message1).isNotEqualTo(message2);
        }

        @Test
        void shouldNotBeEqualForDifferentBodies() {
            SqsMessage message1 = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body-1")
                    .build();

            SqsMessage message2 = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body-2")
                    .build();

            assertThat(message1).isNotEqualTo(message2);
        }

        @Test
        void shouldNotBeEqualForDifferentAttributes() {
            SqsMessage message1 = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .attributes(Map.of("key", "value1"))
                    .build();

            SqsMessage message2 = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .attributes(Map.of("key", "value2"))
                    .build();

            assertThat(message1).isNotEqualTo(message2);
        }

        @Test
        void shouldNotBeEqualToNull() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .build();

            assertThat(message).isNotEqualTo(null);
        }

        @Test
        void shouldNotBeEqualToDifferentClass() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .build();

            assertThat(message).isNotEqualTo("string");
        }

        @Test
        void shouldBeEqualToItself() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-id")
                    .body("body")
                    .build();

            assertThat(message).isEqualTo(message);
        }

        @Test
        void shouldHandleNullFieldsInEquality() {
            SqsMessage message1 = SqsMessage.builder()
                    .messageId(null)
                    .body(null)
                    .build();

            SqsMessage message2 = SqsMessage.builder()
                    .messageId(null)
                    .body(null)
                    .build();

            assertThat(message1).isEqualTo(message2);
            assertThat(message1.hashCode()).isEqualTo(message2.hashCode());
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void shouldProvideReadableToString() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-123")
                    .body("test message body")
                    .receiptHandle("receipt-456")
                    .build();

            String result = message.toString();

            assertThat(result).contains("SqsMessage");
            assertThat(result).contains("messageId='msg-123'");
            assertThat(result).contains("bodyLength=17"); // "test message body".length()
            assertThat(result).contains("receiptHandle='receipt-456'");
            assertThat(result).contains("attributeCount=0");
            assertThat(result).contains("messageAttributeCount=0");
        }

        @Test
        void shouldHandleNullBodyInToString() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-123")
                    .body(null)
                    .build();

            String result = message.toString();

            assertThat(result).contains("bodyLength=0");
        }

        @Test
        void shouldShowCorrectCounts() {
            Map<String, String> attributes = Map.of("attr1", "value1", "attr2", "value2");
            Map<String, SqsMessageAttribute> messageAttributes = Map.of(
                    "msgAttr1", SqsMessageAttribute.stringAttribute("value1"),
                    "msgAttr2", SqsMessageAttribute.numberAttribute("42"),
                    "msgAttr3", SqsMessageAttribute.binaryAttribute("binary".getBytes())
            );

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-123")
                    .body("test body")
                    .attributes(attributes)
                    .messageAttributes(messageAttributes)
                    .build();

            String result = message.toString();

            assertThat(result).contains("attributeCount=2");
            assertThat(result).contains("messageAttributeCount=3");
        }

        @Test
        void shouldIncludeTimestamp() {
            Instant timestamp = Instant.parse("2023-01-01T12:00:00Z");
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-123")
                    .body("test")
                    .timestamp(timestamp)
                    .build();

            String result = message.toString();

            assertThat(result).contains("timestamp=2023-01-01T12:00:00Z");
        }
    }
}