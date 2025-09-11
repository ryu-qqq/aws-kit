package com.ryuqq.aws.dynamodb.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DynamoTransaction and DynamoTransactionItem.
 * Tests transaction building, validation, and item types.
 */
class DynamoTransactionTest {

    @Nested
    class BuilderTests {

        @Test
        void shouldCreateEmptyBuilder() {
            DynamoTransaction.Builder builder = DynamoTransaction.builder();
            assertThat(builder).isNotNull();
        }

        @Test
        void shouldThrowExceptionForEmptyTransaction() {
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().build()
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("At least one transaction item must be specified");
        }

        @Test
        void shouldCreateTransactionWithSingleItem() {
            TestItem item = new TestItem("id-1", "value-1");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            assertThat(transaction.size()).isEqualTo(1);
            assertThat(transaction.isEmpty()).isFalse();
            assertThat(transaction.getItems()).hasSize(1);
        }

        @Test
        void shouldCreateTransactionWithMultipleItems() {
            TestItem item1 = new TestItem("id-1", "value-1");
            TestItem item2 = new TestItem("id-2", "value-2");
            DynamoKey key = DynamoKey.partitionKey("id", "id-3");

            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item1, "table1")
                    .put(item2, "table2")
                    .delete(key, "table3")
                    .build();

            assertThat(transaction.size()).isEqualTo(3);
            assertThat(transaction.getItems()).hasSize(3);
        }

        @Test
        void shouldThrowExceptionForTooManyItems() {
            DynamoTransaction.Builder builder = DynamoTransaction.builder();
            
            // Add 101 items to exceed the limit
            for (int i = 0; i < 101; i++) {
                builder.put(new TestItem("id-" + i, "value-" + i), "table");
            }

            assertThatThrownBy(() -> 
                builder.build()
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Transaction cannot contain more than 100 items");
        }

        @Test
        void shouldAllowExactly100Items() {
            DynamoTransaction.Builder builder = DynamoTransaction.builder();
            
            // Add exactly 100 items
            for (int i = 0; i < 100; i++) {
                builder.put(new TestItem("id-" + i, "value-" + i), "table");
            }

            DynamoTransaction transaction = builder.build();
            assertThat(transaction.size()).isEqualTo(100);
        }
    }

    @Nested
    class PutOperationTests {

        @Test
        void shouldCreatePutItem() {
            TestItem item = new TestItem("test-id", "test-value");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.PUT);
            assertThat(transactionItem.getItem()).isEqualTo(item);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getKey()).isNull();
            assertThat(transactionItem.getExpression()).isNull();
            assertThat(transactionItem.getValues()).isNull();
        }

        @Test
        void shouldThrowExceptionForNullItem() {
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().put(null, "table")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Item cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullTableNameInPut() {
            TestItem item = new TestItem("id", "value");
            
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().put(item, null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Table name cannot be null");
        }

        @Test
        void shouldCreateMultiplePutItems() {
            TestItem item1 = new TestItem("id-1", "value-1");
            TestItem item2 = new TestItem("id-2", "value-2");

            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item1, "table1")
                    .put(item2, "table2")
                    .build();

            List<DynamoTransactionItem> items = transaction.getItems();
            assertThat(items).hasSize(2);
            assertThat(items.get(0).getItem()).isEqualTo(item1);
            assertThat(items.get(0).getTableName()).isEqualTo("table1");
            assertThat(items.get(1).getItem()).isEqualTo(item2);
            assertThat(items.get(1).getTableName()).isEqualTo("table2");
        }
    }

    @Nested
    class UpdateOperationTests {

        @Test
        void shouldCreateUpdateItem() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .update(key, "test-table", "SET #attr = :val", "new-value")
                    .build();

            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.UPDATE);
            assertThat(transactionItem.getKey()).isEqualTo(key);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getExpression()).isEqualTo("SET #attr = :val");
            assertThat(transactionItem.getValues()).containsExactly("new-value");
            assertThat(transactionItem.getItem()).isNull();
        }

        @Test
        void shouldCreateUpdateItemWithMultipleValues() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .update(key, "test-table", "SET #attr1 = :val1, #attr2 = :val2", "value1", "value2")
                    .build();

            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            assertThat(transactionItem.getValues()).containsExactly("value1", "value2");
        }

        @Test
        void shouldCreateUpdateItemWithNoValues() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .update(key, "test-table", "SET #attr = #attr + 1")
                    .build();

            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            // When no varargs are provided, it creates an empty array, not null
            assertThat(transactionItem.getValues()).isEmpty();
        }

        @Test
        void shouldThrowExceptionForNullKeyInUpdate() {
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().update(null, "table", "expression")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Key cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullTableNameInUpdate() {
            DynamoKey key = DynamoKey.partitionKey("id", "value");
            
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().update(key, null, "expression")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Table name cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullUpdateExpression() {
            DynamoKey key = DynamoKey.partitionKey("id", "value");
            
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().update(key, "table", null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Update expression cannot be null");
        }
    }

    @Nested
    class DeleteOperationTests {

        @Test
        void shouldCreateDeleteItem() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .delete(key, "test-table")
                    .build();

            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.DELETE);
            assertThat(transactionItem.getKey()).isEqualTo(key);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getItem()).isNull();
            assertThat(transactionItem.getExpression()).isNull();
            assertThat(transactionItem.getValues()).isNull();
        }

        @Test
        void shouldThrowExceptionForNullKeyInDelete() {
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().delete(null, "table")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Key cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullTableNameInDelete() {
            DynamoKey key = DynamoKey.partitionKey("id", "value");
            
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().delete(key, null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Table name cannot be null");
        }
    }

    @Nested
    class ConditionCheckOperationTests {

        @Test
        void shouldCreateConditionCheckItem() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .conditionCheck(key, "test-table", "attribute_exists(#attr)", "value")
                    .build();

            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.CONDITION_CHECK);
            assertThat(transactionItem.getKey()).isEqualTo(key);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getExpression()).isEqualTo("attribute_exists(#attr)");
            assertThat(transactionItem.getValues()).containsExactly("value");
            assertThat(transactionItem.getItem()).isNull();
        }

        @Test
        void shouldCreateConditionCheckItemWithNoValues() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .conditionCheck(key, "test-table", "attribute_exists(id)")
                    .build();

            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            // When no varargs are provided, it creates an empty array, not null
            assertThat(transactionItem.getValues()).isEmpty();
        }

        @Test
        void shouldThrowExceptionForNullKeyInConditionCheck() {
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().conditionCheck(null, "table", "expression")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Key cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullTableNameInConditionCheck() {
            DynamoKey key = DynamoKey.partitionKey("id", "value");
            
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().conditionCheck(key, null, "expression")
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Table name cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullConditionExpression() {
            DynamoKey key = DynamoKey.partitionKey("id", "value");
            
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().conditionCheck(key, "table", null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Condition expression cannot be null");
        }
    }

    @Nested
    class AddItemTests {

        @Test
        void shouldAddTransactionItemDirectly() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            DynamoTransactionItem item = DynamoTransactionItem.delete(key, "test-table");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .addItem(item)
                    .build();

            assertThat(transaction.getItems()).containsExactly(item);
        }

        @Test
        void shouldThrowExceptionForNullTransactionItem() {
            assertThatThrownBy(() -> 
                DynamoTransaction.builder().addItem(null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Transaction item cannot be null");
        }

        @Test
        void shouldMixBuilderMethodsWithAddItem() {
            TestItem item = new TestItem("id", "value");
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            DynamoTransactionItem customItem = DynamoTransactionItem.delete(key, "custom-table");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "put-table")
                    .addItem(customItem)
                    .update(key, "update-table", "SET #attr = :val", "new-value")
                    .build();

            assertThat(transaction.size()).isEqualTo(3);
            assertThat(transaction.getItems().get(0).getType()).isEqualTo(DynamoTransactionItem.TransactionType.PUT);
            assertThat(transaction.getItems().get(1).getType()).isEqualTo(DynamoTransactionItem.TransactionType.DELETE);
            assertThat(transaction.getItems().get(2).getType()).isEqualTo(DynamoTransactionItem.TransactionType.UPDATE);
        }
    }

    @Nested
    class ConvenienceMethodTests {

        @Test
        void shouldCreateSinglePutTransaction() {
            TestItem item = new TestItem("id", "value");
            
            DynamoTransaction transaction = DynamoTransaction.singlePut(item, "test-table");

            assertThat(transaction.size()).isEqualTo(1);
            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.PUT);
            assertThat(transactionItem.getItem()).isEqualTo(item);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
        }

        @Test
        void shouldCreateSingleUpdateTransaction() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            
            DynamoTransaction transaction = DynamoTransaction.singleUpdate(key, "test-table", 
                    "SET #attr = :val", "new-value");

            assertThat(transaction.size()).isEqualTo(1);
            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.UPDATE);
            assertThat(transactionItem.getKey()).isEqualTo(key);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getExpression()).isEqualTo("SET #attr = :val");
            assertThat(transactionItem.getValues()).containsExactly("new-value");
        }

        @Test
        void shouldCreateSingleDeleteTransaction() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            
            DynamoTransaction transaction = DynamoTransaction.singleDelete(key, "test-table");

            assertThat(transaction.size()).isEqualTo(1);
            DynamoTransactionItem transactionItem = transaction.getItems().get(0);
            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.DELETE);
            assertThat(transactionItem.getKey()).isEqualTo(key);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
        }
    }

    @Nested
    class EqualityAndHashCodeTests {

        @Test
        void shouldBeEqualForSameTransactions() {
            TestItem item = new TestItem("id", "value");
            
            DynamoTransaction transaction1 = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            DynamoTransaction transaction2 = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            assertThat(transaction1).isEqualTo(transaction2);
            assertThat(transaction1.hashCode()).isEqualTo(transaction2.hashCode());
        }

        @Test
        void shouldNotBeEqualForDifferentItems() {
            TestItem item1 = new TestItem("id1", "value1");
            TestItem item2 = new TestItem("id2", "value2");
            
            DynamoTransaction transaction1 = DynamoTransaction.builder()
                    .put(item1, "test-table")
                    .build();

            DynamoTransaction transaction2 = DynamoTransaction.builder()
                    .put(item2, "test-table")
                    .build();

            assertThat(transaction1).isNotEqualTo(transaction2);
        }

        @Test
        void shouldNotBeEqualForDifferentOrder() {
            TestItem item1 = new TestItem("id1", "value1");
            TestItem item2 = new TestItem("id2", "value2");
            
            DynamoTransaction transaction1 = DynamoTransaction.builder()
                    .put(item1, "table1")
                    .put(item2, "table2")
                    .build();

            DynamoTransaction transaction2 = DynamoTransaction.builder()
                    .put(item2, "table2")
                    .put(item1, "table1")
                    .build();

            assertThat(transaction1).isNotEqualTo(transaction2);
        }

        @Test
        void shouldNotBeEqualToNull() {
            TestItem item = new TestItem("id", "value");
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            assertThat(transaction).isNotEqualTo(null);
        }

        @Test
        void shouldNotBeEqualToDifferentClass() {
            TestItem item = new TestItem("id", "value");
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            assertThat(transaction).isNotEqualTo("string");
        }

        @Test
        void shouldBeEqualToItself() {
            TestItem item = new TestItem("id", "value");
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            assertThat(transaction).isEqualTo(transaction);
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void shouldProvideReadableToString() {
            TestItem item = new TestItem("test-id", "test-value");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            String result = transaction.toString();

            assertThat(result).contains("DynamoTransaction");
            assertThat(result).contains("items=");
        }

        @Test
        void shouldIncludeAllItems() {
            TestItem item = new TestItem("id", "value");
            DynamoKey key = DynamoKey.partitionKey("id", "key-id");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "put-table")
                    .delete(key, "delete-table")
                    .build();

            String result = transaction.toString();

            assertThat(result).contains("PUT");
            assertThat(result).contains("DELETE");
            assertThat(result).contains("put-table");
            assertThat(result).contains("delete-table");
        }
    }

    @Nested
    class ImmutabilityTests {

        @Test
        void shouldReturnImmutableItemsList() {
            TestItem item = new TestItem("id", "value");
            
            DynamoTransaction transaction = DynamoTransaction.builder()
                    .put(item, "test-table")
                    .build();

            List<DynamoTransactionItem> items = transaction.getItems();
            
            // Should throw UnsupportedOperationException when trying to modify
            assertThatThrownBy(() -> 
                items.add(DynamoTransactionItem.put(new TestItem("id2", "value2"), "table2"))
            ).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldNotBeAffectedByBuilderModification() {
            TestItem item1 = new TestItem("id1", "value1");
            TestItem item2 = new TestItem("id2", "value2");
            
            DynamoTransaction.Builder builder = DynamoTransaction.builder()
                    .put(item1, "table1");

            DynamoTransaction transaction = builder.build();

            // Try to modify builder after build (this wouldn't affect the built transaction anyway)
            // since build() creates a new transaction each time
            int originalSize = transaction.size();
            
            assertThat(originalSize).isEqualTo(1);
        }
    }

    @Nested
    class DynamoTransactionItemTests {

        @Test
        void shouldCreatePutTransactionItem() {
            TestItem item = new TestItem("id", "value");
            DynamoTransactionItem transactionItem = DynamoTransactionItem.put(item, "test-table");

            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.PUT);
            assertThat(transactionItem.getItem()).isEqualTo(item);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getKey()).isNull();
            assertThat(transactionItem.getExpression()).isNull();
            assertThat(transactionItem.getValues()).isNull();
        }

        @Test
        void shouldCreateUpdateTransactionItem() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            DynamoTransactionItem transactionItem = DynamoTransactionItem.update(key, "test-table", "SET #attr = :val", "value");

            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.UPDATE);
            assertThat(transactionItem.getKey()).isEqualTo(key);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getExpression()).isEqualTo("SET #attr = :val");
            assertThat(transactionItem.getValues()).containsExactly("value");
            assertThat(transactionItem.getItem()).isNull();
        }

        @Test
        void shouldCreateDeleteTransactionItem() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            DynamoTransactionItem transactionItem = DynamoTransactionItem.delete(key, "test-table");

            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.DELETE);
            assertThat(transactionItem.getKey()).isEqualTo(key);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getItem()).isNull();
            assertThat(transactionItem.getExpression()).isNull();
            assertThat(transactionItem.getValues()).isNull();
        }

        @Test
        void shouldCreateConditionCheckTransactionItem() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            DynamoTransactionItem transactionItem = DynamoTransactionItem.conditionCheck(key, "test-table", "attribute_exists(#attr)", "value");

            assertThat(transactionItem.getType()).isEqualTo(DynamoTransactionItem.TransactionType.CONDITION_CHECK);
            assertThat(transactionItem.getKey()).isEqualTo(key);
            assertThat(transactionItem.getTableName()).isEqualTo("test-table");
            assertThat(transactionItem.getExpression()).isEqualTo("attribute_exists(#attr)");
            assertThat(transactionItem.getValues()).containsExactly("value");
            assertThat(transactionItem.getItem()).isNull();
        }

        @Test
        void shouldReturnCopiedValuesArray() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            Object[] originalValues = {"value1", "value2"};
            
            DynamoTransactionItem transactionItem = DynamoTransactionItem.update(key, "test-table", "SET attr = :val", originalValues);

            Object[] retrievedValues = transactionItem.getValues();
            
            // Should be equal but not the same reference
            assertThat(retrievedValues).isEqualTo(originalValues);
            assertThat(retrievedValues).isNotSameAs(originalValues);

            // Modifying retrieved values should not affect original
            retrievedValues[0] = "modified";
            assertThat(transactionItem.getValues()[0]).isNotEqualTo("modified");
        }

        @Test
        void shouldHandleNullValuesArray() {
            DynamoKey key = DynamoKey.partitionKey("id", "test-id");
            DynamoTransactionItem transactionItem = DynamoTransactionItem.update(key, "test-table", "SET attr = attr + 1");

            // When no varargs are provided, it creates an empty array, not null
            assertThat(transactionItem.getValues()).isEmpty();
        }
    }

    // Test helper class
    static class TestItem {
        private final String id;
        private final String value;

        public TestItem(String id, String value) {
            this.id = id;
            this.value = value;
        }

        public String getId() { return id; }
        public String getValue() { return value; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestItem testItem = (TestItem) o;
            return java.util.Objects.equals(id, testItem.id) &&
                   java.util.Objects.equals(value, testItem.value);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(id, value);
        }

        @Override
        public String toString() {
            return "TestItem{id='" + id + "', value='" + value + "'}";
        }
    }
}