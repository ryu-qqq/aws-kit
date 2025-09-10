package com.ryuqq.aws.sqs.serialization;

/**
 * Interface for message serialization and deserialization.
 */
public interface MessageSerializer {

    /**
     * Serialize an object to a string representation.
     *
     * @param object the object to serialize
     * @return the serialized string
     */
    String serialize(Object object);

    /**
     * Deserialize a string to the specified type.
     *
     * @param content the string to deserialize
     * @param targetType the target type
     * @param <T> the type parameter
     * @return the deserialized object
     */
    <T> T deserialize(String content, Class<T> targetType);
}