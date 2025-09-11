package com.ryuqq.aws.lambda;

import com.ryuqq.aws.lambda.properties.LambdaProperties;
import com.ryuqq.aws.lambda.service.DefaultLambdaService;
import com.ryuqq.aws.lambda.service.LambdaService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;

/**
 * Lambda 클라이언트 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(LambdaAsyncClient.class)
@EnableConfigurationProperties(LambdaProperties.class)
public class AwsLambdaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public LambdaAsyncClient lambdaAsyncClient(Region region,
                                             AwsCredentialsProvider credentialsProvider,
                                             LambdaProperties lambdaProperties) {
        // Configure retry at the client level instead of manual implementation
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder()
                        .numRetries(lambdaProperties.getMaxRetries())
                        .build())
                .apiCallTimeout(lambdaProperties.getTimeout())
                .build();
        
        return LambdaAsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(overrideConfig)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public LambdaService lambdaService(LambdaAsyncClient lambdaAsyncClient, LambdaProperties lambdaProperties) {
        return new DefaultLambdaService(lambdaAsyncClient, lambdaProperties);
    }
}