package com.ryuqq.aws.sqs.consumer.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DlqMessage class focusing on security and serialization correctness.
 */
class DlqMessageTest {
    
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }
    
    @Test
    @DisplayName("DlqMessage should serialize and deserialize correctly")
    void testSerializationDeserialization() throws Exception {
        // Arrange
        Instant testTime = Instant.parse("2023-10-01T12:00:00Z");
        Map<String, String> attributes = Map.of("key1", "value1", "key2", "value2");
        
        DlqMessage original = DlqMessage.builder()
            .originalMessageId("msg-123")
            .originalMessage("{\"data\":\"test\"}")
            .errorMessage("Processing failed")
            .errorType("RuntimeException")
            .timestamp(testTime)
            .containerId("container-1")
            .queueUrl("https://sqs.region.amazonaws.com/account/queue")
            .retryAttempts(3)
            .originalAttributes(attributes)
            .build();
        
        // Act - Serialize
        String json = objectMapper.writeValueAsString(original);
        
        // Act - Deserialize
        DlqMessage deserialized = objectMapper.readValue(json, DlqMessage.class);
        
        // Assert
        assertEquals(original.getOriginalMessageId(), deserialized.getOriginalMessageId());
        assertEquals(original.getOriginalMessage(), deserialized.getOriginalMessage());
        assertEquals(original.getErrorMessage(), deserialized.getErrorMessage());
        assertEquals(original.getErrorType(), deserialized.getErrorType());
        assertEquals(original.getTimestamp(), deserialized.getTimestamp());
        assertEquals(original.getContainerId(), deserialized.getContainerId());
        assertEquals(original.getQueueUrl(), deserialized.getQueueUrl());
        assertEquals(original.getRetryAttempts(), deserialized.getRetryAttempts());
        assertEquals(original.getOriginalAttributes(), deserialized.getOriginalAttributes());
    }
    
    @Test
    @DisplayName("DlqMessage should handle null values safely")
    void testNullValueHandling() throws Exception {
        // Arrange
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalMessageId(null)
            .originalMessage(null)
            .errorMessage(null)
            .errorType(null)
            .containerId("test")
            .queueUrl("https://test-queue")
            .build();
        
        // Act
        String json = objectMapper.writeValueAsString(dlqMessage);
        DlqMessage deserialized = objectMapper.readValue(json, DlqMessage.class);
        
        // Assert
        assertNull(deserialized.getOriginalMessageId());
        assertNull(deserialized.getOriginalMessage());
        assertNull(deserialized.getErrorMessage());
        assertNull(deserialized.getErrorType());
        assertEquals("test", deserialized.getContainerId());
        assertEquals("https://test-queue", deserialized.getQueueUrl());
        assertNotNull(deserialized.getTimestamp()); // Auto-generated in builder
    }
    
    @Test
    @DisplayName("DlqMessage should escape special characters properly")
    void testSpecialCharacterHandling() throws Exception {
        // Arrange
        String specialMessage = "Message with \"quotes\", \n newlines, \t tabs, and \\ backslashes";
        String specialError = "Error: \"Invalid JSON\" \\ Malformed structure";
        
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalMessageId("test-id")
            .originalMessage(specialMessage)
            .errorMessage(specialError)
            .errorType("JsonException")
            .containerId("container-1")
            .queueUrl("https://test-queue")
            .build();
        
        // Act
        String json = objectMapper.writeValueAsString(dlqMessage);
        
        // Assert - JSON should be valid and parseable
        JsonNode jsonNode = objectMapper.readTree(json);
        assertEquals(specialMessage, jsonNode.get("originalMessage").asText());
        assertEquals(specialError, jsonNode.get("errorMessage").asText());
        
        // Verify round-trip serialization
        DlqMessage deserialized = objectMapper.readValue(json, DlqMessage.class);
        assertEquals(specialMessage, deserialized.getOriginalMessage());
        assertEquals(specialError, deserialized.getErrorMessage());
    }
    
    @Test
    @DisplayName("DlqMessage should handle unicode characters correctly")
    void testUnicodeHandling() throws Exception {
        // Arrange
        String unicodeMessage = "Unicode: 测试 🎉 العربية עברית русский ñáéíóú";
        String unicodeError = "Error with unicode: 错误 😞";
        
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalMessageId("unicode-test")
            .originalMessage(unicodeMessage)
            .errorMessage(unicodeError)
            .errorType("UnicodeException")
            .containerId("unicode-container")
            .queueUrl("https://test-queue")
            .build();
        
        // Act
        String json = objectMapper.writeValueAsString(dlqMessage);
        DlqMessage deserialized = objectMapper.readValue(json, DlqMessage.class);
        
        // Assert
        assertEquals(unicodeMessage, deserialized.getOriginalMessage());
        assertEquals(unicodeError, deserialized.getErrorMessage());
    }
    
    @Test
    @DisplayName("DlqMessage should handle large content efficiently")
    void testLargeContentHandling() throws Exception {
        // Arrange
        String largeMessage = "x".repeat(50000); // 50KB message
        String largeError = "e".repeat(10000);   // 10KB error
        
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalMessageId("large-test")
            .originalMessage(largeMessage)
            .errorMessage(largeError)
            .errorType("LargeContentException")
            .containerId("large-container")
            .queueUrl("https://test-queue")
            .build();
        
        // Act
        String json = objectMapper.writeValueAsString(dlqMessage);
        DlqMessage deserialized = objectMapper.readValue(json, DlqMessage.class);
        
        // Assert
        assertEquals(largeMessage, deserialized.getOriginalMessage());
        assertEquals(largeError, deserialized.getErrorMessage());
        assertEquals(largeMessage.length(), deserialized.getOriginalMessage().length());
    }
    
    @Test
    @DisplayName("DlqMessage should handle empty attributes map")
    void testEmptyAttributesHandling() throws Exception {
        // Arrange
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalMessageId("test-id")
            .originalMessage("test message")
            .errorMessage("test error")
            .errorType("TestException")
            .containerId("test-container")
            .queueUrl("https://test-queue")
            .originalAttributes(Map.of()) // Empty map
            .build();
        
        // Act
        String json = objectMapper.writeValueAsString(dlqMessage);
        DlqMessage deserialized = objectMapper.readValue(json, DlqMessage.class);
        
        // Assert
        assertNotNull(deserialized.getOriginalAttributes());
        assertTrue(deserialized.getOriginalAttributes().isEmpty());
    }
    
    @Test
    @DisplayName("DlqMessage should auto-generate timestamp if not provided")
    void testAutoTimestampGeneration() throws Exception {
        // Arrange
        Instant beforeCreation = Instant.now();
        
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalMessageId("test-id")
            .originalMessage("test message")
            .errorMessage("test error")
            .errorType("TestException")
            .containerId("test-container")
            .queueUrl("https://test-queue")
            // No timestamp provided
            .build();
        
        Instant afterCreation = Instant.now();
        
        // Assert
        assertNotNull(dlqMessage.getTimestamp());
        assertTrue(dlqMessage.getTimestamp().isAfter(beforeCreation.minusSeconds(1)));
        assertTrue(dlqMessage.getTimestamp().isBefore(afterCreation.plusSeconds(1)));
    }
    
    @Test
    @DisplayName("DlqMessage should preserve provided timestamp")
    void testTimestampPreservation() throws Exception {
        // Arrange
        Instant specificTime = Instant.parse("2023-01-01T00:00:00Z");
        
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalMessageId("test-id")
            .originalMessage("test message")
            .errorMessage("test error")
            .errorType("TestException")
            .containerId("test-container")
            .queueUrl("https://test-queue")
            .timestamp(specificTime)
            .build();
        
        // Assert
        assertEquals(specificTime, dlqMessage.getTimestamp());
    }
    
    @Test
    @DisplayName("DlqMessage attributes should be immutable")
    void testAttributeImmutability() throws Exception {
        // Arrange
        Map<String, String> mutableAttributes = new java.util.HashMap<>();
        mutableAttributes.put("key1", "value1");
        
        DlqMessage dlqMessage = DlqMessage.builder()
            .originalMessageId("test-id")
            .originalMessage("test message")
            .errorMessage("test error")
            .errorType("TestException")
            .containerId("test-container")
            .queueUrl("https://test-queue")
            .originalAttributes(mutableAttributes)
            .build();
        
        // Act - Try to modify the original map
        mutableAttributes.put("key2", "value2");
        
        // Assert - DlqMessage's attributes should not be affected
        assertEquals(1, dlqMessage.getOriginalAttributes().size());
        assertTrue(dlqMessage.getOriginalAttributes().containsKey("key1"));
        assertFalse(dlqMessage.getOriginalAttributes().containsKey("key2"));
        
        // Assert - Returned map should be immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            dlqMessage.getOriginalAttributes().put("key3", "value3");
        });
    }
}