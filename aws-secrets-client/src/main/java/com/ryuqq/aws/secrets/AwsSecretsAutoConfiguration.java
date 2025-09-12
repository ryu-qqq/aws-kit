package com.ryuqq.aws.secrets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqq.aws.commons.properties.AwsProperties;
import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import com.ryuqq.aws.secrets.properties.SecretsProperties;
import com.ryuqq.aws.secrets.service.ParameterStoreService;
import com.ryuqq.aws.secrets.service.SecretsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClientBuilder;
import software.amazon.awssdk.services.ssm.SsmAsyncClient;
import software.amazon.awssdk.services.ssm.SsmAsyncClientBuilder;

import java.net.URI;

/**
 * Spring Boot auto-configuration for AWS Secrets Manager and Parameter Store
 */
@Slf4j
@AutoConfiguration
@RequiredArgsConstructor
@ConditionalOnClass({SecretsManagerAsyncClient.class, SsmAsyncClient.class})
@ConditionalOnProperty(prefix = "aws.secrets", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SecretsProperties.class, AwsProperties.class})
public class AwsSecretsAutoConfiguration {
    
    private final SecretsProperties secretsProperties;
    private final AwsProperties awsProperties;
    
    @Bean
    @ConditionalOnMissingBean
    public SecretsManagerAsyncClient secretsManagerAsyncClient(AwsCredentialsProvider credentialsProvider) {
        log.info("Initializing Secrets Manager async client");
        
        SecretsManagerAsyncClientBuilder builder = SecretsManagerAsyncClient.builder();
        
        // Configure region
        String region = secretsProperties.getRegion() != null ? 
                       secretsProperties.getRegion() : awsProperties.getRegion();
        builder.region(Region.of(region));
        
        // Configure credentials
        builder.credentialsProvider(credentialsProvider);
        
        // Configure endpoint (for LocalStack)
        if (secretsProperties.getSecretsManagerEndpoint() != null) {
            builder.endpointOverride(URI.create(secretsProperties.getSecretsManagerEndpoint()));
            log.info("Using custom Secrets Manager endpoint: {}", 
                    secretsProperties.getSecretsManagerEndpoint());
        }
        
        // HTTP client configuration is handled by AWS SDK defaults
        
        // Use AWS SDK default retry configuration
        
        SecretsManagerAsyncClient client = builder.build();
        log.info("Secrets Manager async client initialized successfully");
        
        return client;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SsmAsyncClient ssmAsyncClient(AwsCredentialsProvider credentialsProvider) {
        log.info("Initializing SSM (Parameter Store) async client");
        
        SsmAsyncClientBuilder builder = SsmAsyncClient.builder();
        
        // Configure region
        String region = secretsProperties.getRegion() != null ? 
                       secretsProperties.getRegion() : awsProperties.getRegion();
        builder.region(Region.of(region));
        
        // Configure credentials
        builder.credentialsProvider(credentialsProvider);
        
        // Configure endpoint (for LocalStack)
        if (secretsProperties.getSsmEndpoint() != null) {
            builder.endpointOverride(URI.create(secretsProperties.getSsmEndpoint()));
            log.info("Using custom SSM endpoint: {}", secretsProperties.getSsmEndpoint());
        }
        
        // HTTP client configuration is handled by AWS SDK defaults
        
        // Use AWS SDK default retry configuration
        
        SsmAsyncClient client = builder.build();
        log.info("SSM async client initialized successfully");
        
        return client;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SecretsCacheManager secretsCacheManager() {
        SecretsProperties.CacheConfig cacheConfig = secretsProperties.getCache();
        
        log.info("Creating secrets cache manager with TTL: {}, max size: {}, enabled: {}", 
                cacheConfig.getTtl(), cacheConfig.getMaximumSize(), cacheConfig.isEnabled());
        
        return new SecretsCacheManager(
                cacheConfig.getTtl(),
                cacheConfig.getMaximumSize(),
                cacheConfig.isEnabled()
        );
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SecretsService secretsService(
            SecretsManagerAsyncClient secretsManagerClient,
            SecretsCacheManager cacheManager,
            ObjectMapper objectMapper) {
        
        log.info("Creating Secrets Manager service");
        return new SecretsService(secretsManagerClient, cacheManager, objectMapper);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ParameterStoreService parameterStoreService(
            SsmAsyncClient ssmClient,
            SecretsCacheManager cacheManager) {
        
        log.info("Creating Parameter Store service");
        return new ParameterStoreService(ssmClient, cacheManager);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create();
    }
    
}