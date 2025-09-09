package com.ryuqq.aws.commons.config;

import com.ryuqq.aws.commons.properties.AwsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.metrics.MetricPublisher;
import software.amazon.awssdk.regions.Region;

import java.time.Duration;

/**
 * AWS SDK v2 클라이언트 공통 설정 베이스 클래스
 * 모든 AWS 서비스 클라이언트 설정의 기반이 되는 설정
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseAwsClientConfiguration {

    protected final AwsProperties awsProperties;
    protected final Region region;
    protected final AwsCredentialsProvider credentialsProvider;

    /**
     * 동기 HTTP 클라이언트 설정 (Apache HTTP Client)
     */
    protected SdkHttpClient createSyncHttpClient() {
        AwsProperties.ClientConfig config = awsProperties.getClientConfig();
        
        return ApacheHttpClient.builder()
                .connectionTimeout(config.getConnectionTimeout())
                .socketTimeout(config.getSocketTimeout())
                .maxConnections(config.getMaxConcurrency())
                .connectionTimeToLive(Duration.ofMinutes(5))
                .connectionMaxIdleTime(Duration.ofSeconds(60))
                .tcpKeepAlive(true)
                .build();
    }

    /**
     * 비동기 HTTP 클라이언트 설정 (Netty NIO)
     */
    protected SdkAsyncHttpClient createAsyncHttpClient() {
        AwsProperties.ClientConfig config = awsProperties.getClientConfig();
        
        NettyNioAsyncHttpClient.Builder builder = NettyNioAsyncHttpClient.builder()
                .connectionTimeout(config.getConnectionTimeout())
                .readTimeout(config.getSocketTimeout())
                .writeTimeout(config.getSocketTimeout())
                .maxConcurrency(config.getMaxConcurrency())
                .maxPendingConnectionAcquires(config.getMaxPendingConnectionAcquires())
                .connectionTimeToLive(Duration.ofMinutes(5))
                .connectionMaxIdleTime(Duration.ofSeconds(60))
                .useIdleConnectionReaper(true);

        if (config.isUseHttp2()) {
            builder.protocol(software.amazon.awssdk.http.Protocol.HTTP2);
        }

        return builder.build();
    }

    /**
     * 클라이언트 오버라이드 설정
     */
    protected ClientOverrideConfiguration createClientOverrideConfiguration() {
        AwsProperties.RetryPolicy retryConfig = awsProperties.getRetryPolicy();
        
        RetryPolicy retryPolicy = createRetryPolicy(retryConfig);
        
        ClientOverrideConfiguration.Builder builder = ClientOverrideConfiguration.builder()
                .apiCallTimeout(awsProperties.getClientConfig().getApiCallTimeout())
                .apiCallAttemptTimeout(awsProperties.getClientConfig().getApiCallAttemptTimeout())
                .retryPolicy(retryPolicy)
                .putAdvancedOption(
                    software.amazon.awssdk.core.client.config.SdkAdvancedClientOption.USER_AGENT_PREFIX,
                    awsProperties.getClientConfig().getUserAgentPrefix()
                );

        // 메트릭 퍼블리셔 추가
        MetricPublisher metricPublisher = createMetricPublisher();
        if (metricPublisher != null) {
            builder.addMetricPublisher(metricPublisher);
        }

        return builder.build();
    }

    /**
     * 재시도 정책 생성
     */
    protected RetryPolicy createRetryPolicy(AwsProperties.RetryPolicy retryConfig) {
        RetryPolicy.Builder builder = RetryPolicy.builder()
                .numRetries(retryConfig.getMaxRetries());

        // 재시도 조건 설정
        if (retryConfig.isRetryOnThrottling()) {
            builder.retryCondition(RetryCondition.defaultRetryCondition());
        } else {
            builder.retryCondition(RetryCondition.none());
        }

        // 백오프 전략 설정 - 기본 전략 사용
        builder.backoffStrategy(BackoffStrategy.defaultStrategy());
        builder.throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy());

        return builder.build();
    }

    /**
     * 메트릭 퍼블리셔 생성 (서브클래스에서 오버라이드 가능)
     */
    protected MetricPublisher createMetricPublisher() {
        // CloudWatch 메트릭 퍼블리셔 또는 커스텀 메트릭 퍼블리셔 구현
        return null;
    }

    /**
     * 연결 풀 설정 정보 로깅
     */
    protected void logConnectionPoolSettings() {
        AwsProperties.ClientConfig config = awsProperties.getClientConfig();
        log.info("AWS Client Connection Pool Settings:");
        log.info("  Max Connections: {}", config.getMaxConcurrency());
        log.info("  Connection Timeout: {}", config.getConnectionTimeout());
        log.info("  Socket Timeout: {}", config.getSocketTimeout());
        log.info("  Max Pending Connection Acquires: {}", config.getMaxPendingConnectionAcquires());
        log.info("  HTTP/2 Enabled: {}", config.isUseHttp2());
    }
}