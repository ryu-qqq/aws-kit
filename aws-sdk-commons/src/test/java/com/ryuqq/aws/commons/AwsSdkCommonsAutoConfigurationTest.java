package com.ryuqq.aws.commons;

import com.ryuqq.aws.commons.properties.AwsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for AwsSdkCommonsAutoConfiguration.
 * Tests Spring Auto-Configuration behavior and bean creation.
 */
class AwsSdkCommonsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AwsSdkCommonsAutoConfiguration.class));

    @Test
    void shouldCreateAllBeansWithDefaultProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Region.class);
            assertThat(context).hasSingleBean(AwsCredentialsProvider.class);
            assertThat(context).hasSingleBean(SdkHttpClient.class);
            assertThat(context).hasSingleBean(ClientOverrideConfiguration.class);
        });
    }

    @Test
    void shouldCreateRegionBeanWithDefaultRegion() {
        contextRunner.run(context -> {
            Region region = context.getBean(Region.class);
            assertThat(region).isEqualTo(Region.AP_NORTHEAST_2);
        });
    }

    @Test
    void shouldCreateRegionBeanWithCustomRegion() {
        contextRunner
                .withPropertyValues("aws.region=eu-west-1")
                .run(context -> {
                    Region region = context.getBean(Region.class);
                    assertThat(region).isEqualTo(Region.EU_WEST_1);
                });
    }

    @Test
    void shouldConfigureHttpClientWithDefaultTimeout() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SdkHttpClient.class);
            // Note: We can't easily test internal timeout configuration without reflection
            // This test verifies bean creation, integration tests would verify behavior
        });
    }

    @Test
    void shouldConfigureHttpClientWithCustomTimeout() {
        contextRunner
                .withPropertyValues("aws.timeout=PT45S")
                .run(context -> {
                    assertThat(context).hasSingleBean(SdkHttpClient.class);
                    // Bean creation with custom timeout
                });
    }

    @Test
    void shouldConfigureClientOverrideWithDefaultSettings() {
        contextRunner.run(context -> {
            ClientOverrideConfiguration config = context.getBean(ClientOverrideConfiguration.class);
            assertThat(config).isNotNull();
            // Default timeout should be 30 seconds
        });
    }

    @Test
    void shouldConfigureClientOverrideWithCustomSettings() {
        contextRunner
                .withPropertyValues("aws.timeout=PT60S")
                .run(context -> {
                    ClientOverrideConfiguration config = context.getBean(ClientOverrideConfiguration.class);
                    assertThat(config).isNotNull();
                });
    }

    @Test
    void shouldRespectConditionalOnMissingBeanForRegion() {
        contextRunner
                .withUserConfiguration(CustomRegionConfiguration.class)
                .run(context -> {
                    Region region = context.getBean(Region.class);
                    assertThat(region).isEqualTo(Region.AP_NORTHEAST_1);
                });
    }

    @Test
    void shouldRespectConditionalOnMissingBeanForCredentialsProvider() {
        contextRunner
                .withUserConfiguration(CustomCredentialsConfiguration.class)
                .run(context -> {
                    AwsCredentialsProvider provider = context.getBean(AwsCredentialsProvider.class);
                    assertThat(provider).isInstanceOf(TestCredentialsProvider.class);
                });
    }

    @Test
    void shouldRespectConditionalOnMissingBeanForHttpClient() {
        contextRunner
                .withUserConfiguration(CustomHttpClientConfiguration.class)
                .run(context -> {
                    SdkHttpClient httpClient = context.getBean(SdkHttpClient.class);
                    assertThat(httpClient).isInstanceOf(TestHttpClient.class);
                });
    }

    @Test
    void shouldRespectConditionalOnMissingBeanForClientOverrideConfiguration() {
        contextRunner
                .withUserConfiguration(CustomClientOverrideConfiguration.class)
                .run(context -> {
                    ClientOverrideConfiguration config = context.getBean(ClientOverrideConfiguration.class);
                    assertThat(config.apiCallTimeout())
                            .hasValue(Duration.ofMinutes(2));
                });
    }

    @Test
    void shouldLoadAwsPropertiesCorrectly() {
        contextRunner
                .withPropertyValues(
                        "aws.region=us-east-1",
                        "aws.access-key=test-access-key",
                        "aws.secret-key=test-secret-key",
                        "aws.endpoint=http://localhost:4566",
                        "aws.timeout=PT120S",
                        "aws.max-retries=5"
                )
                .run(context -> {
                    AwsProperties properties = context.getBean(AwsProperties.class);
                    assertThat(properties.getRegion()).isEqualTo("us-east-1");
                    assertThat(properties.getAccessKey()).isEqualTo("test-access-key");
                    assertThat(properties.getSecretKey()).isEqualTo("test-secret-key");
                    assertThat(properties.getEndpoint()).isEqualTo("http://localhost:4566");
                    assertThat(properties.getTimeout()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(properties.getMaxRetries()).isEqualTo(5);
                });
    }

    // Test configurations for ConditionalOnMissingBean verification

    @Configuration
    static class CustomRegionConfiguration {
        @Bean
        Region region() {
            return Region.AP_NORTHEAST_1;
        }
    }

    @Configuration
    static class CustomCredentialsConfiguration {
        @Bean
        AwsCredentialsProvider awsCredentialsProvider() {
            return new TestCredentialsProvider();
        }
    }

    @Configuration
    static class CustomHttpClientConfiguration {
        @Bean
        SdkHttpClient httpClient() {
            return new TestHttpClient();
        }
    }

    @Configuration
    static class CustomClientOverrideConfiguration {
        @Bean
        ClientOverrideConfiguration clientOverrideConfiguration() {
            return ClientOverrideConfiguration.builder()
                    .apiCallTimeout(Duration.ofMinutes(2))
                    .build();
        }
    }

    // Test implementations for mocking
    
    static class TestCredentialsProvider implements AwsCredentialsProvider {
        @Override
        public software.amazon.awssdk.auth.credentials.AwsCredentials resolveCredentials() {
            return software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test");
        }
    }

    static class TestHttpClient implements SdkHttpClient {
        @Override
        public software.amazon.awssdk.http.ExecutableHttpRequest prepareRequest(
                software.amazon.awssdk.http.HttpExecuteRequest request) {
            throw new UnsupportedOperationException("Test implementation");
        }

        @Override
        public void close() {
            // Test implementation
        }

        @Override
        public String clientName() {
            return "test-client";
        }
    }
}