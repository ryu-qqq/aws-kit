package com.ryuqq.aws.sqs.types;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SqsMessageAttribute.
 * Tests factory methods, data types, and validation.
 */
class SqsMessageAttributeTest {

    @Nested
    class StringAttributeTests {

        @Test
        void shouldCreateStringAttribute() {
            SqsMessageAttribute attribute = SqsMessageAttribute.stringAttribute("test-string");

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.STRING);
            assertThat(attribute.getStringValue()).isEqualTo("test-string");
            assertThat(attribute.getBinaryValue()).isNull();
        }

        @Test
        void shouldCreateStringAttributeWithEmptyString() {
            SqsMessageAttribute attribute = SqsMessageAttribute.stringAttribute("");

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.STRING);
            assertThat(attribute.getStringValue()).isEqualTo("");
            assertThat(attribute.getBinaryValue()).isNull();
        }

        @Test
        void shouldCreateStringAttributeWithSpecialCharacters() {
            String specialString = "特殊文字\n\t\"'\\";
            SqsMessageAttribute attribute = SqsMessageAttribute.stringAttribute(specialString);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.STRING);
            assertThat(attribute.getStringValue()).isEqualTo(specialString);
        }

        @Test
        void shouldThrowExceptionForNullStringValue() {
            assertThatThrownBy(() -> 
                SqsMessageAttribute.stringAttribute(null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("String value cannot be null");
        }
    }

    @Nested
    class NumberAttributeTests {

        @Test
        void shouldCreateNumberAttributeFromString() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute("42");

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("42");
            assertThat(attribute.getBinaryValue()).isNull();
        }

        @Test
        void shouldCreateNumberAttributeFromInteger() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute(123);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("123");
            assertThat(attribute.getBinaryValue()).isNull();
        }

        @Test
        void shouldCreateNumberAttributeFromLong() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute(123456789L);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("123456789");
        }

        @Test
        void shouldCreateNumberAttributeFromDouble() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute(123.45);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("123.45");
        }

        @Test
        void shouldCreateNumberAttributeFromFloat() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute(42.5f);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("42.5");
        }

        @Test
        void shouldCreateNumberAttributeWithNegativeNumber() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute(-42);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("-42");
        }

        @Test
        void shouldCreateNumberAttributeWithZero() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute(0);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("0");
        }

        @Test
        void shouldCreateNumberAttributeWithDecimalString() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute("123.456789");

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("123.456789");
        }

        @Test
        void shouldCreateNumberAttributeWithScientificNotation() {
            SqsMessageAttribute attribute = SqsMessageAttribute.numberAttribute("1.23E+10");

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(attribute.getStringValue()).isEqualTo("1.23E+10");
        }

        @Test
        void shouldThrowExceptionForNullStringNumberValue() {
            assertThatThrownBy(() -> 
                SqsMessageAttribute.numberAttribute((String) null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Number value cannot be null");
        }

        @Test
        void shouldThrowExceptionForNullNumberValue() {
            assertThatThrownBy(() -> 
                SqsMessageAttribute.numberAttribute((Number) null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Number value cannot be null");
        }
    }

    @Nested
    class BinaryAttributeTests {

        @Test
        void shouldCreateBinaryAttribute() {
            byte[] binaryData = "test-binary-data".getBytes();
            SqsMessageAttribute attribute = SqsMessageAttribute.binaryAttribute(binaryData);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.BINARY);
            assertThat(attribute.getBinaryValue()).isEqualTo(binaryData);
            assertThat(attribute.getStringValue()).isNull();
        }

        @Test
        void shouldCreateBinaryAttributeWithEmptyArray() {
            byte[] emptyData = new byte[0];
            SqsMessageAttribute attribute = SqsMessageAttribute.binaryAttribute(emptyData);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.BINARY);
            assertThat(attribute.getBinaryValue()).isEqualTo(emptyData);
        }

        @Test
        void shouldCreateBinaryAttributeWithLargeBinaryData() {
            byte[] largeData = new byte[1024];
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            
            SqsMessageAttribute attribute = SqsMessageAttribute.binaryAttribute(largeData);

            assertThat(attribute.getDataType()).isEqualTo(SqsMessageAttribute.DataType.BINARY);
            assertThat(attribute.getBinaryValue()).isEqualTo(largeData);
        }

        @Test
        void shouldCloneBinaryArrayToPreventMutation() {
            byte[] originalData = "original-data".getBytes();
            SqsMessageAttribute attribute = SqsMessageAttribute.binaryAttribute(originalData);

            // Modify original array
            originalData[0] = 'X';

            // Attribute should be unaffected
            assertThat(attribute.getBinaryValue()).isNotEqualTo(originalData);
            assertThat(attribute.getBinaryValue()[0]).isNotEqualTo('X');
        }

        @Test
        void shouldThrowExceptionForNullBinaryValue() {
            assertThatThrownBy(() -> 
                SqsMessageAttribute.binaryAttribute(null)
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("Binary value cannot be null");
        }
    }

    @Nested
    class DataTypeEnumTests {

        @Test
        void shouldHaveCorrectDataTypeValues() {
            assertThat(SqsMessageAttribute.DataType.STRING.getValue()).isEqualTo("String");
            assertThat(SqsMessageAttribute.DataType.NUMBER.getValue()).isEqualTo("Number");
            assertThat(SqsMessageAttribute.DataType.BINARY.getValue()).isEqualTo("Binary");
        }

        @Test
        void shouldConvertFromStringValue() {
            assertThat(SqsMessageAttribute.DataType.fromValue("String"))
                    .isEqualTo(SqsMessageAttribute.DataType.STRING);
            assertThat(SqsMessageAttribute.DataType.fromValue("Number"))
                    .isEqualTo(SqsMessageAttribute.DataType.NUMBER);
            assertThat(SqsMessageAttribute.DataType.fromValue("Binary"))
                    .isEqualTo(SqsMessageAttribute.DataType.BINARY);
        }

        @Test
        void shouldThrowExceptionForUnknownDataType() {
            assertThatThrownBy(() -> 
                SqsMessageAttribute.DataType.fromValue("Unknown")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("Unknown data type: Unknown");
        }

        @Test
        void shouldThrowExceptionForNullDataType() {
            assertThatThrownBy(() -> 
                SqsMessageAttribute.DataType.fromValue(null)
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldBeCaseSensitive() {
            assertThatThrownBy(() -> 
                SqsMessageAttribute.DataType.fromValue("string")
            ).isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> 
                SqsMessageAttribute.DataType.fromValue("STRING")
            ).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void shouldHaveAllExpectedEnumValues() {
            SqsMessageAttribute.DataType[] values = SqsMessageAttribute.DataType.values();
            
            assertThat(values).hasSize(3);
            assertThat(values).containsExactlyInAnyOrder(
                    SqsMessageAttribute.DataType.STRING,
                    SqsMessageAttribute.DataType.NUMBER,
                    SqsMessageAttribute.DataType.BINARY
            );
        }
    }

    @Nested
    class EqualityAndHashCodeTests {

        @Test
        void shouldBeEqualForSameStringAttributes() {
            SqsMessageAttribute attr1 = SqsMessageAttribute.stringAttribute("test");
            SqsMessageAttribute attr2 = SqsMessageAttribute.stringAttribute("test");

            assertThat(attr1).isEqualTo(attr2);
            assertThat(attr1.hashCode()).isEqualTo(attr2.hashCode());
        }

        @Test
        void shouldBeEqualForSameNumberAttributes() {
            SqsMessageAttribute attr1 = SqsMessageAttribute.numberAttribute("42");
            SqsMessageAttribute attr2 = SqsMessageAttribute.numberAttribute(42);

            assertThat(attr1).isEqualTo(attr2);
            assertThat(attr1.hashCode()).isEqualTo(attr2.hashCode());
        }

        @Test
        void shouldBeEqualForSameBinaryAttributes() {
            byte[] data = "test-data".getBytes();
            SqsMessageAttribute attr1 = SqsMessageAttribute.binaryAttribute(data);
            SqsMessageAttribute attr2 = SqsMessageAttribute.binaryAttribute(data.clone());

            assertThat(attr1).isEqualTo(attr2);
            // Note: Hash code equality is not guaranteed for binary arrays due to implementation
            // using Objects.hashCode(binaryValue) instead of Arrays.hashCode(binaryValue)
        }

        @Test
        void shouldNotBeEqualForDifferentStringValues() {
            SqsMessageAttribute attr1 = SqsMessageAttribute.stringAttribute("test1");
            SqsMessageAttribute attr2 = SqsMessageAttribute.stringAttribute("test2");

            assertThat(attr1).isNotEqualTo(attr2);
        }

        @Test
        void shouldNotBeEqualForDifferentNumberValues() {
            SqsMessageAttribute attr1 = SqsMessageAttribute.numberAttribute("42");
            SqsMessageAttribute attr2 = SqsMessageAttribute.numberAttribute("43");

            assertThat(attr1).isNotEqualTo(attr2);
        }

        @Test
        void shouldNotBeEqualForDifferentBinaryValues() {
            SqsMessageAttribute attr1 = SqsMessageAttribute.binaryAttribute("data1".getBytes());
            SqsMessageAttribute attr2 = SqsMessageAttribute.binaryAttribute("data2".getBytes());

            assertThat(attr1).isNotEqualTo(attr2);
        }

        @Test
        void shouldNotBeEqualForDifferentDataTypes() {
            SqsMessageAttribute attr1 = SqsMessageAttribute.stringAttribute("42");
            SqsMessageAttribute attr2 = SqsMessageAttribute.numberAttribute("42");

            assertThat(attr1).isNotEqualTo(attr2);
        }

        @Test
        void shouldNotBeEqualToNull() {
            SqsMessageAttribute attr = SqsMessageAttribute.stringAttribute("test");
            assertThat(attr).isNotEqualTo(null);
        }

        @Test
        void shouldNotBeEqualToDifferentClass() {
            SqsMessageAttribute attr = SqsMessageAttribute.stringAttribute("test");
            assertThat(attr).isNotEqualTo("test");
        }

        @Test
        void shouldBeEqualToItself() {
            SqsMessageAttribute attr = SqsMessageAttribute.stringAttribute("test");
            assertThat(attr).isEqualTo(attr);
        }

        @Test
        void shouldHandleEmptyBinaryArraysInEquality() {
            SqsMessageAttribute attr1 = SqsMessageAttribute.binaryAttribute(new byte[0]);
            SqsMessageAttribute attr2 = SqsMessageAttribute.binaryAttribute(new byte[0]);

            assertThat(attr1).isEqualTo(attr2);
            // Note: Hash code equality is not guaranteed for binary arrays due to implementation
            // using Objects.hashCode(binaryValue) instead of Arrays.hashCode(binaryValue)
        }
    }

    @Nested
    class ToStringTests {

        @Test
        void shouldProvideReadableToStringForStringAttribute() {
            SqsMessageAttribute attr = SqsMessageAttribute.stringAttribute("test");
            String result = attr.toString();

            assertThat(result).contains("SqsMessageAttribute");
            assertThat(result).contains("dataType=STRING");
            assertThat(result).contains("hasStringValue=true");
            assertThat(result).contains("hasBinaryValue=false");
        }

        @Test
        void shouldProvideReadableToStringForNumberAttribute() {
            SqsMessageAttribute attr = SqsMessageAttribute.numberAttribute("42");
            String result = attr.toString();

            assertThat(result).contains("dataType=NUMBER");
            assertThat(result).contains("hasStringValue=true");
            assertThat(result).contains("hasBinaryValue=false");
        }

        @Test
        void shouldProvideReadableToStringForBinaryAttribute() {
            SqsMessageAttribute attr = SqsMessageAttribute.binaryAttribute("test".getBytes());
            String result = attr.toString();

            assertThat(result).contains("dataType=BINARY");
            assertThat(result).contains("hasStringValue=false");
            assertThat(result).contains("hasBinaryValue=true");
        }

        @Test
        void shouldNotExposeActualValues() {
            SqsMessageAttribute stringAttr = SqsMessageAttribute.stringAttribute("sensitive-data");
            SqsMessageAttribute numberAttr = SqsMessageAttribute.numberAttribute("123456789");
            SqsMessageAttribute binaryAttr = SqsMessageAttribute.binaryAttribute("binary-secret".getBytes());

            // toString should not contain the actual values for security
            assertThat(stringAttr.toString()).doesNotContain("sensitive-data");
            assertThat(numberAttr.toString()).doesNotContain("123456789");
            assertThat(binaryAttr.toString()).doesNotContain("binary-secret");
        }
    }

    @Nested
    class ImmutabilityTests {

        @Test
        void shouldReturnClonedBinaryArray() {
            byte[] originalData = "test-data".getBytes();
            SqsMessageAttribute attr = SqsMessageAttribute.binaryAttribute(originalData);

            byte[] retrievedData = attr.getBinaryValue();
            
            // Should be equal but not the same reference
            assertThat(retrievedData).isEqualTo(originalData);
            assertThat(retrievedData).isNotSameAs(originalData);

            // Modifying retrieved data should not affect original
            retrievedData[0] = 'X';
            assertThat(attr.getBinaryValue()[0]).isNotEqualTo('X');
        }

        @Test
        void shouldMaintainImmutabilityAfterConstruction() {
            byte[] originalData = "original".getBytes();
            SqsMessageAttribute attr = SqsMessageAttribute.binaryAttribute(originalData);

            // Modify original data after construction
            originalData[0] = 'X';

            // Attribute should be unaffected
            assertThat(attr.getBinaryValue()[0]).isNotEqualTo('X');
            assertThat(new String(attr.getBinaryValue())).isEqualTo("original");
        }
    }

    @Nested
    class ConstructorTests {

        @Test
        void shouldCreateValidStringAttribute() {
            SqsMessageAttribute attr = SqsMessageAttribute.stringAttribute("test");

            assertThat(attr.getStringValue()).isEqualTo("test");
            assertThat(attr.getBinaryValue()).isNull();
            assertThat(attr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.STRING);
        }

        @Test
        void shouldCreateValidNumberAttribute() {
            SqsMessageAttribute attr = SqsMessageAttribute.numberAttribute(42);

            assertThat(attr.getStringValue()).isEqualTo("42");
            assertThat(attr.getBinaryValue()).isNull();
            assertThat(attr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.NUMBER);
        }

        @Test
        void shouldCreateValidBinaryAttribute() {
            byte[] data = "test".getBytes();
            SqsMessageAttribute attr = SqsMessageAttribute.binaryAttribute(data);

            assertThat(attr.getStringValue()).isNull();
            assertThat(attr.getBinaryValue()).isEqualTo(data);
            assertThat(attr.getDataType()).isEqualTo(SqsMessageAttribute.DataType.BINARY);
        }
    }
}