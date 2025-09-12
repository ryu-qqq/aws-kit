package com.ryuqq.aws.dynamodb;

import com.ryuqq.aws.dynamodb.properties.DynamoDbProperties;
import com.ryuqq.aws.dynamodb.service.DefaultDynamoDbService;
import com.ryuqq.aws.dynamodb.service.DynamoDbService;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Simplified DynamoDB auto-configuration
 */
@AutoConfiguration
@EnableConfigurationProperties(DynamoDbProperties.class)
public class AwsDynamoDbAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SdkAsyncHttpClient dynamoDbAsyncHttpClient(DynamoDbProperties properties) {
        // timeout 설정을 포함한 HTTP 클라이언트 구성
        return NettyNioAsyncHttpClient.builder()
                .connectionTimeout(properties.getTimeout())
                .connectionAcquisitionTimeout(properties.getTimeout())
                .readTimeout(properties.getTimeout())
                .writeTimeout(properties.getTimeout())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbAsyncClient dynamoDbAsyncClient(DynamoDbProperties properties, 
                                                   SdkAsyncHttpClient httpClient) {
        var builder = DynamoDbAsyncClient.builder();
        
        // 지역 설정
        if (properties.getRegion() != null) {
            builder.region(software.amazon.awssdk.regions.Region.of(properties.getRegion()));
        }
        
        // 엔드포인트 오버라이드 (LocalStack 등)
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(java.net.URI.create(properties.getEndpoint()));
        }
        
        // 재시도 정책 설정 - maxRetries 값에 따른 재시도 모드 설정
        if (properties.getMaxRetries() > 3) {
            // 기본 재시도 횟수보다 많은 경우 LEGACY 모드 사용 (더 관대한 재시도)
            builder.overrideConfiguration(b -> b.retryPolicy(RetryMode.LEGACY));
        } else {
            // 기본 재시도 횟수 이하인 경우 STANDARD 모드 사용
            builder.overrideConfiguration(b -> b.retryPolicy(RetryMode.STANDARD));
        }
        
        // HTTP 클라이언트 설정 (timeout 포함)
        builder.httpClient(httpClient);
        
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
    public TableNameResolver tableNameResolver(DynamoDbProperties properties) {
        return new TableNameResolver(properties.getTablePrefix(), properties.getTableSuffix());
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbService<?> dynamoDbService(DynamoDbEnhancedAsyncClient enhancedClient, 
                                              DynamoDbAsyncClient rawClient,
                                              TableNameResolver tableNameResolver) {
        return new DefaultDynamoDbService<>(enhancedClient, rawClient, tableNameResolver);
    }
}