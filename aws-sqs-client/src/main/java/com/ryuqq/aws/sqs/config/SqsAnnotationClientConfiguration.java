package com.ryuqq.aws.sqs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqq.aws.sqs.serialization.JacksonMessageSerializer;
import com.ryuqq.aws.sqs.serialization.MessageSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for annotation-based SQS clients.
 */
@Configuration
public class SqsAnnotationClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageSerializer messageSerializer(ObjectMapper objectMapper) {
        return new JacksonMessageSerializer(objectMapper);
    }
}