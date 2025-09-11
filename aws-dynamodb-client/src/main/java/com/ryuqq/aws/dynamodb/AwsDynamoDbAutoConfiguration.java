package com.ryuqq.aws.dynamodb;

import com.ryuqq.aws.dynamodb.properties.DynamoDbProperties;
import com.ryuqq.aws.dynamodb.service.DefaultDynamoDbService;
import com.ryuqq.aws.dynamodb.service.DynamoDbService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * Simplified DynamoDB auto-configuration
 */
@AutoConfiguration
@EnableConfigurationProperties(DynamoDbProperties.class)
public class AwsDynamoDbAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DynamoDbAsyncClient dynamoDbAsyncClient(DynamoDbProperties properties) {
        var builder = DynamoDbAsyncClient.builder();
        
        if (properties.getRegion() != null) {
            builder.region(software.amazon.awssdk.regions.Region.of(properties.getRegion()));
        }
        
        if (properties.getEndpoint() != null) {
            builder.endpointOverride(java.net.URI.create(properties.getEndpoint()));
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
    public DynamoDbService<?> dynamoDbService(DynamoDbEnhancedAsyncClient enhancedClient) {
        return new DefaultDynamoDbService<>(enhancedClient);
    }
}