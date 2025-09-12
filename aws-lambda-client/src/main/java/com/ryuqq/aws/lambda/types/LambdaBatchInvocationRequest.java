package com.ryuqq.aws.lambda.types;

import lombok.Builder;
import lombok.Data;
import lombok.Singular;

import java.util.List;

/**
 * Lambda 함수 배치 호출 요청을 위한 타입 추상화 클래스
 * 
 * 여러 Lambda 함수를 동시에 호출하거나 동일한 함수를 여러 다른 페이로드로
 * 호출할 때 사용하는 배치 요청 설정입니다.
 * 
 * 주요 기능:
 * - 다중 함수 동시 호출
 * - 동일 함수의 다중 페이로드 처리
 * - 배치 처리 최적화 설정
 * - 실패 처리 정책 설정
 * - 결과 집계 방식 설정
 * 
 * 사용 예시:
 * <pre>
 * {@code
 * // 동일 함수, 다른 페이로드들
 * LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
 *     .batchId("batch-001")
 *     .request(LambdaInvocationRequest.builder()
 *         .functionName("data-processor")
 *         .payload("{\"userId\":1}")
 *         .correlationId("req-001")
 *         .build())
 *     .request(LambdaInvocationRequest.builder()
 *         .functionName("data-processor") 
 *         .payload("{\"userId\":2}")
 *         .correlationId("req-002")
 *         .build())
 *     .maxConcurrency(5)
 *     .failFast(false)
 *     .build();
 * 
 * CompletableFuture<List<LambdaInvocationResponse>> results = 
 *     lambdaService.invokeBatch(batchRequest);
 * 
 * // 다중 함수 호출
 * LambdaBatchInvocationRequest multiFunctionBatch = LambdaBatchInvocationRequest.builder()
 *     .batchId("multi-batch-001")
 *     .request(LambdaInvocationRequest.builder()
 *         .functionName("user-service")
 *         .payload("{\"action\":\"getProfile\",\"userId\":123}")
 *         .build())
 *     .request(LambdaInvocationRequest.builder()
 *         .functionName("order-service")
 *         .payload("{\"action\":\"getOrders\",\"userId\":123}")
 *         .build())
 *     .request(LambdaInvocationRequest.builder()
 *         .functionName("notification-service")
 *         .payload("{\"action\":\"getPreferences\",\"userId\":123}")
 *         .build())
 *     .maxConcurrency(3)
 *     .failFast(true)
 *     .build();
 * }
 * </pre>
 */
@Data
@Builder
public class LambdaBatchInvocationRequest {
    
    /**
     * 배치 작업 고유 식별자
     * 
     * 배치 작업을 추적하고 로깅하기 위한 고유 ID입니다.
     * 분산 시스템에서 배치 작업을 추적하고 결과를 연관시키는데 사용됩니다.
     * 
     * 권장 형식:
     * - 타임스탬프 기반: "batch-20231201-143022-001"
     * - UUID: "batch-550e8400-e29b-41d4-a716-446655440000"
     * - 계층적: "user-data-processing-batch-001"
     * - 작업별: "daily-report-batch-2023-12-01"
     * 
     * 용도:
     * - 로그 집계 및 검색
     * - 배치 작업 진행 상황 추적
     * - 에러 디버깅 및 분석
     * - 성능 메트릭 수집
     * - 재처리 작업 식별
     * 
     * 로깅 예시:
     * log.info("Starting batch processing [batchId={}] with {} requests", 
     *          request.getBatchId(), request.getRequests().size());
     */
    private final String batchId;
    
    /**
     * 개별 Lambda 호출 요청 목록
     * 
     * 배치로 처리할 Lambda 함수 호출 요청들의 리스트입니다.
     * 각 요청은 독립적으로 처리되며, 서로 다른 함수이거나 동일한 함수일 수 있습니다.
     * 
     * @singular 어노테이션을 통해 빌더에서 개별 요청을 하나씩 추가할 수 있습니다.
     * 
     * 제약 사항:
     * - 최소 1개 이상의 요청 필요
     * - 실용적 최대치: 100-1000개 (메모리 및 성능 고려)
     * - AWS Lambda 동시 실행 한도 고려 필요
     * 
     * 배치 구성 전략:
     * - 동질적 배치: 동일한 함수, 유사한 처리 시간
     * - 이질적 배치: 다른 함수들, 다른 처리 특성
     * - 크기별 배치: 페이로드 크기가 비슷한 것끼리 그룹핑
     * - 우선순위별 배치: 중요도가 비슷한 작업끼리 그룹핑
     */
    @Singular
    private final List<LambdaInvocationRequest> requests;
    
    /**
     * 최대 동시 실행 수
     * 
     * 배치 내에서 동시에 실행할 수 있는 Lambda 함수의 최대 개수입니다.
     * 시스템 리소스와 다운스트림 서비스 보호를 위한 제한입니다.
     * 
     * 기본값: 10
     * 권장 범위: 1-50 (시스템 환경에 따라 조정)
     * 
     * 설정 고려사항:
     * 
     * 1. AWS Lambda 한도:
     *    - 계정별 기본 동시 실행 한도: 1000개 (리전별)
     *    - 함수별 예약 동시성 설정 확인
     *    - 다른 애플리케이션과의 리소스 공유 고려
     * 
     * 2. 다운스트림 서비스 용량:
     *    - 데이터베이스 연결 풀 크기
     *    - 외부 API 호출 한도
     *    - 네트워크 대역폭 제한
     * 
     * 3. 메모리 및 CPU:
     *    - 클라이언트 애플리케이션의 메모리 사용량
     *    - CompletableFuture 스레드 풀 크기
     *    - GC 압박 여부
     * 
     * 4. 비용 최적화:
     *    - 동시 실행 수 증가 = 더 빠른 처리 = 더 높은 순간 비용
     *    - 처리 시간 vs 동시성 비용의 균형점 찾기
     * 
     * 성능 튜닝:
     * - 처리 시간이 긴 함수: 높은 동시성 (I/O 대기 시간 활용)
     * - CPU 집약적 함수: 낮은 동시성 (CPU 경합 방지)
     * - 메모리 집약적 함수: 중간 동시성 (메모리 압박 방지)
     */
    @Builder.Default
    private final int maxConcurrency = 10;
    
    /**
     * 빠른 실패 모드 활성화 여부
     * 
     * true: 첫 번째 함수 실행 실패시 남은 모든 요청 취소
     * false: 실패한 함수가 있어도 모든 요청 계속 처리
     * 
     * 기본값: false (모든 요청 처리)
     * 
     * 빠른 실패 모드 (failFast = true) 사용 시나리오:
     * 
     * 1. 의존성이 강한 작업들:
     *    - 순차적 데이터 처리 파이프라인
     *    - 이전 단계 실패시 다음 단계 의미 없음
     *    - 트랜잭션적 일관성이 중요한 작업
     * 
     * 2. 비용 최적화가 중요한 경우:
     *    - 실패 확정시 불필요한 Lambda 호출 비용 절약
     *    - 리소스 낭비 방지
     *    - 빠른 에러 응답 필요
     * 
     * 3. 실시간 처리:
     *    - 실패시 즉시 대안 로직 실행
     *    - 사용자 대기 시간 최소화
     * 
     * 완료 모드 (failFast = false) 사용 시나리오:
     * 
     * 1. 독립적인 작업들:
     *    - 각 요청이 독립적으로 처리 가능
     *    - 부분적 성공도 의미 있는 경우
     *    - 배치 데이터 처리
     * 
     * 2. 최대한 많은 작업 완료가 목표:
     *    - ETL 작업의 데이터 처리
     *    - 알림 발송 (일부 실패해도 나머지는 발송)
     *    - 보고서 생성 (부분적 데이터라도 유용)
     * 
     * 3. 실패 분석이 중요한 경우:
     *    - 어떤 요청들이 실패했는지 전체 패턴 파악
     *    - 실패율 분석 및 시스템 개선
     */
    @Builder.Default
    private final boolean failFast = false;
    
    /**
     * 배치 처리 타임아웃 (밀리초)
     * 
     * 전체 배치 작업의 최대 대기 시간입니다.
     * 이 시간을 초과하면 완료되지 않은 모든 요청이 취소됩니다.
     * 
     * 기본값: 900,000ms (15분 - Lambda 최대 실행 시간)
     * 권장 범위: 30,000ms (30초) ~ 900,000ms (15분)
     * 
     * 타임아웃 설정 고려사항:
     * 
     * 1. 개별 함수 실행 시간:
     *    - 가장 오래 걸리는 함수의 예상 실행 시간
     *    - 네트워크 지연 및 콜드 스타트 시간 고려
     *    - 여유시간 추가 (예상 시간의 1.5-2배)
     * 
     * 2. 동시성과 처리 시간:
     *    - maxConcurrency가 낮으면 순차 처리로 인한 지연
     *    - 대기열 크기 = requests.size() / maxConcurrency
     *    - 총 예상 시간 = (대기열 크기) × (평균 함수 실행 시간)
     * 
     * 3. 사용자 경험:
     *    - API 응답의 경우 사용자 허용 대기 시간
     *    - 배치 작업의 경우 업무 요구사항
     *    - 실시간성이 중요한지 완결성이 중요한지
     * 
     * 타임아웃 전략:
     * - 짧은 타임아웃: 빠른 실패, 사용자 경험 우선
     * - 긴 타임아웃: 완전한 처리, 데이터 무결성 우선
     * - 적응적 타임아웃: 배치 크기에 따라 동적 조정
     * 
     * 예시 설정:
     * - 실시간 API: 30초
     * - 배치 처리: 5-15분
     * - 대용량 처리: 15분 (Lambda 최대값)
     */
    @Builder.Default
    private final long timeoutMs = 900_000L; // 15분
    
    /**
     * 재시도 정책 설정
     * 
     * 개별 Lambda 호출이 실패했을 때의 재시도 동작을 제어합니다.
     * 
     * RetryPolicy.NONE (기본값):
     * - 재시도 없음, 첫 번째 실패시 즉시 실패 처리
     * - 가장 빠른 처리, 예측 가능한 실행 시간
     * - 중요하지 않은 작업이나 재시도가 무의미한 작업
     * 
     * RetryPolicy.INDIVIDUAL:
     * - 각 Lambda 호출의 설정된 재시도 정책 사용
     * - LambdaService.invokeWithRetry() 로직 적용
     * - 세밀한 재시도 제어 가능
     * 
     * RetryPolicy.BATCH_LEVEL:
     * - 배치 레벨에서 통합 재시도 정책 적용
     * - 실패한 요청들만 모아서 재시도
     * - 배치 전체의 일관된 재시도 동작
     * 
     * 재시도 정책 선택 가이드:
     * 
     * 1. NONE 사용 시나리오:
     *    - 실시간 API 응답 (빠른 응답이 중요)
     *    - 멱등성이 보장되지 않는 작업
     *    - 재시도해도 성공 확률이 낮은 작업
     * 
     * 2. INDIVIDUAL 사용 시나리오:
     *    - 각 함수마다 다른 재시도 요구사항
     *    - 함수별 최적화된 재시도 전략
     *    - 복잡한 에러 처리 로직
     * 
     * 3. BATCH_LEVEL 사용 시나리오:
     *    - 배치 전체의 일관성이 중요
     *    - 단순하고 일관된 재시도 정책
     *    - 배치 처리 진행 상황 추적 중요
     */
    @Builder.Default
    private final RetryPolicy retryPolicy = RetryPolicy.NONE;
    
    /**
     * 결과 집계 방식
     * 
     * 배치 처리 완료 후 결과를 어떻게 반환할지 결정합니다.
     * 
     * AggregationMode.ALL (기본값):
     * - 모든 요청의 결과를 요청 순서대로 반환
     * - 실패한 요청도 에러 정보와 함께 포함
     * - 가장 완전한 정보 제공
     * 
     * AggregationMode.SUCCESS_ONLY:
     * - 성공한 요청의 결과만 반환
     * - 실패한 요청은 제외
     * - 처리 성공한 데이터만 필요한 경우
     * 
     * AggregationMode.FAILURE_ONLY:
     * - 실패한 요청의 정보만 반환
     * - 에러 분석 및 재처리용
     * - 실패 케이스 분석시 유용
     * 
     * AggregationMode.SUMMARY:
     * - 성공/실패 개수와 요약 정보만 반환
     * - 메모리 효율적
     * - 대용량 배치 처리시 유용
     * 
     * 집계 방식 선택 가이드:
     * 
     * 1. ALL 사용 시나리오:
     *    - 모든 결과가 필요한 일반적인 경우
     *    - 디버깅 및 분석이 중요한 개발/테스트 환경
     *    - 배치 크기가 작거나 중간 규모 (< 1000개)
     * 
     * 2. SUCCESS_ONLY 사용 시나리오:
     *    - 성공한 데이터만 후속 처리하는 경우
     *    - 실패는 별도 처리 (DLQ, 로그 등)
     *    - 메모리 사용량 최적화 필요
     * 
     * 3. FAILURE_ONLY 사용 시나리오:
     *    - 에러 분석 및 재처리 전용 배치
     *    - 장애 복구 작업
     *    - 품질 관리 및 모니터링
     * 
     * 4. SUMMARY 사용 시나리오:
     *    - 대용량 배치 처리 (> 10,000개)
     *    - 진행 상황 모니터링만 필요
     *    - 메모리 제약이 있는 환경
     */
    @Builder.Default
    private final AggregationMode aggregationMode = AggregationMode.ALL;
    
    /**
     * 재시도 정책 열거형
     */
    public enum RetryPolicy {
        /**
         * 재시도 없음 - 첫 번째 실패시 즉시 실패 처리
         */
        NONE,
        
        /**
         * 개별 함수의 재시도 설정 사용
         */
        INDIVIDUAL,
        
        /**
         * 배치 레벨 재시도 정책 적용
         */
        BATCH_LEVEL
    }
    
    /**
     * 결과 집계 방식 열거형
     */
    public enum AggregationMode {
        /**
         * 모든 결과 반환 (성공 + 실패)
         */
        ALL,
        
        /**
         * 성공한 결과만 반환
         */
        SUCCESS_ONLY,
        
        /**
         * 실패한 결과만 반환
         */
        FAILURE_ONLY,
        
        /**
         * 요약 정보만 반환 (개수, 통계 등)
         */
        SUMMARY
    }
    
    /**
     * 배치 요청의 유효성 검증
     * 
     * @return 유효하면 true, 그렇지 않으면 false
     */
    public boolean isValid() {
        return batchId != null && !batchId.trim().isEmpty() &&
               requests != null && !requests.isEmpty() &&
               maxConcurrency > 0 && maxConcurrency <= 1000 &&
               timeoutMs > 0 && timeoutMs <= 900_000L;
    }
    
    /**
     * 예상 처리 시간 계산 (밀리초)
     * 
     * 동시성을 고려한 대략적인 배치 처리 시간을 추정합니다.
     * 
     * @param avgFunctionExecutionMs 평균 함수 실행 시간 (밀리초)
     * @return 예상 처리 시간 (밀리초)
     */
    public long estimatedProcessingTimeMs(long avgFunctionExecutionMs) {
        if (requests.isEmpty()) {
            return 0L;
        }
        
        // 동시성을 고려한 배치 수 계산
        int batches = (int) Math.ceil((double) requests.size() / maxConcurrency);
        
        // 각 배치는 순차적으로 실행되므로 배치 수 × 평균 실행 시간
        return batches * avgFunctionExecutionMs;
    }
    
    /**
     * 배치 정보 요약
     * 
     * 로깅 및 모니터링을 위한 배치 정보 요약을 반환합니다.
     * 
     * @return 배치 정보 요약 문자열
     */
    public String getSummary() {
        return String.format("Batch[id=%s, requests=%d, maxConcurrency=%d, failFast=%s, retryPolicy=%s, aggregation=%s]",
                batchId, requests.size(), maxConcurrency, failFast, retryPolicy, aggregationMode);
    }
}