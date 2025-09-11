package com.ryuqq.aws.lambda.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lambda 클라이언트 설정 프로퍼티
 * 함수 호출에 필요한 필수 설정만 포함
 */
@Data
@Component
@ConfigurationProperties(prefix = "aws.lambda")
public class LambdaProperties {

    /**
     * 함수 호출 타임아웃 설정
     */
    private Duration timeout = Duration.ofMinutes(15);

    /**
     * 재시도 최대 횟수
     */
    private int maxRetries = 3;

    /**
     * 최대 동시 실행 수
     */
    private int maxConcurrentInvocations = 10;
}
