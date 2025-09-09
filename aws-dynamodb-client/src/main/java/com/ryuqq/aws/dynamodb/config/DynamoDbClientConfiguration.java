package com.ryuqq.aws.dynamodb.config;

import com.ryuqq.aws.dynamodb.client.DefaultDynamoDbClient;
import com.ryuqq.aws.dynamodb.client.DynamoDbClient;
import com.ryuqq.aws.dynamodb.properties.DynamoDbProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;

/**
 * DynamoDB 클라이언트 자동 설정
 */
@Configuration
@EnableConfigurationProperties(DynamoDbProperties.class)
public class DynamoDbClientConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbAsyncClient dynamoDbAsyncClient(Region region,
                                                 AwsCredentialsProvider credentialsProvider,
                                                 ClientOverrideConfiguration clientOverrideConfiguration,
                                                 NettyNioAsyncHttpClient httpClient,
                                                 DynamoDbProperties dynamoDbProperties) {
        
        var builder = DynamoDbAsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(clientOverrideConfiguration)
                .httpClient(httpClient);
        
        // LocalStack 또는 커스텀 엔드포인트 설정
        if (dynamoDbProperties.getEndpoint() != null) {
            builder.endpointOverride(URI.create(dynamoDbProperties.getEndpoint()));
        }
        
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient(DynamoDbAsyncClient dynamoDbAsyncClient) {
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbClient dynamoDbClient(DynamoDbAsyncClient dynamoDbAsyncClient,
                                       DynamoDbEnhancedAsyncClient enhancedClient,
                                       com.ryuqq.aws.commons.metrics.AwsMetricsProvider metricsProvider) {
        return new DefaultDynamoDbClient(dynamoDbAsyncClient, enhancedClient, metricsProvider);
    }
}