package com.ryuqq.aws.sqs;

import com.ryuqq.aws.sqs.properties.SqsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * AWS SQS 라이브러리의 Spring Boot 자동 구성 클래스
 * 
 * <p>Spring Boot 애플리케이션에서 SQS 기능을 사용하기 위해 필요한 
 * Bean들을 자동으로 등록합니다.</p>
 * 
 * <h3>자동 구성 조건:</h3>
 * <ul>
 *   <li>AWS SDK SqsAsyncClient 클래스가 클래스패스에 있어야 함</li>
 *   <li>aws-sdk-commons 모듈에서 공통 AWS 설정이 제공되어야 함</li>
 * </ul>
 * 
 * <h3>생성되는 Bean:</h3>
 * <ul>
 *   <li>SqsAsyncClient: AWS SQS 비동기 클라이언트</li>
 *   <li>SqsService: SQS 서비스 래퍼 클래스</li>
 * </ul>
 * 
 * @since 1.0.0
 * @see SqsProperties SQS 관련 설정 프로퍼티
 */
@AutoConfiguration
@ConditionalOnClass(SqsAsyncClient.class)
@EnableConfigurationProperties(SqsProperties.class)
public class AwsSqsAutoConfiguration {

    /**
     * AWS SQS 비동기 클라이언트 Bean을 생성합니다.
     * 
     * <p>aws-sdk-commons 모듈에서 제공하는 공통 AWS 설정을 사용하여
     * SQS 비동기 클라이언트를 구성합니다.</p>
     * 
     * <h4>사용되는 공통 설정:</h4>
     * <ul>
     *   <li>Region: AWS 리전 설정</li>
     *   <li>AwsCredentialsProvider: AWS 인증 정보</li>
     *   <li>ClientOverrideConfiguration: 클라이언트 공통 설정 (타임아웃, 재시도 등)</li>
     * </ul>
     * 
     * @param region AWS 리전 설정
     * @param credentialsProvider AWS 인증 정보 제공자
     * @param clientOverrideConfiguration 클라이언트 공통 설정
     * @return 구성된 SqsAsyncClient 인스턴스
     */
    @Bean
    @ConditionalOnMissingBean
    public SqsAsyncClient sqsAsyncClient(Region region,
                                       AwsCredentialsProvider credentialsProvider,
                                       ClientOverrideConfiguration clientOverrideConfiguration) {
        return SqsAsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(clientOverrideConfiguration)
                .build();
    }
}