package com.ryuqq.aws.sqs.config;

import com.ryuqq.aws.sqs.client.DefaultSqsClient;
import com.ryuqq.aws.sqs.client.SqsClient;
import com.ryuqq.aws.sqs.properties.SqsProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * SQS 클라이언트 자동 설정
 */
@Configuration
@EnableConfigurationProperties(SqsProperties.class)
public class SqsClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqsAsyncClient sqsAsyncClient(Region region,
                                       AwsCredentialsProvider credentialsProvider,
                                       ClientOverrideConfiguration clientOverrideConfiguration,
                                       NettyNioAsyncHttpClient httpClient) {
        return SqsAsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(clientOverrideConfiguration)
                .httpClient(httpClient)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqsClient sqsClient(SqsAsyncClient sqsAsyncClient,
                              com.ryuqq.aws.commons.metrics.AwsMetricsProvider metricsProvider) {
        return new DefaultSqsClient(sqsAsyncClient, metricsProvider);
    }
}