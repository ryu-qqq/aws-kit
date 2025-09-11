package com.ryuqq.aws.commons.properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AwsProperties.
 * Tests property binding, validation, and default values.
 */
class AwsPropertiesTest {

    private AwsProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AwsProperties();
    }

    @Test
    void shouldHaveCorrectDefaultValues() {
        assertThat(properties.getRegion()).isEqualTo("us-west-2");
        assertThat(properties.getAccessKey()).isNull();
        assertThat(properties.getSecretKey()).isNull();
        assertThat(properties.getEndpoint()).isNull();
        assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.getMaxRetries()).isEqualTo(3);
    }

    @Test
    void shouldSetAndGetRegion() {
        properties.setRegion("eu-west-1");
        assertThat(properties.getRegion()).isEqualTo("eu-west-1");
    }

    @Test
    void shouldSetAndGetAccessKey() {
        properties.setAccessKey("test-access-key");
        assertThat(properties.getAccessKey()).isEqualTo("test-access-key");
    }

    @Test
    void shouldSetAndGetSecretKey() {
        properties.setSecretKey("test-secret-key");
        assertThat(properties.getSecretKey()).isEqualTo("test-secret-key");
    }

    @Test
    void shouldSetAndGetEndpoint() {
        properties.setEndpoint("http://localhost:4566");
        assertThat(properties.getEndpoint()).isEqualTo("http://localhost:4566");
    }

    @Test
    void shouldSetAndGetTimeout() {
        Duration customTimeout = Duration.ofMinutes(2);
        properties.setTimeout(customTimeout);
        assertThat(properties.getTimeout()).isEqualTo(customTimeout);
    }

    @Test
    void shouldSetAndGetMaxRetries() {
        properties.setMaxRetries(5);
        assertThat(properties.getMaxRetries()).isEqualTo(5);
    }

    @Test
    void shouldBindFromConfigurationProperties() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("aws.region", "ap-northeast-1");
        configMap.put("aws.access-key", "bound-access-key");
        configMap.put("aws.secret-key", "bound-secret-key");
        configMap.put("aws.endpoint", "https://s3.amazonaws.com");
        configMap.put("aws.timeout", "PT45S");
        configMap.put("aws.max-retries", "10");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(configMap);
        Binder binder = new Binder(source);

        AwsProperties boundProperties = binder.bind("aws", AwsProperties.class).get();

        assertThat(boundProperties.getRegion()).isEqualTo("ap-northeast-1");
        assertThat(boundProperties.getAccessKey()).isEqualTo("bound-access-key");
        assertThat(boundProperties.getSecretKey()).isEqualTo("bound-secret-key");
        assertThat(boundProperties.getEndpoint()).isEqualTo("https://s3.amazonaws.com");
        assertThat(boundProperties.getTimeout()).isEqualTo(Duration.ofSeconds(45));
        assertThat(boundProperties.getMaxRetries()).isEqualTo(10);
    }

    @Test
    void shouldBindPartialConfiguration() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("aws.region", "us-east-1");
        configMap.put("aws.timeout", "PT60S");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(configMap);
        Binder binder = new Binder(source);

        AwsProperties boundProperties = binder.bind("aws", AwsProperties.class).get();

        assertThat(boundProperties.getRegion()).isEqualTo("us-east-1");
        assertThat(boundProperties.getTimeout()).isEqualTo(Duration.ofMinutes(1));
        // Verify defaults for unspecified properties
        assertThat(boundProperties.getAccessKey()).isNull();
        assertThat(boundProperties.getSecretKey()).isNull();
        assertThat(boundProperties.getEndpoint()).isNull();
        assertThat(boundProperties.getMaxRetries()).isEqualTo(3);
    }

    @Test
    void shouldHandleEmptyConfiguration() {
        Map<String, Object> configMap = new HashMap<>();
        ConfigurationPropertySource source = new MapConfigurationPropertySource(configMap);
        Binder binder = new Binder(source);

        AwsProperties boundProperties = binder.bind("aws", AwsProperties.class)
                .orElse(new AwsProperties());

        // Should use all default values
        assertThat(boundProperties.getRegion()).isEqualTo("us-west-2");
        assertThat(boundProperties.getAccessKey()).isNull();
        assertThat(boundProperties.getSecretKey()).isNull();
        assertThat(boundProperties.getEndpoint()).isNull();
        assertThat(boundProperties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(boundProperties.getMaxRetries()).isEqualTo(3);
    }

    @Test
    void shouldBindDurationFromString() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("aws.timeout", "PT2M30S"); // 2 minutes 30 seconds

        ConfigurationPropertySource source = new MapConfigurationPropertySource(configMap);
        Binder binder = new Binder(source);

        AwsProperties boundProperties = binder.bind("aws", AwsProperties.class).get();
        assertThat(boundProperties.getTimeout()).isEqualTo(Duration.ofSeconds(150));
    }

    @Test
    void shouldBindDurationFromSeconds() {
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("aws.timeout", "45s");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(configMap);
        Binder binder = new Binder(source);

        AwsProperties boundProperties = binder.bind("aws", AwsProperties.class).get();
        assertThat(boundProperties.getTimeout()).isEqualTo(Duration.ofSeconds(45));
    }

    @Test
    void shouldSetNullValues() {
        // Verify that null values can be set without issues
        properties.setRegion(null);
        properties.setAccessKey(null);
        properties.setSecretKey(null);
        properties.setEndpoint(null);
        properties.setTimeout(null);

        assertThat(properties.getRegion()).isNull();
        assertThat(properties.getAccessKey()).isNull();
        assertThat(properties.getSecretKey()).isNull();
        assertThat(properties.getEndpoint()).isNull();
        assertThat(properties.getTimeout()).isNull();
    }

    @Test
    void shouldHandleEdgeCaseRetryValues() {
        properties.setMaxRetries(0);
        assertThat(properties.getMaxRetries()).isEqualTo(0);

        properties.setMaxRetries(Integer.MAX_VALUE);
        assertThat(properties.getMaxRetries()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void shouldBindLocalStackConfiguration() {
        // Common LocalStack configuration pattern
        Map<String, Object> configMap = new HashMap<>();
        configMap.put("aws.region", "us-east-1");
        configMap.put("aws.access-key", "test");
        configMap.put("aws.secret-key", "test");
        configMap.put("aws.endpoint", "http://localhost:4566");

        ConfigurationPropertySource source = new MapConfigurationPropertySource(configMap);
        Binder binder = new Binder(source);

        AwsProperties boundProperties = binder.bind("aws", AwsProperties.class).get();

        assertThat(boundProperties.getRegion()).isEqualTo("us-east-1");
        assertThat(boundProperties.getAccessKey()).isEqualTo("test");
        assertThat(boundProperties.getSecretKey()).isEqualTo("test");
        assertThat(boundProperties.getEndpoint()).isEqualTo("http://localhost:4566");
    }
}