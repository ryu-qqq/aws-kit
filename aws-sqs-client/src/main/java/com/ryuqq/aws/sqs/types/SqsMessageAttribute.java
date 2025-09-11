package com.ryuqq.aws.sqs.types;

import java.util.Objects;

/**
 * Represents a message attribute for SQS messages.
 * Library-specific type to avoid exposing AWS SDK types in public API.
 */
public final class SqsMessageAttribute {
    
    public enum DataType {
        STRING("String"),
        NUMBER("Number"),
        BINARY("Binary");
        
        private final String value;
        
        DataType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static DataType fromValue(String value) {
            for (DataType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown data type: " + value);
        }
    }
    
    private final String stringValue;
    private final byte[] binaryValue;
    private final DataType dataType;
    
    private SqsMessageAttribute(String stringValue, byte[] binaryValue, DataType dataType) {
        this.stringValue = stringValue;
        this.binaryValue = binaryValue;
        this.dataType = dataType;
    }
    
    public String getStringValue() {
        return stringValue;
    }
    
    public byte[] getBinaryValue() {
        return binaryValue;
    }
    
    public DataType getDataType() {
        return dataType;
    }
    
    public static SqsMessageAttribute stringAttribute(String value) {
        Objects.requireNonNull(value, "String value cannot be null");
        return new SqsMessageAttribute(value, null, DataType.STRING);
    }
    
    public static SqsMessageAttribute numberAttribute(String value) {
        Objects.requireNonNull(value, "Number value cannot be null");
        return new SqsMessageAttribute(value, null, DataType.NUMBER);
    }
    
    public static SqsMessageAttribute numberAttribute(Number value) {
        Objects.requireNonNull(value, "Number value cannot be null");
        return new SqsMessageAttribute(value.toString(), null, DataType.NUMBER);
    }
    
    public static SqsMessageAttribute binaryAttribute(byte[] value) {
        Objects.requireNonNull(value, "Binary value cannot be null");
        return new SqsMessageAttribute(null, value.clone(), DataType.BINARY);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqsMessageAttribute that = (SqsMessageAttribute) o;
        return Objects.equals(stringValue, that.stringValue) &&
               Objects.deepEquals(binaryValue, that.binaryValue) &&
               dataType == that.dataType;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(stringValue, Objects.hashCode(binaryValue), dataType);
    }
    
    @Override
    public String toString() {
        return "SqsMessageAttribute{" +
               "dataType=" + dataType +
               ", hasStringValue=" + (stringValue != null) +
               ", hasBinaryValue=" + (binaryValue != null) +
               '}';
    }
}