package com.ryuqq.aws.sqs.serialization;

/**
 * Exception thrown when message serialization or deserialization fails.
 */
public class MessageSerializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MessageSerializationException(String message) {
        super(message);
    }

    public MessageSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}