package com.ryuqq.aws.sqs;

import com.ryuqq.aws.sqs.properties.SqsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * SQS 클라이언트 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(SqsAsyncClient.class)
@EnableConfigurationProperties(SqsProperties.class)
public class AwsSqsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SqsAsyncClient sqsAsyncClient(Region region,
                                       AwsCredentialsProvider credentialsProvider,
                                       ClientOverrideConfiguration clientOverrideConfiguration) {
        return SqsAsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(clientOverrideConfiguration)
                .build();
    }
}