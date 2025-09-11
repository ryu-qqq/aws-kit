package com.ryuqq.aws.commons;

import com.ryuqq.aws.commons.properties.AwsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;

/**
 * Simplified AWS SDK Commons auto-configuration with essential components only
 */
@AutoConfiguration
@EnableConfigurationProperties(AwsProperties.class)
public class AwsSdkCommonsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Region region(AwsProperties properties) {
        return Region.of(properties.getRegion());
    }

    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    @Bean
    @ConditionalOnMissingBean
    public SdkHttpClient httpClient(AwsProperties properties) {
        return ApacheHttpClient.builder()
                .connectionTimeout(properties.getTimeout())
                .socketTimeout(properties.getTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ClientOverrideConfiguration clientOverrideConfiguration(AwsProperties properties) {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(properties.getTimeout())
                .apiCallAttemptTimeout(properties.getTimeout())
                .build();
    }
}