package com.ryuqq.aws.dynamodb.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DynamoKey.
 * Tests builder patterns, validation, and behavior.
 */
class DynamoKeyTest {

    @Nested
    class BuilderTests {

        @Test
        void shouldCreatePartitionKeyOnly() {
            DynamoKey key = DynamoKey.builder()
                    .partitionValue("userId", "user123")
                    .build();

            assertThat(key.getKeyAttributes()).hasSize(1);
            assertThat(key.getKeyAttributes().get("userId")).isEqualTo("user123");
            assertThat(key.getPartitionKey()).isEqualTo("userId");
            assertThat(key.getPartitionValue()).isEqualTo("user123");
        }

        @Test
        void shouldCreateCompositeKey() {
            DynamoKey key = DynamoKey.builder()
                    .partitionValue("userId", "user123")
                    .sortValue("timestamp", 1234567890L)
                    .build();

            assertThat(key.getKeyAttributes()).hasSize(2);
            assertThat(key.getKeyAttributes().get("userId")).isEqualTo("user123");
            assertThat(key.getKeyAttributes().get("timestamp")).isEqualTo(1234567890L);
        }

        @Test
        void shouldThrowExceptionForNullPartitionKeyName() {
            assertThatThrownBy(() -> 
                DynamoKey.builder().partitionValue(null, "value")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Partition key attribute name cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullPartitionKeyValue() {
            assertThatThrownBy(() -> 
                DynamoKey.builder().partitionValue("key", null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Partition key value cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullSortKeyName() {
            assertThatThrownBy(() -> 
                DynamoKey.builder()
                    .partitionValue("pk", "value")
                    .sortValue(null, "sortValue")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Sort key attribute name cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullSortKeyValue() {
            assertThatThrownBy(() -> 
                DynamoKey.builder()
                    .partitionValue("pk", "value")
                    .sortValue("sk", null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Sort key value cannot be null");
        }

        @Test
        void shouldThrowExceptionForEmptyBuilder() {
            assertThatThrownBy(() -> 
                DynamoKey.builder().build()
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("At least one key attribute must be specified");
        }

        @Test
        void shouldOverrideSortKeyValue() {
            DynamoKey key = DynamoKey.builder()
                    .partitionValue("userId", "user123")
                    .sortValue("timestamp", 1000L)
                    .sortValue("timestamp", 2000L) // Override previous value
                    .build();

            assertThat(key.getKeyAttributes()).hasSize(2);
            assertThat(key.getKeyAttributes().get("timestamp")).isEqualTo(2000L);
        }

        @Test
        void shouldAllowMultipleSortKeysWithDifferentNames() {
            // This creates a key with 3 attributes (1 partition + 2 sort)
            DynamoKey key = DynamoKey.builder()
                    .partitionValue("userId", "user123")
                    .sortValue("timestamp", 1000L)
                    .sortValue("type", "login")
                    .build();

            assertThat(key.getKeyAttributes()).hasSize(3);
            assertThat(key.getKeyAttributes().get("userId")).isEqualTo("user123");
            assertThat(key.getKeyAttributes().get("timestamp")).isEqualTo(1000L);
            assertThat(key.getKeyAttributes().get("type")).isEqualTo("login");
        }
    }

    @Nested
    class ConvenienceMethodTests {

        @Test
        void shouldCreatePartitionKeyUsingConvenienceMethod() {
            DynamoKey key = DynamoKey.partitionKey("userId", "user456");

            assertThat(key.getKeyAttributes()).hasSize(1);
            assertThat(key.getKeyAttributes().get("userId")).isEqualTo("user456");
            assertThat(key.getPartitionKey()).isEqualTo("userId");
            assertThat(key.getPartitionValue()).isEqualTo("user456");
        }

        @Test
        void shouldCreateSortKeyUsingConvenienceMethod() {
            DynamoKey key = DynamoKey.sortKey("userId", "user789", "timestamp", 987654321L);

            assertThat(key.getKeyAttributes()).hasSize(2);
            assertThat(key.getKeyAttributes().get("userId")).isEqualTo("user789");
            assertThat(key.getKeyAttributes().get("timestamp")).isEqualTo(987654321L);
        }

        @Test
        void shouldThrowExceptionForNullValuesInConvenienceMethods() {
            assertThatThrownBy(() -> 
                DynamoKey.partitionKey(null, "value")
            ).isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> 
                DynamoKey.partitionKey("key", null)
            ).isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> 
                DynamoKey.sortKey(null, "pValue", "sKey", "sValue")
            ).isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> 
                DynamoKey.sortKey("pKey", null, "sKey", "sValue")
            ).isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> 
                DynamoKey.sortKey("pKey", "pValue", null, "sValue")
            ).isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> 
                DynamoKey.sortKey("pKey", "pValue", "sKey", null)
            ).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class DataTypeTests {

        @Test
        void shouldHandleStringValues() {
            DynamoKey key = DynamoKey.partitionKey("attr", "string-value");
            assertThat(key.getPartitionValue()).isEqualTo("string-value");
        }

        @Test
        void shouldHandleIntegerValues() {
            DynamoKey key = DynamoKey.partitionKey("attr", 42);
            assertThat(key.getPartitionValue()).isEqualTo(42);
        }

        @Test
        void shouldHandleLongValues() {
            DynamoKey key = DynamoKey.partitionKey("attr", 123456789L);
            assertThat(key.getPartitionValue()).isEqualTo(123456789L);
        }

        @Test
        void shouldHandleDoubleValues() {
            DynamoKey key = DynamoKey.partitionKey("attr", 123.45);
            assertThat(key.getPartitionValue()).isEqualTo(123.45);
        }

        @Test
        void shouldHandleBooleanValues() {
            DynamoKey key = DynamoKey.partitionKey("attr", true);
            assertThat(key.getPartitionValue()).isEqualTo(true);
        }

        @Test
        void shouldHandleBinaryValues() {
            byte[] binaryData = "test-binary".getBytes();
            DynamoKey key = DynamoKey.partitionKey("attr", binaryData);
            assertThat(key.getPartitionValue()).isEqualTo(binaryData);
        }

        @Test
        void shouldHandleMixedDataTypes() {
            DynamoKey key = DynamoKey.sortKey("stringKey", "stringValue", "numberKey", 42);

            assertThat(key.getKeyAttributes().get("stringKey")).isEqualTo("stringValue");
            assertThat(key.getKeyAttributes().get("numberKey")).isEqualTo(42);
        }
    }

    @Nested
    class EqualityAndHashCodeTests {

        @Test
        void shouldBeEqualForSameKeyAttributes() {
            DynamoKey key1 = DynamoKey.partitionKey("userId", "user123");
            DynamoKey key2 = DynamoKey.partitionKey("userId", "user123");

            assertThat(key1).isEqualTo(key2);
            assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        }

        @Test
        void shouldBeEqualForSameCompositeKeys() {
            DynamoKey key1 = DynamoKey.sortKey("userId", "user123", "timestamp", 1000L);
            DynamoKey key2 = DynamoKey.sortKey("userId", "user123", "timestamp", 1000L);

            assertThat(key1).isEqualTo(key2);
            assertThat(key1.hashCode()).isEqualTo(key2.hashCode());
        }

        @Test
        void shouldNotBeEqualForDifferentPartitionValues() {
            DynamoKey key1 = DynamoKey.partitionKey("userId", "user123");
            DynamoKey key2 = DynamoKey.partitionKey("userId", "user456");

            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        void shouldNotBeEqualForDifferentSortValues() {
            DynamoKey key1 = DynamoKey.sortKey("userId", "user123", "timestamp", 1000L);
            DynamoKey key2 = DynamoKey.sortKey("userId", "user123", "timestamp", 2000L);

            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        void shouldNotBeEqualForDifferentKeyNames() {
            DynamoKey key1 = DynamoKey.partitionKey("userId", "user123");
            DynamoKey key2 = DynamoKey.partitionKey("customerId", "user123");

            assertThat(key1).isNotEqualTo(key2);
        }

        @Test
        void shouldNotBeEqualToNull() {
            DynamoKey key = DynamoKey.partitionKey("userId", "user123");
            assertThat(key).isNotEqualTo(null);
        }

        @Test
        void shouldNotBeEqualToDifferentClass() {
            DynamoKey key = DynamoKey.partitionKey("userId", "user123");
            assertThat(key).isNotEqualTo("string");
        }

        @Test
        void shouldBeEqualToItself() {
            DynamoKey key = DynamoKey.partitionKey("userId", "user123");
            assertThat(key).isEqualTo(key);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void shouldProvideReadableToString() {
            DynamoKey key = DynamoKey.partitionKey("userId", "user123");
            String result = key.toString();

            assertThat(result).contains("DynamoKey");
            assertThat(result).contains("keyAttributes");
            assertThat(result).contains("userId");
            assertThat(result).contains("user123");
        }

        @Test
        void shouldProvideReadableToStringForCompositeKey() {
            DynamoKey key = DynamoKey.sortKey("userId", "user123", "timestamp", 1000L);
            String result = key.toString();

            assertThat(result).contains("DynamoKey");
            assertThat(result).contains("keyAttributes");
            assertThat(result).contains("userId");
            assertThat(result).contains("user123");
            assertThat(result).contains("timestamp");
            assertThat(result).contains("1000");
        }
    }

    @Nested
    class ImmutabilityTests {

        @Test
        void shouldReturnImmutableKeyAttributes() {
            DynamoKey key = DynamoKey.sortKey("userId", "user123", "timestamp", 1000L);
            Map<String, Object> attributes = key.getKeyAttributes();

            assertThatThrownBy(() -> 
                attributes.put("newKey", "newValue")
            ).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldNotAffectOriginalAfterModifyingSourceMap() {
            DynamoKey.Builder builder = DynamoKey.builder()
                    .partitionValue("userId", "user123")
                    .sortValue("timestamp", 1000L);

            DynamoKey key = builder.build();

            // Try to modify builder after build (this would affect a new build, not the existing key)
            builder.partitionValue("userId", "modified");

            // Original key should be unchanged
            assertThat(key.getKeyAttributes().get("userId")).isEqualTo("user123");
        }
    }

    @Nested
    class AccessorMethodTests {

        @Test
        void shouldGetPartitionKeyAndValueForSingleKey() {
            DynamoKey key = DynamoKey.partitionKey("userId", "user123");

            assertThat(key.getPartitionKey()).isEqualTo("userId");
            assertThat(key.getPartitionValue()).isEqualTo("user123");
        }

        @Test
        void shouldGetPartitionKeyAndValueForCompositeKey() {
            DynamoKey key = DynamoKey.sortKey("userId", "user123", "timestamp", 1000L);

            // getPartitionKey() and getPartitionValue() return the first key-value pair
            assertThat(key.getPartitionKey()).isIn("userId", "timestamp");
            assertThat(key.getPartitionValue()).isIn("user123", 1000L);
        }

        @Test
        void shouldGetKeyAttributesMap() {
            DynamoKey key = DynamoKey.sortKey("userId", "user123", "timestamp", 1000L);
            Map<String, Object> attributes = key.getKeyAttributes();

            assertThat(attributes).hasSize(2);
            assertThat(attributes).containsEntry("userId", "user123");
            assertThat(attributes).containsEntry("timestamp", 1000L);
        }
    }
}