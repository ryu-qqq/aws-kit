package com.ryuqq.aws.sns.types;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SnsMessage Unit Tests")
class SnsMessageTest {

    @Test
    @DisplayName("Should create SnsMessage with builder pattern")
    void shouldCreateSnsMessageWithBuilder() {
        // Given/When
        SnsMessage message = SnsMessage.builder()
                .body("Test message body")
                .subject("Test subject")
                .phoneNumber("+1234567890")
                .targetArn("arn:aws:sns:us-east-1:123456789012:endpoint/platform/endpoint-id")
                .messageGroupId("group-123")
                .messageDeduplicationId("dedup-456")
                .structure(SnsMessage.MessageStructure.JSON)
                .build();

        // Then
        assertThat(message.getBody()).isEqualTo("Test message body");
        assertThat(message.getSubject()).isEqualTo("Test subject");
        assertThat(message.getPhoneNumber()).isEqualTo("+1234567890");
        assertThat(message.getTargetArn()).isEqualTo("arn:aws:sns:us-east-1:123456789012:endpoint/platform/endpoint-id");
        assertThat(message.getMessageGroupId()).isEqualTo("group-123");
        assertThat(message.getMessageDeduplicationId()).isEqualTo("dedup-456");
        assertThat(message.getStructure()).isEqualTo(SnsMessage.MessageStructure.JSON);
    }

    @Test
    @DisplayName("Should create SnsMessage with defaults")
    void shouldCreateSnsMessageWithDefaults() {
        // Given/When
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .build();

        // Then
        assertThat(message.getBody()).isEqualTo("Test message");
        assertThat(message.getSubject()).isNull();
        assertThat(message.getPhoneNumber()).isNull();
        assertThat(message.getTargetArn()).isNull();
        assertThat(message.getMessageGroupId()).isNull();
        assertThat(message.getMessageDeduplicationId()).isNull();
        assertThat(message.getStructure()).isEqualTo(SnsMessage.MessageStructure.RAW);
        assertThat(message.getAttributes()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Should require non-null body")
    void shouldRequireNonNullBody() {
        // Given/When/Then
        assertThatThrownBy(() ->
                SnsMessage.builder()
                        .body(null)
                        .build()
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should create SnsMessage using of() static factory with body only")
    void shouldCreateSnsMessageUsingOfWithBodyOnly() {
        // Given/When
        SnsMessage message = SnsMessage.of("Simple message");

        // Then
        assertThat(message.getBody()).isEqualTo("Simple message");
        assertThat(message.getSubject()).isNull();
        assertThat(message.getStructure()).isEqualTo(SnsMessage.MessageStructure.RAW);
        assertThat(message.getAttributes()).isEmpty();
    }

    @Test
    @DisplayName("Should create SnsMessage using of() static factory with subject and body")
    void shouldCreateSnsMessageUsingOfWithSubjectAndBody() {
        // Given/When
        SnsMessage message = SnsMessage.of("Test Subject", "Test message body");

        // Then
        assertThat(message.getSubject()).isEqualTo("Test Subject");
        assertThat(message.getBody()).isEqualTo("Test message body");
        assertThat(message.getStructure()).isEqualTo(SnsMessage.MessageStructure.RAW);
        assertThat(message.getAttributes()).isEmpty();
    }

    @Test
    @DisplayName("Should add attribute using withAttribute method")
    void shouldAddAttributeUsingWithAttribute() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .build();

        // When
        SnsMessage result = message.withAttribute("correlationId", "12345")
                                  .withAttribute("messageType", "ORDER");

        // Then
        assertThat(result).isSameAs(message); // Should return same instance
        assertThat(message.getAttributes()).hasSize(2);
        assertThat(message.getAttributes()).containsEntry("correlationId", "12345");
        assertThat(message.getAttributes()).containsEntry("messageType", "ORDER");
    }

    @Test
    @DisplayName("Should handle chained withAttribute calls")
    void shouldHandleChainedWithAttributeCalls() {
        // Given/When
        SnsMessage message = SnsMessage.of("Test message")
                .withAttribute("key1", "value1")
                .withAttribute("key2", "value2")
                .withAttribute("key3", "value3");

        // Then
        assertThat(message.getAttributes()).hasSize(3);
        assertThat(message.getAttributes()).containsEntry("key1", "value1");
        assertThat(message.getAttributes()).containsEntry("key2", "value2");
        assertThat(message.getAttributes()).containsEntry("key3", "value3");
    }

    @Test
    @DisplayName("Should overwrite existing attribute when adding with same key")
    void shouldOverwriteExistingAttributeWhenAddingWithSameKey() {
        // Given
        SnsMessage message = SnsMessage.of("Test message")
                .withAttribute("key1", "originalValue");

        // When
        message.withAttribute("key1", "newValue");

        // Then
        assertThat(message.getAttributes()).hasSize(1);
        assertThat(message.getAttributes()).containsEntry("key1", "newValue");
    }

    @Test
    @DisplayName("Should create JSON structured message")
    void shouldCreateJsonStructuredMessage() {
        // Given
        Map<String, String> protocolMessages = new HashMap<>();
        protocolMessages.put("default", "Default message");
        protocolMessages.put("email", "Email specific message");
        protocolMessages.put("sms", "SMS specific message");

        // When
        SnsMessage message = SnsMessage.jsonMessage(protocolMessages);

        // Then
        assertThat(message.getStructure()).isEqualTo(SnsMessage.MessageStructure.JSON);
        assertThat(message.getBody()).contains("\"default\":\"Default message\"");
        assertThat(message.getBody()).contains("\"email\":\"Email specific message\"");
        assertThat(message.getBody()).contains("\"sms\":\"SMS specific message\"");
    }

    @Test
    @DisplayName("Should create valid JSON structure for protocol messages")
    void shouldCreateValidJsonStructureForProtocolMessages() {
        // Given
        Map<String, String> protocolMessages = new HashMap<>();
        protocolMessages.put("default", "Hello World");
        protocolMessages.put("email", "Email: Hello World");

        // When
        SnsMessage message = SnsMessage.jsonMessage(protocolMessages);

        // Then
        String expectedJson1 = "{\"default\":\"Hello World\",\"email\":\"Email: Hello World\"}";
        String expectedJson2 = "{\"email\":\"Email: Hello World\",\"default\":\"Hello World\"}";
        
        // JSON order may vary, so check both possibilities
        assertThat(message.getBody()).satisfiesAnyOf(
                body -> assertThat(body).isEqualTo(expectedJson1),
                body -> assertThat(body).isEqualTo(expectedJson2)
        );
    }

    @ParameterizedTest
    @MethodSource("provideJsonEscapeTestCases")
    @DisplayName("Should properly escape JSON special characters")
    void shouldProperlyEscapeJsonSpecialCharacters(String input, String expectedEscaped) {
        // Given
        Map<String, String> protocolMessages = new HashMap<>();
        protocolMessages.put("default", input);

        // When
        SnsMessage message = SnsMessage.jsonMessage(protocolMessages);

        // Then
        String expectedJson = "{\"default\":\"" + expectedEscaped + "\"}";
        assertThat(message.getBody()).isEqualTo(expectedJson);
    }

    static Stream<Arguments> provideJsonEscapeTestCases() {
        return Stream.of(
                Arguments.of("Simple text", "Simple text"),
                Arguments.of("Text with \"quotes\"", "Text with \\\"quotes\\\""),
                Arguments.of("Text with\nnewline", "Text with\\nnewline"),
                Arguments.of("Text with\rcarriage return", "Text with\\rcarriage return"),
                Arguments.of("Text with\ttab", "Text with\\ttab"),
                Arguments.of("Complex: \"Hello\"\nWorld\t!", "Complex: \\\"Hello\\\"\\nWorld\\t!"),
                Arguments.of("", ""), // Empty string
                Arguments.of("Only\\backslash", "Only\\backslash") // Backslash should remain as is
        );
    }

    @Test
    @DisplayName("Should handle empty protocol messages map")
    void shouldHandleEmptyProtocolMessagesMap() {
        // Given
        Map<String, String> emptyMap = new HashMap<>();

        // When
        SnsMessage message = SnsMessage.jsonMessage(emptyMap);

        // Then
        assertThat(message.getBody()).isEqualTo("{}");
        assertThat(message.getStructure()).isEqualTo(SnsMessage.MessageStructure.JSON);
    }

    @Test
    @DisplayName("Should handle single protocol message")
    void shouldHandleSingleProtocolMessage() {
        // Given
        Map<String, String> singleMessage = new HashMap<>();
        singleMessage.put("sms", "SMS only message");

        // When
        SnsMessage message = SnsMessage.jsonMessage(singleMessage);

        // Then
        assertThat(message.getBody()).isEqualTo("{\"sms\":\"SMS only message\"}");
        assertThat(message.getStructure()).isEqualTo(SnsMessage.MessageStructure.JSON);
    }

    @Test
    @DisplayName("MessageStructure enum should have correct values")
    void messageStructureEnumShouldHaveCorrectValues() {
        // Then
        assertThat(SnsMessage.MessageStructure.RAW.getValue()).isEqualTo("raw");
        assertThat(SnsMessage.MessageStructure.JSON.getValue()).isEqualTo("json");
    }

    @Test
    @DisplayName("Should create attributes map when not provided in builder")
    void shouldCreateAttributesMapWhenNotProvidedInBuilder() {
        // Given/When
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .build();

        // Then
        assertThat(message.getAttributes()).isNotNull();
        assertThat(message.getAttributes()).isEmpty();
        
        // Should be able to add attributes
        message.withAttribute("test", "value");
        assertThat(message.getAttributes()).hasSize(1);
    }

    @Test
    @DisplayName("Should use provided attributes map from builder")
    void shouldUseProvidedAttributesMapFromBuilder() {
        // Given
        Map<String, String> providedAttributes = new HashMap<>();
        providedAttributes.put("prePopulated", "value");

        // When
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .attributes(providedAttributes)
                .build();

        // Then
        assertThat(message.getAttributes()).isSameAs(providedAttributes);
        assertThat(message.getAttributes()).containsEntry("prePopulated", "value");
        
        // Should be able to add more attributes
        message.withAttribute("additional", "value2");
        assertThat(message.getAttributes()).hasSize(2);
    }

    @Test
    @DisplayName("Should handle null and empty values in withAttribute")
    void shouldHandleNullAndEmptyValuesInWithAttribute() {
        // Given
        SnsMessage message = SnsMessage.of("Test message");

        // When/Then - null key should not cause exception but will put null key
        assertThatCode(() -> message.withAttribute(null, "value")).doesNotThrowAnyException();
        assertThat(message.getAttributes()).containsEntry(null, "value");

        // null value should be allowed
        assertThatCode(() -> message.withAttribute("key", null)).doesNotThrowAnyException();
        assertThat(message.getAttributes()).containsEntry("key", null);

        // Empty string key and value should be allowed
        assertThatCode(() -> message.withAttribute("", "")).doesNotThrowAnyException();
        assertThat(message.getAttributes()).containsEntry("", "");
    }

    @Test
    @DisplayName("Should maintain builder immutability for optional fields")
    void shouldMaintainBuilderImmutabilityForOptionalFields() {
        // Given
        SnsMessage.SnsMessageBuilder builder = SnsMessage.builder().body("Test");

        // When
        SnsMessage message1 = builder.subject("Subject 1").build();
        SnsMessage message2 = builder.subject("Subject 2").build();

        // Then - Second build should override the first (standard Lombok behavior)
        assertThat(message1.getSubject()).isEqualTo("Subject 1");
        assertThat(message2.getSubject()).isEqualTo("Subject 2");
    }

    @Test
    @DisplayName("Should generate meaningful toString representation")
    void shouldGenerateMeaningfulToStringRepresentation() {
        // Given
        SnsMessage message = SnsMessage.builder()
                .body("Test message")
                .subject("Test subject")
                .messageGroupId("group-123")
                .build()
                .withAttribute("key1", "value1");

        // When
        String toString = message.toString();

        // Then
        assertThat(toString).contains("body=Test message");
        assertThat(toString).contains("subject=Test subject");
        assertThat(toString).contains("messageGroupId=group-123");
        assertThat(toString).contains("attributes=");
        assertThat(toString).contains("SnsMessage");
    }

    @Test
    @DisplayName("Should handle protocol messages with special keys")
    void shouldHandleProtocolMessagesWithSpecialKeys() {
        // Given
        Map<String, String> protocolMessages = new HashMap<>();
        protocolMessages.put("APNS", "Apple Push Notification");
        protocolMessages.put("APNS_SANDBOX", "Apple Push Notification Sandbox");
        protocolMessages.put("GCM", "Google Cloud Messaging");
        protocolMessages.put("ADM", "Amazon Device Messaging");

        // When
        SnsMessage message = SnsMessage.jsonMessage(protocolMessages);

        // Then
        assertThat(message.getBody()).contains("\"APNS\":\"Apple Push Notification\"");
        assertThat(message.getBody()).contains("\"APNS_SANDBOX\":\"Apple Push Notification Sandbox\"");
        assertThat(message.getBody()).contains("\"GCM\":\"Google Cloud Messaging\"");
        assertThat(message.getBody()).contains("\"ADM\":\"Amazon Device Messaging\"");
        assertThat(message.getStructure()).isEqualTo(SnsMessage.MessageStructure.JSON);
    }

    @Test
    @DisplayName("Should handle very long message body")
    void shouldHandleVeryLongMessageBody() {
        // Given
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            longMessage.append("This is a very long message segment ").append(i).append(". ");
        }
        String longMessageString = longMessage.toString();

        // When
        SnsMessage message = SnsMessage.of(longMessageString);

        // Then
        assertThat(message.getBody()).isEqualTo(longMessageString);
        assertThat(message.getBody().length()).isGreaterThan(30000); // Verify it's actually long
    }

    @Test
    @DisplayName("Should handle unicode characters in message body")
    void shouldHandleUnicodeCharactersInMessageBody() {
        // Given
        String unicodeMessage = "Hello World! 🌍 Héllo Wörld! こんにちは世界！ مرحبا بالعالم!";

        // When
        SnsMessage message = SnsMessage.of("Subject with émojis 🎉", unicodeMessage);

        // Then
        assertThat(message.getSubject()).isEqualTo("Subject with émojis 🎉");
        assertThat(message.getBody()).isEqualTo(unicodeMessage);
    }

    @Test
    @DisplayName("Should handle complex JSON structure escaping")
    void shouldHandleComplexJsonStructureEscaping() {
        // Given
        Map<String, String> protocolMessages = new HashMap<>();
        protocolMessages.put("default", "Simple message");
        protocolMessages.put("email", "{\"subject\":\"Alert\",\"body\":\"Line 1\\nLine 2\"}");

        // When
        SnsMessage message = SnsMessage.jsonMessage(protocolMessages);

        // Then
        assertThat(message.getBody()).contains("\"email\":\"{\\\"subject\\\":\\\"Alert\\\",\\\"body\\\":\\\"Line 1\\\\nLine 2\\\"}\"");
    }

    @Test
    @DisplayName("Should validate FIFO message fields")
    void shouldValidateFifoMessageFields() {
        // Given/When
        SnsMessage fifoMessage = SnsMessage.builder()
                .body("FIFO message")
                .messageGroupId("group.123")
                .messageDeduplicationId("dedup-id-456")
                .build();

        // Then
        assertThat(fifoMessage.getMessageGroupId()).isEqualTo("group.123");
        assertThat(fifoMessage.getMessageDeduplicationId()).isEqualTo("dedup-id-456");
        assertThat(fifoMessage.getBody()).isEqualTo("FIFO message");
    }
}