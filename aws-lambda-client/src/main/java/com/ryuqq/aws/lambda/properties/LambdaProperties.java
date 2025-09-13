package com.ryuqq.aws.lambda.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lambda 클라이언트 설정 프로퍼티
 * 함수 호출에 필요한 필수 설정만 포함
 * 
 * AWS Lambda 클라이언트의 동작을 제어하는 설정 클래스입니다.
 * application.yml에서 'aws.lambda' 접두사로 설정을 바인딩합니다.
 * 
 * 설정 예시 (application.yml):
 * <pre>
 * aws:
 *   lambda:
 *     timeout: PT5M          # 5분 타임아웃
 *     max-retries: 5         # 최대 5회 재시도
 *     max-concurrent-invocations: 20  # 동시 실행 20개
 * </pre>
 * 
 * 주요 고려사항:
 * - timeout: Lambda 함수의 최대 실행 시간은 15분
 * - maxRetries: 너무 높으면 응답 시간 지연, 너무 낮으면 일시적 장애에 취약
 * - maxConcurrentInvocations: AWS 계정의 동시 실행 한도와 연관
 */
@Component
@ConfigurationProperties(prefix = "aws.lambda")
public record LambdaProperties(

    /**
     * 함수 호출 타임아웃 설정
     * 
     * Lambda 함수 호출시 최대 대기 시간을 설정합니다.
     * AWS Lambda의 최대 실행 시간은 15분이므로 이를 초과할 수 없습니다.
     * 
     * 기본값: 15분 (AWS Lambda 최대 실행 시간)
     * 권장값: 함수의 평균 실행 시간 + 여유시간
     * 
     * 고려사항:
     * - 너무 짧으면 정상적인 장시간 실행 함수도 타임아웃
     * - 너무 길면 장애 상황에서 빠른 실패 처리 불가
     * - 함수별로 다른 타임아웃이 필요한 경우 별도 설정 고려
     */
    Duration timeout,

    /**
     * 최대 동시 실행 수
     * 
     * 애플리케이션에서 동시에 실행할 수 있는 Lambda 호출의 최대 개수입니다.
     * AWS 계정 레벨의 동시 실행 한도와는 별개의 애플리케이션 레벨 제한입니다.
     * 
     * 기본값: 10개
     * 권장값: 시스템 요구사항과 AWS 한도에 따라 조정
     * 
     * 고려사항:
     * - AWS 계정의 기본 동시 실행 한도: 1000개 (리전별)
     * - 너무 높으면: AWS 한도 초과, 메모리 사용량 증가
     * - 너무 낮으면: 처리량 저하, 병목 현상
     * - 함수별 예약된 동시성과 충돌 가능
     * 
     * 모니터링 지표:
     * - CloudWatch의 ConcurrentExecutions 메트릭 확인
     * - Throttles 메트릭으로 한도 초과 확인
     * - Duration 메트릭으로 성능 영향 확인
     */
    int maxConcurrentInvocations,

    /**
     * 배치 호출시 기본 타임아웃 (밀리초)
     * 
     * invokeBatch(), invokeMultiple() 메서드에서 사용할 기본 타임아웃 값입니다.
     * 개별 배치 요청에서 타임아웃을 지정하지 않으면 이 값이 사용됩니다.
     * 
     * 기본값: 900,000ms (15분 - Lambda 최대 실행 시간)
     * 권장 범위: 30,000ms (30초) ~ 900,000ms (15분)
     * 
     * 설정 고려사항:
     * - 개별 Lambda 함수의 예상 실행 시간
     * - 배치 크기와 동시성 설정
     * - 네트워크 지연 및 재시도 시간
     * - 사용자 경험 vs 완전성 요구사항
     */
    long defaultBatchTimeoutMs,

    /**
     * 배치 호출시 기본 재시도 정책
     * 
     * 배치 호출에서 개별 Lambda 호출이 실패했을 때의 기본 재시도 동작입니다.
     * 
     * NONE: 재시도 없음 (기본값)
     * INDIVIDUAL: 각 Lambda 호출의 개별 재시도 설정 사용  
     * BATCH_LEVEL: 배치 레벨에서 통합 재시도 정책 적용
     */
    String defaultRetryPolicy,

    /**
     * 상관관계 ID 자동 생성 여부
     * 
     * true: Lambda 호출시 상관관계 ID가 없으면 자동으로 UUID 생성
     * false: 상관관계 ID를 명시적으로 설정해야 함
     * 
     * 기본값: true (자동 생성)
     * 
     * 자동 생성 형식: "lambda-" + UUID
     * 예시: "lambda-550e8400-e29b-41d4-a716-446655440000"
     */
    boolean autoGenerateCorrelationId
) {
    
    public LambdaProperties {
        timeout = timeout != null ? timeout : Duration.ofMinutes(15);
        maxConcurrentInvocations = maxConcurrentInvocations == 0 ? 10 : maxConcurrentInvocations;
        defaultBatchTimeoutMs = defaultBatchTimeoutMs == 0 ? 900_000L : defaultBatchTimeoutMs;
        defaultRetryPolicy = defaultRetryPolicy != null ? defaultRetryPolicy : "NONE";
        // autoGenerateCorrelationId는 설정되지 않으면 true가 기본값 (자동 생성 활성화)
    }
}
