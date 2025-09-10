package com.ryuqq.aws.sqs.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for message serialization functionality.
 * Tests JSON serialization/deserialization and error handling.
 */
@ExtendWith(MockitoExtension.class)
class MessageSerializationTest {

    @Mock
    private ObjectMapper objectMapper;
    
    private JacksonMessageSerializer serializer;
    private JacksonMessageSerializer realSerializer;
    
    @BeforeEach
    void setUp() {
        serializer = new JacksonMessageSerializer(objectMapper);
        realSerializer = new JacksonMessageSerializer(); // For real serialization tests
    }

    @Nested
    @DisplayName("Successful Serialization")
    class SuccessfulSerialization {
        
        @Test
        @DisplayName("Should serialize simple string")
        void shouldSerializeSimpleString() throws JsonProcessingException {
            // Given
            String input = "Hello World";
            String expected = "\"Hello World\"";
            when(objectMapper.writeValueAsString(input)).thenReturn(expected);
            
            // When
            String result = serializer.serialize(input);
            
            // Then
            assertThat(result).isEqualTo(expected);
        }
        
        @Test
        @DisplayName("Should serialize simple object")
        void shouldSerializeSimpleObject() throws JsonProcessingException {
            // Given
            TestDto dto = new TestDto("test", 123);
            String expected = "{\"name\":\"test\",\"value\":123}";
            when(objectMapper.writeValueAsString(dto)).thenReturn(expected);
            
            // When
            String result = serializer.serialize(dto);
            
            // Then
            assertThat(result).isEqualTo(expected);
        }
        
        @Test
        @DisplayName("Should serialize null object")
        void shouldSerializeNullObject() throws JsonProcessingException {
            // Given
            when(objectMapper.writeValueAsString(null)).thenReturn("null");
            
            // When
            String result = serializer.serialize(null);
            
            // Then
            assertThat(result).isEqualTo("null");
        }
        
        @Test
        @DisplayName("Should serialize collection")
        void shouldSerializeCollection() throws JsonProcessingException {
            // Given
            List<String> list = List.of("item1", "item2", "item3");
            String expected = "[\"item1\",\"item2\",\"item3\"]";
            when(objectMapper.writeValueAsString(list)).thenReturn(expected);
            
            // When
            String result = serializer.serialize(list);
            
            // Then
            assertThat(result).isEqualTo(expected);
        }
        
        @Test
        @DisplayName("Should serialize map")
        void shouldSerializeMap() throws JsonProcessingException {
            // Given
            Map<String, Object> map = Map.of("key1", "value1", "key2", 42);
            String expected = "{\"key1\":\"value1\",\"key2\":42}";
            when(objectMapper.writeValueAsString(map)).thenReturn(expected);
            
            // When
            String result = serializer.serialize(map);
            
            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Serialization Error Handling")
    class SerializationErrorHandling {
        
        @Test
        @DisplayName("Should throw MessageSerializationException on Jackson error")
        void shouldThrowExceptionOnJacksonError() throws JsonProcessingException {
            // Given
            TestDto dto = new TestDto("test", 123);
            when(objectMapper.writeValueAsString(any()))
                    .thenThrow(new JsonProcessingException("Serialization failed") {});
            
            // When & Then
            assertThatThrownBy(() -> serializer.serialize(dto))
                    .isInstanceOf(MessageSerializationException.class)
                    .hasMessage("Failed to serialize object")
                    .hasCauseInstanceOf(JsonProcessingException.class);
        }
        
        @Test
        @DisplayName("Should provide detailed error message")
        void shouldProvideDetailedErrorMessage() throws JsonProcessingException {
            // Given
            TestDto dto = new TestDto("test", 123);
            JsonProcessingException jacksonException = new JsonProcessingException("Specific serialization error") {};
            when(objectMapper.writeValueAsString(any())).thenThrow(jacksonException);
            
            // When & Then
            assertThatThrownBy(() -> serializer.serialize(dto))
                    .isInstanceOf(MessageSerializationException.class)
                    .hasMessage("Failed to serialize object")
                    .hasCause(jacksonException);
        }
    }

    @Nested
    @DisplayName("Successful Deserialization")
    class SuccessfulDeserialization {
        
        @Test
        @DisplayName("Should deserialize to simple object")
        void shouldDeserializeToSimpleObject() throws JsonProcessingException {
            // Given
            String json = "{\"name\":\"test\",\"value\":123}";
            TestDto expected = new TestDto("test", 123);
            when(objectMapper.readValue(json, TestDto.class)).thenReturn(expected);
            
            // When
            TestDto result = serializer.deserialize(json, TestDto.class);
            
            // Then
            assertThat(result).isEqualTo(expected);
        }
        
        @Test
        @DisplayName("Should deserialize to string")
        void shouldDeserializeToString() throws JsonProcessingException {
            // Given
            String json = "\"Hello World\"";
            String expected = "Hello World";
            when(objectMapper.readValue(json, String.class)).thenReturn(expected);
            
            // When
            String result = serializer.deserialize(json, String.class);
            
            // Then
            assertThat(result).isEqualTo(expected);
        }
        
        @Test
        @DisplayName("Should deserialize to collection")
        void shouldDeserializeToCollection() throws JsonProcessingException {
            // Given
            String json = "[\"item1\",\"item2\",\"item3\"]";
            @SuppressWarnings("unchecked")
            List<String> expected = List.of("item1", "item2", "item3");
            when(objectMapper.readValue(json, List.class)).thenReturn(expected);
            
            // When
            @SuppressWarnings("unchecked")
            List<String> result = serializer.deserialize(json, List.class);
            
            // Then
            assertThat(result).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Deserialization Error Handling")
    class DeserializationErrorHandling {
        
        @Test
        @DisplayName("Should throw MessageSerializationException on Jackson error")
        void shouldThrowExceptionOnJacksonDeserializationError() throws JsonProcessingException {
            // Given
            String invalidJson = "{invalid json}";
            when(objectMapper.readValue(invalidJson, TestDto.class))
                    .thenThrow(new JsonProcessingException("Deserialization failed") {});
            
            // When & Then
            assertThatThrownBy(() -> serializer.deserialize(invalidJson, TestDto.class))
                    .isInstanceOf(MessageSerializationException.class)
                    .hasMessage("Failed to deserialize object")
                    .hasCauseInstanceOf(JsonProcessingException.class);
        }
        
        @Test
        @DisplayName("Should handle null JSON input")
        void shouldHandleNullJsonInput() throws JsonProcessingException {
            // Given
            when(objectMapper.readValue((String) null, TestDto.class))
                    .thenThrow(new JsonProcessingException("Cannot parse null") {});
            
            // When & Then
            assertThatThrownBy(() -> serializer.deserialize(null, TestDto.class))
                    .isInstanceOf(MessageSerializationException.class);
        }
        
        @Test
        @DisplayName("Should handle empty JSON input")
        void shouldHandleEmptyJsonInput() throws JsonProcessingException {
            // Given
            String emptyJson = "";
            when(objectMapper.readValue(emptyJson, TestDto.class))
                    .thenThrow(new JsonProcessingException("Cannot parse empty string") {});
            
            // When & Then
            assertThatThrownBy(() -> serializer.deserialize(emptyJson, TestDto.class))
                    .isInstanceOf(MessageSerializationException.class);
        }
    }

    @Nested
    @DisplayName("Real Serialization Tests")
    class RealSerializationTests {
        
        @Test
        @DisplayName("Should serialize and deserialize complex object")
        void shouldSerializeAndDeserializeComplexObject() {
            // Given
            ComplexDto original = new ComplexDto(
                    "test-id",
                    "Test Name",
                    BigDecimal.valueOf(99.99),
                    LocalDateTime.of(2024, 1, 1, 12, 0),
                    List.of("tag1", "tag2"),
                    Map.of("key1", "value1", "key2", "value2")
            );
            
            // When
            String json = realSerializer.serialize(original);
            ComplexDto deserialized = realSerializer.deserialize(json, ComplexDto.class);
            
            // Then
            assertThat(json).isNotNull();
            assertThat(json).contains("\"id\":\"test-id\"");
            assertThat(json).contains("\"name\":\"Test Name\"");
            assertThat(deserialized).isNotNull();
            assertThat(deserialized.getId()).isEqualTo(original.getId());
            assertThat(deserialized.getName()).isEqualTo(original.getName());
            assertThat(deserialized.getPrice()).isEqualTo(original.getPrice());
            assertThat(deserialized.getTags()).isEqualTo(original.getTags());
            assertThat(deserialized.getMetadata()).isEqualTo(original.getMetadata());
        }
        
        @Test
        @DisplayName("Should serialize primitive types correctly")
        void shouldSerializePrimitiveTypesCorrectly() {
            // Given & When & Then
            assertThat(realSerializer.serialize("string")).isEqualTo("\"string\"");
            assertThat(realSerializer.serialize(123)).isEqualTo("123");
            assertThat(realSerializer.serialize(true)).isEqualTo("true");
            assertThat(realSerializer.serialize(null)).isEqualTo("null");
        }
        
        @Test
        @DisplayName("Should deserialize primitive types correctly")
        void shouldDeserializePrimitiveTypesCorrectly() {
            // Given & When & Then
            assertThat(realSerializer.deserialize("\"string\"", String.class)).isEqualTo("string");
            assertThat(realSerializer.deserialize("123", Integer.class)).isEqualTo(123);
            assertThat(realSerializer.deserialize("true", Boolean.class)).isEqualTo(true);
        }
        
        @Test
        @DisplayName("Should handle special characters in strings")
        void shouldHandleSpecialCharactersInStrings() {
            // Given
            String specialString = "Special chars: \"quotes\", \\backslash, \n newline, \t tab";
            
            // When
            String json = realSerializer.serialize(specialString);
            String deserialized = realSerializer.deserialize(json, String.class);
            
            // Then
            assertThat(deserialized).isEqualTo(specialString);
        }
        
        @Test
        @DisplayName("Should handle unicode characters")
        void shouldHandleUnicodeCharacters() {
            // Given
            String unicodeString = "Unicode: 你好, 🚀, العربية";
            
            // When
            String json = realSerializer.serialize(unicodeString);
            String deserialized = realSerializer.deserialize(json, String.class);
            
            // Then
            assertThat(deserialized).isEqualTo(unicodeString);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Should handle circular reference protection")
        void shouldHandleCircularReferenceProtection() {
            // Given - CircularDto has a reference to itself
            CircularDto dto = new CircularDto("test");
            dto.setSelf(dto); // Create circular reference
            
            // When & Then - Should not throw StackOverflowError
            assertThatThrownBy(() -> realSerializer.serialize(dto))
                    .isInstanceOf(MessageSerializationException.class);
        }
        
        @Test
        @DisplayName("Should handle very large objects")
        void shouldHandleVeryLargeObjects() {
            // Given
            LargeDto largeDto = new LargeDto();
            for (int i = 0; i < 1000; i++) {
                largeDto.addItem("item-" + i);
            }
            
            // When
            String json = realSerializer.serialize(largeDto);
            LargeDto deserialized = realSerializer.deserialize(json, LargeDto.class);
            
            // Then
            assertThat(deserialized.getItems()).hasSize(1000);
            assertThat(deserialized.getItems()).containsExactlyElementsOf(largeDto.getItems());
        }
        
        @Test
        @DisplayName("Should handle nested objects")
        void shouldHandleNestedObjects() {
            // Given
            NestedDto nested = new NestedDto(
                    "parent",
                    new NestedDto.Child("child1", new NestedDto.Child.GrandChild("grandchild1"))
            );
            
            // When
            String json = realSerializer.serialize(nested);
            NestedDto deserialized = realSerializer.deserialize(json, NestedDto.class);
            
            // Then
            assertThat(deserialized.getName()).isEqualTo("parent");
            assertThat(deserialized.getChild().getName()).isEqualTo("child1");
            assertThat(deserialized.getChild().getGrandChild().getName()).isEqualTo("grandchild1");
        }
    }

    // Test DTOs
    static class TestDto {
        private String name;
        private int value;
        
        public TestDto() {}
        
        public TestDto(String name, int value) {
            this.name = name;
            this.value = value;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TestDto)) return false;
            TestDto testDto = (TestDto) o;
            return value == testDto.value && java.util.Objects.equals(name, testDto.name);
        }
        
        @Override
        public int hashCode() {
            return java.util.Objects.hash(name, value);
        }
    }
    
    static class ComplexDto {
        private String id;
        private String name;
        private BigDecimal price;
        private LocalDateTime timestamp;
        private List<String> tags;
        private Map<String, String> metadata;
        
        public ComplexDto() {}
        
        public ComplexDto(String id, String name, BigDecimal price, LocalDateTime timestamp, 
                         List<String> tags, Map<String, String> metadata) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.timestamp = timestamp;
            this.tags = tags;
            this.metadata = metadata;
        }
        
        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }
    
    static class CircularDto {
        private String name;
        private CircularDto self;
        
        public CircularDto() {}
        
        public CircularDto(String name) {
            this.name = name;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public CircularDto getSelf() { return self; }
        public void setSelf(CircularDto self) { this.self = self; }
    }
    
    static class LargeDto {
        private List<String> items = new java.util.ArrayList<>();
        
        public void addItem(String item) {
            items.add(item);
        }
        
        public List<String> getItems() { return items; }
        public void setItems(List<String> items) { this.items = items; }
    }
    
    static class NestedDto {
        private String name;
        private Child child;
        
        public NestedDto() {}
        
        public NestedDto(String name, Child child) {
            this.name = name;
            this.child = child;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Child getChild() { return child; }
        public void setChild(Child child) { this.child = child; }
        
        static class Child {
            private String name;
            private GrandChild grandChild;
            
            public Child() {}
            
            public Child(String name, GrandChild grandChild) {
                this.name = name;
                this.grandChild = grandChild;
            }
            
            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public GrandChild getGrandChild() { return grandChild; }
            public void setGrandChild(GrandChild grandChild) { this.grandChild = grandChild; }
            
            static class GrandChild {
                private String name;
                
                public GrandChild() {}
                
                public GrandChild(String name) {
                    this.name = name;
                }
                
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
            }
        }
    }
}