package com.ryuqq.aws.sqs.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Jackson-based implementation of MessageSerializer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JacksonMessageSerializer implements MessageSerializer {

    private final ObjectMapper objectMapper;

    @Override
    public String serialize(Object object) {
        if (object == null) {
            return null;
        }

        if (object instanceof String) {
            return (String) object;
        }

        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON: {}", object.getClass().getName(), e);
            throw new MessageSerializationException("Failed to serialize object to JSON", e);
        }
    }

    @Override
    public <T> T deserialize(String content, Class<T> targetType) {
        if (content == null) {
            return null;
        }

        if (targetType == String.class) {
            return targetType.cast(content);
        }

        try {
            return objectMapper.readValue(content, targetType);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize JSON to {}: {}", targetType.getName(), content, e);
            throw new MessageSerializationException("Failed to deserialize JSON to " + targetType.getName(), e);
        }
    }
}