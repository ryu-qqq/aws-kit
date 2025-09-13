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
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;

/**
 * Lambda 클라이언트 자동 설정
 * 
 * Spring Boot의 자동 설정 기능을 사용하여 AWS Lambda 클라이언트를 자동으로 구성합니다.
 * aws-sdk-commons 모듈에서 제공하는 공통 AWS 설정을 기반으로 Lambda 특화 설정을 적용합니다.
 * 
 * 자동 설정 조건:
 * 1. LambdaAsyncClient 클래스가 클래스패스에 존재
 * 2. Region과 AwsCredentialsProvider 빈이 존재 (aws-sdk-commons에서 제공)
 * 3. LambdaProperties 설정이 활성화됨
 * 
 * 생성되는 빈:
 * - LambdaAsyncClient: AWS SDK Lambda 비동기 클라이언트
 * - LambdaService: 고수준 Lambda 서비스 인터페이스
 * 
 * 설정 우선순위:
 * 1. 사용자 정의 빈 (ConditionalOnMissingBean으로 보호)
 * 2. 이 자동 설정의 기본 구성
 * 3. AWS SDK의 기본값
 */
@AutoConfiguration
@ConditionalOnClass(LambdaAsyncClient.class)
@EnableConfigurationProperties(LambdaProperties.class)
public class AwsLambdaAutoConfiguration {

    /**
     * AWS Lambda 비동기 클라이언트 빈 생성
     * 
     * AWS SDK의 LambdaAsyncClient를 생성하고 성능 최적화 설정을 적용합니다.
     * 내부적으로 HTTP/2 연결 풀링과 비동기 I/O를 사용합니다.
     * 
     * 적용되는 설정:
     * - 재시도 정책: 지수적 백오프와 함께 설정된 횟수만큼 재시도
     * - API 호출 타임아웃: Lambda 함수의 최대 실행 시간 설정
     * - 리전과 인증 정보: 공통 AWS 설정에서 주입
     * 
     * @param region AWS 리전 (aws-sdk-commons에서 제공)
     * @param credentialsProvider AWS 인증 정보 제공자 (aws-sdk-commons에서 제공)
     * @param lambdaProperties Lambda 특화 설정 프로퍼티
     * @return 설정이 적용된 Lambda 비동기 클라이언트
     */
    @Bean
    @ConditionalOnMissingBean
    public LambdaAsyncClient lambdaAsyncClient(Region region,
                                             AwsCredentialsProvider credentialsProvider,
                                             LambdaProperties lambdaProperties) {
        // 클라이언트 수준에서 재시도 정책 설정
        // 애플리케이션 수준 재시도와 구분하여 저수준 네트워크 오류 처리
        ClientOverrideConfiguration overrideConfig = ClientOverrideConfiguration.builder()
                .retryStrategy(RetryMode.STANDARD)
                .apiCallTimeout(lambdaProperties.timeout())             // API 호출 타임아웃
                .build();
        
        // Lambda 비동기 클라이언트 빌더 패턴으로 생성
        // 모든 Lambda 호출이 이 클라이언트를 통해 처리됨
        return LambdaAsyncClient.builder()
                .region(region)                                           // AWS 리전 설정
                .credentialsProvider(credentialsProvider)                // 인증 정보 제공자
                .overrideConfiguration(overrideConfig)                   // 커스텀 설정 적용
                .build();
    }

    /**
     * Lambda 서비스 빈 생성
     * 
     * 고수준 Lambda 서비스 인터페이스를 구현한 DefaultLambdaService 빈을 생성합니다.
     * 이 빈이 실제 애플리케이션에서 사용하는 주요 인터페이스입니다.
     * 
     * 제공 기능:
     * - 동기/비동기 Lambda 함수 호출
     * - 자동 재시도 메커니즘
     * - 에러 분류 및 처리
     * - CompletableFuture 기반 비동기 처리
     * 
     * @param lambdaAsyncClient 설정된 Lambda 비동기 클라이언트
     * @param lambdaProperties Lambda 설정 프로퍼티
     * @return Lambda 서비스 구현체
     */
    @Bean
    @ConditionalOnMissingBean
    public LambdaService lambdaService(LambdaAsyncClient lambdaAsyncClient, LambdaProperties lambdaProperties) {
        return new DefaultLambdaService(lambdaAsyncClient, lambdaProperties);
    }

}