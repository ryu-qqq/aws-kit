package com.ryuqq.aws.s3;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

/**
 * S3 클라이언트 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(S3AsyncClient.class)
public class AwsS3AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public S3AsyncClient s3AsyncClient(Region region,
                                     AwsCredentialsProvider credentialsProvider,
                                     ClientOverrideConfiguration clientOverrideConfiguration,
                                     NettyNioAsyncHttpClient httpClient) {
        return S3AsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(clientOverrideConfiguration)
                .httpClient(httpClient)
                .build();
    }
}