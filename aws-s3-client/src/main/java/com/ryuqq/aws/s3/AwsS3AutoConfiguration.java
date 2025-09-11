package com.ryuqq.aws.s3;

import com.ryuqq.aws.s3.properties.S3Properties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * S3 클라이언트 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(S3AsyncClient.class)
@EnableConfigurationProperties(S3Properties.class)
public class AwsS3AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public S3AsyncClient s3AsyncClient(Region region,
                                     AwsCredentialsProvider credentialsProvider,
                                     ClientOverrideConfiguration clientOverrideConfiguration) {
        return S3AsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(clientOverrideConfiguration)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(Region region,
                                  AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3TransferManager s3TransferManager(S3AsyncClient s3AsyncClient,
                                             S3Properties s3Properties) {
        return S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();
    }
}