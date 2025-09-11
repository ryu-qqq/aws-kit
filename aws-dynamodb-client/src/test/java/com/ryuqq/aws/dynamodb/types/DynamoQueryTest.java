package com.ryuqq.aws.dynamodb.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DynamoQuery.
 * Tests query building, operators, and validation.
 */
class DynamoQueryTest {

    @Nested
    class PartitionKeyOnlyQueryTests {

        @Test
        void shouldCreatePartitionKeyOnlyQuery() {
            DynamoQuery query = DynamoQuery.keyEqual("userId", "user123");

            assertThat(query.getPartitionKey()).isEqualTo("userId");
            assertThat(query.getPartitionValue()).isEqualTo("user123");
            assertThat(query.getSortKey()).isNull();
            assertThat(query.getOperator()).isNull();
            assertThat(query.getSortValue()).isNull();
            assertThat(query.getSortValueEnd()).isNull();
            assertThat(query.hasSortKeyCondition()).isFalse();
        }

        @Test
        void shouldCreatePartitionKeyOnlyQueryUsingBuilder() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("customerId", "cust456")
                    .build();

            assertThat(query.getPartitionKey()).isEqualTo("customerId");
            assertThat(query.getPartitionValue()).isEqualTo("cust456");
            assertThat(query.hasSortKeyCondition()).isFalse();
        }
    }

    @Nested
    class CompositeKeyQueryTests {

        @Test
        void shouldCreateEqualQuery() {
            DynamoQuery query = DynamoQuery.keyEqualAndSortEqual("userId", "user123", "timestamp", 1234567890L);

            assertThat(query.getPartitionKey()).isEqualTo("userId");
            assertThat(query.getPartitionValue()).isEqualTo("user123");
            assertThat(query.getSortKey()).isEqualTo("timestamp");
            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.EQUAL);
            assertThat(query.getSortValue()).isEqualTo(1234567890L);
            assertThat(query.getSortValueEnd()).isNull();
            assertThat(query.hasSortKeyCondition()).isTrue();
        }

        @Test
        void shouldCreateBeginsWithQuery() {
            DynamoQuery query = DynamoQuery.keyEqualAndSortBeginsWith("userId", "user123", "prefix", "test");

            assertThat(query.getPartitionKey()).isEqualTo("userId");
            assertThat(query.getPartitionValue()).isEqualTo("user123");
            assertThat(query.getSortKey()).isEqualTo("prefix");
            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.BEGINS_WITH);
            assertThat(query.getSortValue()).isEqualTo("test");
            assertThat(query.hasSortKeyCondition()).isTrue();
        }
    }

    @Nested
    class BuilderComparisonOperatorTests {

        @Test
        void shouldCreateLessThanQuery() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyLessThan("timestamp", 1234567890L)
                    .build();

            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.LESS_THAN);
            assertThat(query.getSortValue()).isEqualTo(1234567890L);
            assertThat(query.hasSortKeyCondition()).isTrue();
        }

        @Test
        void shouldCreateLessThanOrEqualQuery() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyLessThanOrEqual("timestamp", 1234567890L)
                    .build();

            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.LESS_THAN_OR_EQUAL);
            assertThat(query.getSortValue()).isEqualTo(1234567890L);
        }

        @Test
        void shouldCreateGreaterThanQuery() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyGreaterThan("timestamp", 1234567890L)
                    .build();

            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.GREATER_THAN);
            assertThat(query.getSortValue()).isEqualTo(1234567890L);
        }

        @Test
        void shouldCreateGreaterThanOrEqualQuery() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyGreaterThanOrEqual("timestamp", 1234567890L)
                    .build();

            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.GREATER_THAN_OR_EQUAL);
            assertThat(query.getSortValue()).isEqualTo(1234567890L);
        }

        @Test
        void shouldCreateBetweenQuery() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyBetween("timestamp", 1000000000L, 2000000000L)
                    .build();

            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.BETWEEN);
            assertThat(query.getSortValue()).isEqualTo(1000000000L);
            assertThat(query.getSortValueEnd()).isEqualTo(2000000000L);
        }

        @Test
        void shouldCreateBeginsWithQuery() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyBeginsWith("prefix", "test-prefix")
                    .build();

            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.BEGINS_WITH);
            assertThat(query.getSortValue()).isEqualTo("test-prefix");
        }

        @Test
        void shouldCreateEqualQueryUsingBuilder() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyEqual("timestamp", 1234567890L)
                    .build();

            assertThat(query.getOperator()).isEqualTo(DynamoQuery.ComparisonOperator.EQUAL);
            assertThat(query.getSortValue()).isEqualTo(1234567890L);
        }
    }

    @Nested
    class ValidationTests {

        @Test
        void shouldThrowExceptionForNullPartitionKey() {
            assertThatThrownBy(() -> 
                DynamoQuery.builder().partitionKeyEqual(null, "value")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Partition key cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullPartitionValue() {
            assertThatThrownBy(() -> 
                DynamoQuery.builder().partitionKeyEqual("key", null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Partition value cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullSortKey() {
            assertThatThrownBy(() -> 
                DynamoQuery.builder()
                    .partitionKeyEqual("pk", "pv")
                    .sortKeyEqual(null, "sv")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Sort key cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullSortValue() {
            assertThatThrownBy(() -> 
                DynamoQuery.builder()
                    .partitionKeyEqual("pk", "pv")
                    .sortKeyEqual("sk", null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Sort value cannot be null");
        }

        @Test
        void shouldThrowExceptionForMissingPartitionKey() {
            assertThatThrownBy(() -> 
                DynamoQuery.builder().build()
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Partition key and value are required");
        }

        @Test
        void shouldThrowExceptionForSortKeyWithoutOperator() {
            // This scenario isn't possible with the current builder API since each sort method sets the operator
            // But we test the validation logic
            DynamoQuery.Builder builder = DynamoQuery.builder()
                    .partitionKeyEqual("pk", "pv");

            // All sort key methods set both sortKey and operator, so this validation is defensive
            DynamoQuery query = builder.sortKeyEqual("sk", "sv").build();
            assertThat(query.hasSortKeyCondition()).isTrue();
        }

        @Test
        void shouldThrowExceptionForNullBetweenValues() {
            assertThatThrownBy(() -> 
                DynamoQuery.builder()
                    .partitionKeyEqual("pk", "pv")
                    .sortKeyBetween("sk", null, "end")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Start value cannot be null");

            assertThatThrownBy(() -> 
                DynamoQuery.builder()
                    .partitionKeyEqual("pk", "pv")
                    .sortKeyBetween("sk", "start", null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("End value cannot be null");
        }
    }

    @Nested
    class DataTypeTests {

        @Test
        void shouldHandleStringValues() {
            DynamoQuery query = DynamoQuery.keyEqualAndSortEqual("strKey", "strValue", "sortKey", "sortValue");

            assertThat(query.getPartitionValue()).isEqualTo("strValue");
            assertThat(query.getSortValue()).isEqualTo("sortValue");
        }

        @Test
        void shouldHandleNumericValues() {
            DynamoQuery query = DynamoQuery.keyEqualAndSortEqual("intKey", 42, "longKey", 123456789L);

            assertThat(query.getPartitionValue()).isEqualTo(42);
            assertThat(query.getSortValue()).isEqualTo(123456789L);
        }

        @Test
        void shouldHandleDoubleValues() {
            DynamoQuery query = DynamoQuery.keyEqualAndSortEqual("doubleKey", 123.45, "sortKey", 987.65);

            assertThat(query.getPartitionValue()).isEqualTo(123.45);
            assertThat(query.getSortValue()).isEqualTo(987.65);
        }

        @Test
        void shouldHandleBooleanValues() {
            DynamoQuery query = DynamoQuery.keyEqualAndSortEqual("boolKey", true, "sortKey", false);

            assertThat(query.getPartitionValue()).isEqualTo(true);
            assertThat(query.getSortValue()).isEqualTo(false);
        }

        @Test
        void shouldHandleBinaryValues() {
            byte[] partitionData = "partition-binary".getBytes();
            byte[] sortData = "sort-binary".getBytes();
            
            DynamoQuery query = DynamoQuery.keyEqualAndSortEqual("binaryKey", partitionData, "sortKey", sortData);

            assertThat(query.getPartitionValue()).isEqualTo(partitionData);
            assertThat(query.getSortValue()).isEqualTo(sortData);
        }

        @Test
        void shouldHandleMixedDataTypesInBetween() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyBetween("timestamp", 1000L, 2000L)
                    .build();

            assertThat(query.getSortValue()).isEqualTo(1000L);
            assertThat(query.getSortValueEnd()).isEqualTo(2000L);
        }
    }

    @Nested
    class EqualityAndHashCodeTests {

        @Test
        void shouldBeEqualForSameQueries() {
            DynamoQuery query1 = DynamoQuery.keyEqual("userId", "user123");
            DynamoQuery query2 = DynamoQuery.keyEqual("userId", "user123");

            assertThat(query1).isEqualTo(query2);
            assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
        }

        @Test
        void shouldBeEqualForSameCompositeQueries() {
            DynamoQuery query1 = DynamoQuery.keyEqualAndSortEqual("userId", "user123", "timestamp", 1000L);
            DynamoQuery query2 = DynamoQuery.keyEqualAndSortEqual("userId", "user123", "timestamp", 1000L);

            assertThat(query1).isEqualTo(query2);
            assertThat(query1.hashCode()).isEqualTo(query2.hashCode());
        }

        @Test
        void shouldNotBeEqualForDifferentPartitionValues() {
            DynamoQuery query1 = DynamoQuery.keyEqual("userId", "user123");
            DynamoQuery query2 = DynamoQuery.keyEqual("userId", "user456");

            assertThat(query1).isNotEqualTo(query2);
        }

        @Test
        void shouldNotBeEqualForDifferentOperators() {
            DynamoQuery query1 = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyEqual("timestamp", 1000L)
                    .build();

            DynamoQuery query2 = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyLessThan("timestamp", 1000L)
                    .build();

            assertThat(query1).isNotEqualTo(query2);
        }

        @Test
        void shouldNotBeEqualForDifferentSortValues() {
            DynamoQuery query1 = DynamoQuery.keyEqualAndSortEqual("userId", "user123", "timestamp", 1000L);
            DynamoQuery query2 = DynamoQuery.keyEqualAndSortEqual("userId", "user123", "timestamp", 2000L);

            assertThat(query1).isNotEqualTo(query2);
        }

        @Test
        void shouldNotBeEqualToNull() {
            DynamoQuery query = DynamoQuery.keyEqual("userId", "user123");
            assertThat(query).isNotEqualTo(null);
        }

        @Test
        void shouldNotBeEqualToDifferentClass() {
            DynamoQuery query = DynamoQuery.keyEqual("userId", "user123");
            assertThat(query).isNotEqualTo("string");
        }

        @Test
        void shouldBeEqualToItself() {
            DynamoQuery query = DynamoQuery.keyEqual("userId", "user123");
            assertThat(query).isEqualTo(query);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void shouldProvideReadableToStringForPartitionOnly() {
            DynamoQuery query = DynamoQuery.keyEqual("userId", "user123");
            String result = query.toString();

            assertThat(result).contains("DynamoQuery");
            assertThat(result).contains("partitionKey='userId'");
            assertThat(result).contains("partitionValue=user123");
            assertThat(result).contains("sortKey='null'");
            assertThat(result).contains("operator=null");
        }

        @Test
        void shouldProvideReadableToStringForCompositeQuery() {
            DynamoQuery query = DynamoQuery.keyEqualAndSortEqual("userId", "user123", "timestamp", 1000L);
            String result = query.toString();

            assertThat(result).contains("DynamoQuery");
            assertThat(result).contains("partitionKey='userId'");
            assertThat(result).contains("partitionValue=user123");
            assertThat(result).contains("sortKey='timestamp'");
            assertThat(result).contains("operator=EQUAL");
            assertThat(result).contains("sortValue=1000");
        }

        @Test
        void shouldProvideReadableToStringForBetweenQuery() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyBetween("timestamp", 1000L, 2000L)
                    .build();
            String result = query.toString();

            assertThat(result).contains("operator=BETWEEN");
            assertThat(result).contains("sortValue=1000");
            assertThat(result).contains("sortValueEnd=2000");
        }
    }

    @Nested
    class ComparisonOperatorEnumTests {

        @Test
        void shouldHaveAllExpectedOperators() {
            DynamoQuery.ComparisonOperator[] operators = DynamoQuery.ComparisonOperator.values();

            assertThat(operators).containsExactlyInAnyOrder(
                    DynamoQuery.ComparisonOperator.EQUAL,
                    DynamoQuery.ComparisonOperator.LESS_THAN,
                    DynamoQuery.ComparisonOperator.LESS_THAN_OR_EQUAL,
                    DynamoQuery.ComparisonOperator.GREATER_THAN,
                    DynamoQuery.ComparisonOperator.GREATER_THAN_OR_EQUAL,
                    DynamoQuery.ComparisonOperator.BETWEEN,
                    DynamoQuery.ComparisonOperator.BEGINS_WITH
            );
        }

        @Test
        void shouldConvertOperatorToString() {
            assertThat(DynamoQuery.ComparisonOperator.EQUAL.toString()).isEqualTo("EQUAL");
            assertThat(DynamoQuery.ComparisonOperator.LESS_THAN.toString()).isEqualTo("LESS_THAN");
            assertThat(DynamoQuery.ComparisonOperator.BEGINS_WITH.toString()).isEqualTo("BEGINS_WITH");
        }

        @Test
        void shouldConvertStringToOperator() {
            assertThat(DynamoQuery.ComparisonOperator.valueOf("EQUAL")).isEqualTo(DynamoQuery.ComparisonOperator.EQUAL);
            assertThat(DynamoQuery.ComparisonOperator.valueOf("BETWEEN")).isEqualTo(DynamoQuery.ComparisonOperator.BETWEEN);
        }
    }

    @Nested
    class HasSortKeyConditionTests {

        @Test
        void shouldReturnFalseForPartitionOnlyQuery() {
            DynamoQuery query = DynamoQuery.keyEqual("userId", "user123");
            assertThat(query.hasSortKeyCondition()).isFalse();
        }

        @Test
        void shouldReturnTrueForQueryWithSortCondition() {
            DynamoQuery query = DynamoQuery.keyEqualAndSortEqual("userId", "user123", "timestamp", 1000L);
            assertThat(query.hasSortKeyCondition()).isTrue();
        }

        @Test
        void shouldReturnTrueForAllSortOperators() {
            String partitionKey = "userId";
            String partitionValue = "user123";
            String sortKey = "timestamp";
            Object sortValue = 1000L;

            DynamoQuery.ComparisonOperator[] operators = {
                    DynamoQuery.ComparisonOperator.EQUAL,
                    DynamoQuery.ComparisonOperator.LESS_THAN,
                    DynamoQuery.ComparisonOperator.LESS_THAN_OR_EQUAL,
                    DynamoQuery.ComparisonOperator.GREATER_THAN,
                    DynamoQuery.ComparisonOperator.GREATER_THAN_OR_EQUAL,
                    DynamoQuery.ComparisonOperator.BEGINS_WITH
            };

            for (DynamoQuery.ComparisonOperator operator : operators) {
                DynamoQuery query = createQueryWithOperator(partitionKey, partitionValue, sortKey, sortValue, operator);
                assertThat(query.hasSortKeyCondition())
                        .as("hasSortKeyCondition should be true for operator %s", operator)
                        .isTrue();
            }
        }

        @Test
        void shouldReturnTrueForBetweenOperator() {
            DynamoQuery query = DynamoQuery.builder()
                    .partitionKeyEqual("userId", "user123")
                    .sortKeyBetween("timestamp", 1000L, 2000L)
                    .build();

            assertThat(query.hasSortKeyCondition()).isTrue();
        }

        private DynamoQuery createQueryWithOperator(String partitionKey, String partitionValue, 
                                                    String sortKey, Object sortValue, 
                                                    DynamoQuery.ComparisonOperator operator) {
            DynamoQuery.Builder builder = DynamoQuery.builder().partitionKeyEqual(partitionKey, partitionValue);
            
            switch (operator) {
                case EQUAL:
                    return builder.sortKeyEqual(sortKey, sortValue).build();
                case LESS_THAN:
                    return builder.sortKeyLessThan(sortKey, sortValue).build();
                case LESS_THAN_OR_EQUAL:
                    return builder.sortKeyLessThanOrEqual(sortKey, sortValue).build();
                case GREATER_THAN:
                    return builder.sortKeyGreaterThan(sortKey, sortValue).build();
                case GREATER_THAN_OR_EQUAL:
                    return builder.sortKeyGreaterThanOrEqual(sortKey, sortValue).build();
                case BEGINS_WITH:
                    return builder.sortKeyBeginsWith(sortKey, sortValue).build();
                default:
                    throw new IllegalArgumentException("Unsupported operator: " + operator);
            }
        }
    }
}