package com.ryuqq.aws.lambda;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;

/**
 * Lambda 클라이언트 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(LambdaAsyncClient.class)
public class AwsLambdaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LambdaAsyncClient lambdaAsyncClient(Region region,
                                             AwsCredentialsProvider credentialsProvider,
                                             ClientOverrideConfiguration clientOverrideConfiguration,
                                             NettyNioAsyncHttpClient httpClient) {
        return LambdaAsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(clientOverrideConfiguration)
                .httpClient(httpClient)
                .build();
    }
}