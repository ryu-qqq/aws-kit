package com.ryuqq.aws.sns;

import com.ryuqq.aws.commons.properties.AwsProperties;
import com.ryuqq.aws.sns.adapter.SnsTypeAdapter;
import com.ryuqq.aws.sns.properties.SnsProperties;
import com.ryuqq.aws.sns.service.SnsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.SnsAsyncClientBuilder;

import java.net.URI;

/**
 * Spring Boot auto-configuration for AWS SNS
 */
@AutoConfiguration
@ConditionalOnClass(SnsAsyncClient.class)
@ConditionalOnProperty(prefix = "aws.sns", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SnsProperties.class, AwsProperties.class})
public class AwsSnsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AwsSnsAutoConfiguration.class);

    private final SnsProperties snsProperties;
    private final AwsProperties awsProperties;

    public AwsSnsAutoConfiguration(SnsProperties snsProperties, AwsProperties awsProperties) {
        this.snsProperties = snsProperties;
        this.awsProperties = awsProperties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SnsAsyncClient snsAsyncClient(AwsCredentialsProvider credentialsProvider) {
        log.info("Initializing SNS async client");
        
        SnsAsyncClientBuilder builder = SnsAsyncClient.builder();
        
        // Configure region
        String region = snsProperties.region() != null ? 
                       snsProperties.region() : awsProperties.getRegion();
        builder.region(Region.of(region));
        
        // Configure credentials
        builder.credentialsProvider(credentialsProvider);
        
        // Configure endpoint (for LocalStack)
        if (snsProperties.endpoint() != null) {
            builder.endpointOverride(URI.create(snsProperties.endpoint()));
            log.info("Using custom SNS endpoint: {}", snsProperties.endpoint());
        }
        
        // HTTP client configuration is handled by AWS SDK defaults
        
        // Use AWS SDK default retry configuration
        
        SnsAsyncClient client = builder.build();
        log.info("SNS async client initialized successfully");
        
        return client;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SnsTypeAdapter snsTypeAdapter() {
        return new SnsTypeAdapter();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SnsService snsService(SnsAsyncClient snsAsyncClient, SnsTypeAdapter snsTypeAdapter) {
        log.info("Creating SNS service");
        return new SnsService(snsAsyncClient, snsTypeAdapter);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create();
    }
    
}