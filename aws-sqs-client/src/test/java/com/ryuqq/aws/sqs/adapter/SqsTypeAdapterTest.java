package com.ryuqq.aws.sqs.adapter;

import com.ryuqq.aws.sqs.types.SqsMessage;
import com.ryuqq.aws.sqs.types.SqsMessageAttribute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SqsTypeAdapter.
 * Tests conversion logic between library types and AWS SDK types.
 */
class SqsTypeAdapterTest {

    @Nested
    class AwsMessageToSqsMessageConversionTests {

        @Test
        void shouldConvertNullMessageToNull() {
            SqsMessage result = SqsTypeAdapter.fromAwsMessage(null);
            assertThat(result).isNull();
        }

        @Test
        void shouldConvertBasicMessage() {
            Message awsMessage = Message.builder()
                    .messageId("msg-123")
                    .body("test message body")
                    .receiptHandle("receipt-handle-123")
                    .md5OfBody("md5-hash")
                    .build();

            SqsMessage sqsMessage = SqsTypeAdapter.fromAwsMessage(awsMessage);

            assertThat(sqsMessage).isNotNull();
            assertThat(sqsMessage.getMessageId()).isEqualTo("msg-123");
            assertThat(sqsMessage.getBody()).isEqualTo("test message body");
            assertThat(sqsMessage.getReceiptHandle()).isEqualTo("receipt-handle-123");
            assertThat(sqsMessage.getMd5OfBody()).isEqualTo("md5-hash");
            assertThat(sqsMessage.getTimestamp()).isNotNull();
        }

        @Test
        void shouldConvertMessageWithAttributes() {
            Map<MessageSystemAttributeName, String> attributes = new HashMap<>();
            attributes.put(MessageSystemAttributeName.SENDER_ID, "sender-123");
            attributes.put(MessageSystemAttributeName.SENT_TIMESTAMP, "1234567890");
            attributes.put(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1");

            Message awsMessage = Message.builder()
                    .messageId("msg-123")
                    .body("test body")
                    .attributes(attributes)
                    .build();

            SqsMessage sqsMessage = SqsTypeAdapter.fromAwsMessage(awsMessage);

            assertThat(sqsMessage.getAttributes()).hasSize(3);
            assertThat(sqsMessage.getAttribute("SenderId")).isEqualTo("sender-123");
            assertThat(sqsMessage.getAttribute("SentTimestamp")).isEqualTo("1234567890");
            assertThat(sqsMessage.getAttribute("ApproximateReceiveCount")).isEqualTo("1");
        }

        @Test
        void shouldConvertMessageWithMessageAttributes() {
            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("stringAttr", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue("test-string")
                    .build());
            messageAttributes.put("numberAttr", MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue("42")
                    .build());
            messageAttributes.put("binaryAttr", MessageAttributeValue.builder()
                    .dataType("Binary")
                    .binaryValue(SdkBytes.fromUtf8String("binary-data"))
                    .build());

            Message awsMessage = Message.builder()
                    .messageId("msg-123")
                    .body("test body")
                    .messageAttributes(messageAttributes)
                    .build();

            SqsMessage sqsMessage = SqsTypeAdapter.fromAwsMessage(awsMessage);

            assertThat(sqsMessage.getMessageAttributes()).hasSize(3);
            
            SqsMessageAttribute stringAttr = sqsMessage.getMessageAttribute("stringAttr");
            assertThat(stringAttr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.STRING);
            assertThat(stringAttr.getStringValue()).isEqualTo("test-string");

            SqsMessageAttribute numberAttr = sqsMessage.getMessageAttribute("numberAttr");
            assertThat(numberAttr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(numberAttr.getStringValue()).isEqualTo("42");

            SqsMessageAttribute binaryAttr = sqsMessage.getMessageAttribute("binaryAttr");
            assertThat(binaryAttr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.BINARY);
            assertThat(binaryAttr.getBinaryValue()).isEqualTo("binary-data".getBytes());
        }

        @Test
        void shouldConvertMessageWithAllFields() {
            Map<MessageSystemAttributeName, String> attributes = new HashMap<>();
            attributes.put(MessageSystemAttributeName.SENDER_ID, "sender-123");

            Map<String, MessageAttributeValue> messageAttributes = new HashMap<>();
            messageAttributes.put("customAttr", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue("custom-value")
                    .build());

            Message awsMessage = Message.builder()
                    .messageId("msg-123")
                    .body("complete test message")
                    .receiptHandle("receipt-handle-456")
                    .md5OfBody("body-md5")
                    .md5OfMessageAttributes("attrs-md5")
                    .attributes(attributes)
                    .messageAttributes(messageAttributes)
                    .build();

            SqsMessage sqsMessage = SqsTypeAdapter.fromAwsMessage(awsMessage);

            assertThat(sqsMessage.getMessageId()).isEqualTo("msg-123");
            assertThat(sqsMessage.getBody()).isEqualTo("complete test message");
            assertThat(sqsMessage.getReceiptHandle()).isEqualTo("receipt-handle-456");
            assertThat(sqsMessage.getMd5OfBody()).isEqualTo("body-md5");
            assertThat(sqsMessage.getMd5OfMessageAttributes()).isEqualTo("attrs-md5");
            assertThat(sqsMessage.getAttributes()).hasSize(1);
            assertThat(sqsMessage.getMessageAttributes()).hasSize(1);
        }

        @Test
        void shouldConvertListOfMessages() {
            Message msg1 = Message.builder()
                    .messageId("msg-1")
                    .body("first message")
                    .build();

            Message msg2 = Message.builder()
                    .messageId("msg-2")
                    .body("second message")
                    .build();

            List<Message> awsMessages = List.of(msg1, msg2);

            List<SqsMessage> sqsMessages = SqsTypeAdapter.fromAwsMessages(awsMessages);

            assertThat(sqsMessages).hasSize(2);
            assertThat(sqsMessages.get(0).getMessageId()).isEqualTo("msg-1");
            assertThat(sqsMessages.get(1).getMessageId()).isEqualTo("msg-2");
        }

        @Test
        void shouldReturnEmptyListForNullMessages() {
            List<SqsMessage> result = SqsTypeAdapter.fromAwsMessages(null);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class SqsMessageAttributeConversionTests {

        @Test
        void shouldConvertNullAttributeToNull() {
            MessageAttributeValue result = SqsTypeAdapter.toAwsMessageAttributeValue(null);
            assertThat(result).isNull();

            SqsMessageAttribute result2 = SqsTypeAdapter.fromAwsMessageAttributeValue(null);
            assertThat(result2).isNull();
        }

        @Test
        void shouldConvertStringAttribute() {
            SqsMessageAttribute sqsAttr = SqsMessageAttribute.stringAttribute("test-string");

            MessageAttributeValue awsAttr = SqsTypeAdapter.toAwsMessageAttributeValue(sqsAttr);

            assertThat(awsAttr.dataType()).isEqualTo("String");
            assertThat(awsAttr.stringValue()).isEqualTo("test-string");
            assertThat(awsAttr.binaryValue()).isNull();
        }

        @Test
        void shouldConvertNumberAttribute() {
            SqsMessageAttribute sqsAttr = SqsMessageAttribute.numberAttribute("42.5");

            MessageAttributeValue awsAttr = SqsTypeAdapter.toAwsMessageAttributeValue(sqsAttr);

            assertThat(awsAttr.dataType()).isEqualTo("Number");
            assertThat(awsAttr.stringValue()).isEqualTo("42.5");
            assertThat(awsAttr.binaryValue()).isNull();
        }

        @Test
        void shouldConvertBinaryAttribute() {
            byte[] binaryData = "test-binary-data".getBytes();
            SqsMessageAttribute sqsAttr = SqsMessageAttribute.binaryAttribute(binaryData);

            MessageAttributeValue awsAttr = SqsTypeAdapter.toAwsMessageAttributeValue(sqsAttr);

            assertThat(awsAttr.dataType()).isEqualTo("Binary");
            assertThat(awsAttr.stringValue()).isNull();
            assertThat(awsAttr.binaryValue()).isNotNull();
            assertThat(awsAttr.binaryValue().asByteArray()).isEqualTo(binaryData);
        }

        @Test
        void shouldConvertAwsStringAttributeToSqs() {
            MessageAttributeValue awsAttr = MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue("aws-string")
                    .build();

            SqsMessageAttribute sqsAttr = SqsTypeAdapter.fromAwsMessageAttributeValue(awsAttr);

            assertThat(sqsAttr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.STRING);
            assertThat(sqsAttr.getStringValue()).isEqualTo("aws-string");
            assertThat(sqsAttr.getBinaryValue()).isNull();
        }

        @Test
        void shouldConvertAwsNumberAttributeToSqs() {
            MessageAttributeValue awsAttr = MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue("123")
                    .build();

            SqsMessageAttribute sqsAttr = SqsTypeAdapter.fromAwsMessageAttributeValue(awsAttr);

            assertThat(sqsAttr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(sqsAttr.getStringValue()).isEqualTo("123");
            assertThat(sqsAttr.getBinaryValue()).isNull();
        }

        @Test
        void shouldConvertAwsBinaryAttributeToSqs() {
            byte[] binaryData = "aws-binary".getBytes();
            MessageAttributeValue awsAttr = MessageAttributeValue.builder()
                    .dataType("Binary")
                    .binaryValue(SdkBytes.fromByteArray(binaryData))
                    .build();

            SqsMessageAttribute sqsAttr = SqsTypeAdapter.fromAwsMessageAttributeValue(awsAttr);

            assertThat(sqsAttr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.BINARY);
            assertThat(sqsAttr.getBinaryValue()).isEqualTo(binaryData);
            assertThat(sqsAttr.getStringValue()).isNull();
        }

        @Test
        void shouldHandleAllSupportedDataTypes() {
            // Test that all enum values are supported - since DataType is an enum,
            // we can't easily create an unsupported one. This test validates current behavior.
            SqsMessageAttribute stringAttr = SqsMessageAttribute.stringAttribute("test");
            SqsMessageAttribute numberAttr = SqsMessageAttribute.numberAttribute("42");
            SqsMessageAttribute binaryAttr = SqsMessageAttribute.binaryAttribute("data".getBytes());

            MessageAttributeValue stringResult = SqsTypeAdapter.toAwsMessageAttributeValue(stringAttr);
            MessageAttributeValue numberResult = SqsTypeAdapter.toAwsMessageAttributeValue(numberAttr);
            MessageAttributeValue binaryResult = SqsTypeAdapter.toAwsMessageAttributeValue(binaryAttr);

            assertThat(stringResult).isNotNull();
            assertThat(numberResult).isNotNull();
            assertThat(binaryResult).isNotNull();
        }

        @Test
        void shouldHandleRoundTripConversion() {
            // Test string round trip
            SqsMessageAttribute originalString = SqsMessageAttribute.stringAttribute("round-trip-test");
            MessageAttributeValue awsString = SqsTypeAdapter.toAwsMessageAttributeValue(originalString);
            SqsMessageAttribute convertedString = SqsTypeAdapter.fromAwsMessageAttributeValue(awsString);
            
            assertThat(convertedString.getDataType()).isEqualTo(originalString.getDataType());
            assertThat(convertedString.getStringValue()).isEqualTo(originalString.getStringValue());

            // Test binary round trip
            byte[] originalData = "round-trip-binary".getBytes();
            SqsMessageAttribute originalBinary = SqsMessageAttribute.binaryAttribute(originalData);
            MessageAttributeValue awsBinary = SqsTypeAdapter.toAwsMessageAttributeValue(originalBinary);
            SqsMessageAttribute convertedBinary = SqsTypeAdapter.fromAwsMessageAttributeValue(awsBinary);
            
            assertThat(convertedBinary.getDataType()).isEqualTo(originalBinary.getDataType());
            assertThat(convertedBinary.getBinaryValue()).isEqualTo(originalBinary.getBinaryValue());
        }
    }

    @Nested
    class MessageAttributeMapConversionTests {

        @Test
        void shouldConvertNullMapToEmptyMap() {
            Map<String, MessageAttributeValue> result = SqsTypeAdapter.toAwsMessageAttributes(null);
            assertThat(result).isEmpty();

            Map<String, SqsMessageAttribute> result2 = SqsTypeAdapter.fromAwsMessageAttributes(null);
            assertThat(result2).isEmpty();
        }

        @Test
        void shouldConvertEmptyMapToEmptyMap() {
            Map<String, SqsMessageAttribute> emptyMap = new HashMap<>();
            Map<String, MessageAttributeValue> result = SqsTypeAdapter.toAwsMessageAttributes(emptyMap);
            assertThat(result).isEmpty();
        }

        @Test
        void shouldConvertSqsAttributeMapToAws() {
            Map<String, SqsMessageAttribute> sqsAttributes = new HashMap<>();
            sqsAttributes.put("attr1", SqsMessageAttribute.stringAttribute("value1"));
            sqsAttributes.put("attr2", SqsMessageAttribute.numberAttribute("42"));
            sqsAttributes.put("attr3", SqsMessageAttribute.binaryAttribute("binary".getBytes()));

            Map<String, MessageAttributeValue> awsAttributes = SqsTypeAdapter.toAwsMessageAttributes(sqsAttributes);

            assertThat(awsAttributes).hasSize(3);
            assertThat(awsAttributes.get("attr1").dataType()).isEqualTo("String");
            assertThat(awsAttributes.get("attr1").stringValue()).isEqualTo("value1");
            assertThat(awsAttributes.get("attr2").dataType()).isEqualTo("Number");
            assertThat(awsAttributes.get("attr2").stringValue()).isEqualTo("42");
            assertThat(awsAttributes.get("attr3").dataType()).isEqualTo("Binary");
        }

        @Test
        void shouldConvertAwsAttributeMapToSqs() {
            Map<String, MessageAttributeValue> awsAttributes = new HashMap<>();
            awsAttributes.put("attr1", MessageAttributeValue.builder()
                    .dataType("String")
                    .stringValue("aws-value1")
                    .build());
            awsAttributes.put("attr2", MessageAttributeValue.builder()
                    .dataType("Number")
                    .stringValue("99")
                    .build());

            Map<String, SqsMessageAttribute> sqsAttributes = SqsTypeAdapter.fromAwsMessageAttributes(awsAttributes);

            assertThat(sqsAttributes).hasSize(2);
            assertThat(sqsAttributes.get("attr1").getDataType()).isEqualTo(SqsMessageAttribute.DataType.STRING);
            assertThat(sqsAttributes.get("attr1").getStringValue()).isEqualTo("aws-value1");
            assertThat(sqsAttributes.get("attr2").getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(sqsAttributes.get("attr2").getStringValue()).isEqualTo("99");
        }

        @Test
        void shouldHandleAttributeMapRoundTrip() {
            Map<String, SqsMessageAttribute> originalAttributes = new HashMap<>();
            originalAttributes.put("string", SqsMessageAttribute.stringAttribute("test"));
            originalAttributes.put("number", SqsMessageAttribute.numberAttribute("123"));
            originalAttributes.put("binary", SqsMessageAttribute.binaryAttribute("data".getBytes()));

            Map<String, MessageAttributeValue> awsAttributes = SqsTypeAdapter.toAwsMessageAttributes(originalAttributes);
            Map<String, SqsMessageAttribute> convertedAttributes = SqsTypeAdapter.fromAwsMessageAttributes(awsAttributes);

            assertThat(convertedAttributes).hasSize(3);
            assertThat(convertedAttributes.get("string").getStringValue()).isEqualTo("test");
            assertThat(convertedAttributes.get("number").getStringValue()).isEqualTo("123");
            assertThat(convertedAttributes.get("binary").getBinaryValue()).isEqualTo("data".getBytes());
        }
    }

    @Nested
    class ConvenienceMethodTests {

        @Test
        void shouldProvideConvenienceMethodsForSqsMessage() {
            Map<String, String> attributes = new HashMap<>();
            attributes.put("ApproximateReceiveCount", "2");
            attributes.put("ApproximateFirstReceiveTimestamp", "1234567890");
            attributes.put("SentTimestamp", "1234567000");
            attributes.put("SenderId", "test-sender");

            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-123")
                    .body("test")
                    .attributes(attributes)
                    .build();

            assertThat(message.getApproximateReceiveCount()).isEqualTo("2");
            assertThat(message.getApproximateFirstReceiveTimestamp()).isEqualTo("1234567890");
            assertThat(message.getSentTimestamp()).isEqualTo("1234567000");
            assertThat(message.getSenderId()).isEqualTo("test-sender");
        }

        @Test
        void shouldReturnNullForMissingAttributes() {
            SqsMessage message = SqsMessage.builder()
                    .messageId("msg-123")
                    .body("test")
                    .build();

            assertThat(message.getApproximateReceiveCount()).isNull();
            assertThat(message.getApproximateFirstReceiveTimestamp()).isNull();
            assertThat(message.getSentTimestamp()).isNull();
            assertThat(message.getSenderId()).isNull();
            assertThat(message.getAttribute("NonExistent")).isNull();
            assertThat(message.getMessageAttribute("NonExistent")).isNull();
        }
    }
}