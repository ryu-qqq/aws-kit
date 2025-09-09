package com.ryuqq.aws.commons.config;

import com.ryuqq.aws.commons.properties.AwsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;

/**
 * AWS Client 공통 설정
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(AwsProperties.class)
public class AwsClientConfig {

    private final AwsProperties awsProperties;

    public AwsClientConfig(AwsProperties awsProperties) {
        this.awsProperties = awsProperties;
    }

    @Bean
    public Region awsRegion() {
        Region region = Region.of(awsProperties.getRegion());
        log.info("AWS Region configured: {}", region);
        return region;
    }

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        AwsProperties.Credentials creds = awsProperties.getCredentials();
        
        AwsCredentialsProviderChain.Builder builder = AwsCredentialsProviderChain.builder();

        // 1. 환경변수 또는 시스템 프로퍼티에서 자격증명 확인
        if (creds.getAccessKeyId() != null && creds.getSecretAccessKey() != null) {
            log.info("Using static AWS credentials");
            
            if (creds.getSessionToken() != null) {
                builder.addCredentialsProvider(StaticCredentialsProvider.create(
                        AwsSessionCredentials.create(
                                creds.getAccessKeyId(), 
                                creds.getSecretAccessKey(), 
                                creds.getSessionToken())));
            } else {
                builder.addCredentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(creds.getAccessKeyId(), creds.getSecretAccessKey())));
            }
        }

        // 2. 프로파일에서 자격증명 확인
        if (creds.getProfileName() != null) {
            log.info("Adding profile credentials provider: {}", creds.getProfileName());
            builder.addCredentialsProvider(ProfileCredentialsProvider.create(creds.getProfileName()));
        }

        // 3. Instance Profile/Container Credentials (기본값)
        if (creds.isUseInstanceProfile()) {
            log.info("Adding instance profile credentials provider");
            builder.addCredentialsProvider(InstanceProfileCredentialsProvider.create())
                   .addCredentialsProvider(ContainerCredentialsProvider.builder().build());
        }

        // 4. 환경변수 자격증명 (최종 fallback)
        builder.addCredentialsProvider(EnvironmentVariableCredentialsProvider.create())
               .addCredentialsProvider(SystemPropertyCredentialsProvider.create());

        return builder.build();
    }

    @Bean
    public ClientOverrideConfiguration clientOverrideConfiguration() {
        AwsProperties.RetryPolicy retryConfig = awsProperties.getRetryPolicy();
        
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .numRetries(retryConfig.getMaxRetries())
                .retryCondition(RetryCondition.defaultRetryCondition())
                .backoffStrategy(BackoffStrategy.defaultStrategy())
                .throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy())
                .build();

        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(awsProperties.getClientConfig().getApiCallTimeout())
                .apiCallAttemptTimeout(awsProperties.getClientConfig().getApiCallAttemptTimeout())
                .retryPolicy(retryPolicy)
                .putAdvancedOption(software.amazon.awssdk.core.client.config.SdkAdvancedClientOption.USER_AGENT_PREFIX, 
                                 awsProperties.getClientConfig().getUserAgentPrefix())
                .build();
    }

    @Bean
    public SdkHttpClient apacheHttpClient() {
        AwsProperties.ClientConfig clientConfig = awsProperties.getClientConfig();
        
        return ApacheHttpClient.builder()
                .connectionTimeout(clientConfig.getConnectionTimeout())
                .socketTimeout(clientConfig.getSocketTimeout())
                .maxConnections(clientConfig.getMaxConcurrency())
                .build();
    }

    @Bean
    public SdkAsyncHttpClient nettyNioAsyncHttpClient() {
        AwsProperties.ClientConfig clientConfig = awsProperties.getClientConfig();
        
        NettyNioAsyncHttpClient.Builder builder = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(clientConfig.getConnectionTimeout())
                .readTimeout(clientConfig.getSocketTimeout())
                .maxConcurrency(clientConfig.getMaxConcurrency())
                .maxPendingConnectionAcquires(clientConfig.getMaxPendingConnectionAcquires());

        if (clientConfig.isUseHttp2()) {
            builder.protocol(software.amazon.awssdk.http.Protocol.HTTP2);
        }

        return builder.build();
    }
}