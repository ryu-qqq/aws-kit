package com.ryuqq.aws.sqs.consumer.component.impl;

import com.ryuqq.aws.sqs.consumer.component.MetricsCollector;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 메모리 기반 MetricsCollector 구현체
 * 
 * SQS Consumer의 성능 메트릭을 메모리에 저장하여 런타임 모니터링을 지원합니다.
 * ConcurrentHashMap을 사용하여 Thread-safe한 메트릭 수집을 보장합니다.
 * 
 * <h3>주요 특징</h3>
 * <ul>
 *   <li><strong>Thread 안전성</strong>: ConcurrentHashMap과 AtomicLong을 사용한 동시성 보장</li>
 *   <li><strong>실시간 모니터링</strong>: 메모리에 저장되어 빠른 조회 성능</li>
 *   <li><strong>자동 계산</strong>: 평균/최대/최소 처리 시간 자동 산출</li>
 *   <li><strong>Spring 통합</strong>: @Component 어노테이션으로 자동 빈 등록</li>
 * </ul>
 * 
 * <h3>사용 예시</h3>
 * <pre>
 * {@literal @}Autowired
 * private MetricsCollector metricsCollector;
 * 
 * // 메시지 처리 성공 시
 * metricsCollector.recordMessageProcessed("container-1");
 * metricsCollector.recordProcessingTime("container-1", 150L);
 * 
 * // 메트릭 조회
 * ContainerMetrics metrics = metricsCollector.getContainerMetrics("container-1");
 * </pre>
 * 
 * <h3>대안 구현</h3>
 * 더 고도화된 모니터링이 필요하다면 Micrometer나 Prometheus와 같은
 * 메트릭 시스템과 연동하는 구현체로 대체할 수 있습니다.
 * 
 * @see MetricsCollector
 */
@Component
public class InMemoryMetricsCollector implements MetricsCollector {
    
    private final Map<String, MutableContainerMetrics> metricsMap = new ConcurrentHashMap<>();
    
    // 하위 호환성을 위한 추가 메서드 - 기존 코드와의 호환성 보장
    /**
     * 메시지 처리 성공 이벤트 원스톱 기록 (하위 호환성)
     * 
     * 기존 API와의 호환성을 위해 제공되는 편의 메서드입니다.
     * 내부적으로 recordMessageProcessed()와 recordProcessingTime()을 순차적으로 호출합니다.
     * 
     * @param containerId 컨테이너 ID
     * @param processingTimeMs 처리 소요 시간 (밀리초)
     */
    public void recordProcessedMessage(String containerId, long processingTimeMs) {
        recordMessageProcessed(containerId);
        recordProcessingTime(containerId, processingTimeMs);
    }
    
    /**
     * 메시지 처리 실패 이벤트 원스톱 기록 (하위 호환성)
     * 
     * @param containerId 컨테이너 ID
     * @param exception 발생한 예외
     */
    public void recordFailedMessage(String containerId, Exception exception) {
        recordMessageFailed(containerId, exception);
    }
    
    /**
     * 메시지 처리 성공 이벤트 기록
     * 
     * 성공 카운터를 증가시키고 마지막 처리 시간을 현재 시간으로 업데이트합니다.
     * Thread-safe한 AtomicLong 연산을 사용하여 동시성을 보장합니다.
     */
    @Override
    public void recordMessageProcessed(String containerId) {
        MutableContainerMetrics metrics = getOrCreateMetrics(containerId);
        metrics.recordMessageProcessed();
    }
    
    /**
     * 메시지 처리 실패 이벤트 기록
     * 
     * 실패 카운터를 증가시키고 마지막 오류 시간과 예외 정보를 기록합니다.
     * 디버깅과 문제 진단에 필요한 예외 정보를 저장합니다.
     */
    @Override
    public void recordMessageFailed(String containerId, Exception exception) {
        MutableContainerMetrics metrics = getOrCreateMetrics(containerId);
        metrics.recordMessageFailed(exception);
    }
    
    /**
     * 메시지 처리 시간 기록
     * 
     * 개별 메시지의 처리 소요 시간을 기록하여 성능 메트릭을 계산합니다.
     * 평균, 최대, 최소 처리 시간 통계에 사용됩니다.
     */
    @Override
    public void recordProcessingTime(String containerId, long processingTimeMillis) {
        MutableContainerMetrics metrics = getOrCreateMetrics(containerId);
        metrics.recordProcessingTime(processingTimeMillis);
    }
    
    /**
     * 컨테이너 상태 변경 이벤트 기록
     * 
     * 컨테이너의 상태 전환을 추적하여 상태 변경 횟수와 현재 상태를 업데이트합니다.
     * 컨테이너의 안정성 및 상태 관리 정상성을 모니터링하는 데 사용됩니다.
     */
    @Override
    public void recordStateChange(String containerId, String fromState, String toState) {
        MutableContainerMetrics metrics = getOrCreateMetrics(containerId);
        metrics.recordStateChange(fromState, toState);
    }
    
    /**
     * 특정 컨테이너의 메트릭 조회
     * 
     * 내부에 저장된 Mutable 메트릭을 Immutable 객체로 변환하여 반환합니다.
     * 컨테이너가 존재하지 않을 경우 기본값으로 초기화된 메트릭을 생성합니다.
     */
    @Override
    public ContainerMetrics getContainerMetrics(String containerId) {
        MutableContainerMetrics metrics = metricsMap.get(containerId);
        return metrics != null ? metrics.toImmutable() : createEmptyMetrics(containerId);
    }
    
    /**
     * 전체 컨테이너의 메트릭 조회
     * 
     * 모든 컨테이너의 멤트릭을 동시에 Immutable 객체로 변환하여 반환합니다.
     * ConcurrentHashMap을 사용하여 Thread-safe한 조회를 보장합니다.
     */
    @Override
    public Map<String, ContainerMetrics> getAllMetrics() {
        Map<String, ContainerMetrics> result = new ConcurrentHashMap<>();
        metricsMap.forEach((id, metrics) -> result.put(id, metrics.toImmutable()));
        return result;
    }
    
    /**
     * 특정 컨테이너의 메트릭 초기화
     * 
     * 지정된 컨테이너의 모든 메트릭 데이터를 메모리에서 제거합니다.
     * 컨테이너 재시작 또는 문제 해결 후 메트릭 새로고침에 사용됩니다.
     */
    @Override
    public void resetContainerMetrics(String containerId) {
        metricsMap.remove(containerId);
    }
    
    /**
     * 전체 메트릭 초기화
     * 
     * 모든 컨테이너의 메트릭 데이터를 메모리에서 제거합니다.
     * 시스템 전체 재시작이나 메트릭 데이터 완전 리셋이 필요한 경우 사용됩니다.
     */
    @Override
    public void resetAllMetrics() {
        metricsMap.clear();
    }
    
    /**
     * DLQ 작업 결과 기록
     * 
     * 인터페이스의 새로운 메서드를 구현하여 DLQ 작업 결과를 추적합니다.
     * 내부적으로 MutableContainerMetrics의 recordDlqOperation을 호출합니다.
     */
    @Override
    public void recordDlqOperation(String containerId, boolean success) {
        MutableContainerMetrics metrics = getOrCreateMetrics(containerId);
        metrics.recordDlqOperation(success);
    }
    
    /**
     * 재시도 시도 횟수 기록
     * 
     * 인터페이스의 새로운 메서드를 구현하여 재시도 패턴을 추적합니다.
     * 내부적으로 MutableContainerMetrics의 recordRetryAttempts를 호출합니다.
     */
    @Override
    public void recordRetryAttempts(String containerId, int retryAttempts) {
        if (retryAttempts > 0) { // 0 이하의 재시도 횟수는 무시
            MutableContainerMetrics metrics = getOrCreateMetrics(containerId);
            metrics.recordRetryAttempts(retryAttempts);
        }
    }
    
    /**
     * 메트릭 인스턴스 조회 또는 생성
     * 
     * 지정된 컨테이너 ID에 대한 메트릭 인스턴스를 조회하고,
     * 존재하지 않을 경우 새로운 인스턴스를 생성합니다.
     * ConcurrentHashMap.computeIfAbsent()를 사용하여 Thread-safe한 생성을 보장합니다.
     */
    private MutableContainerMetrics getOrCreateMetrics(String containerId) {
        return metricsMap.computeIfAbsent(containerId, MutableContainerMetrics::new);
    }
    
    /**
     * 빈 메트릭 인스턴스 생성
     * 
     * 존재하지 않는 컨테이너에 대해 기본값으로 초기화된 메트릭 인스턴스를 생성합니다.
     * 이를 통해 null 처리 대신 안전한 기본값을 제공합니다.
     */
    private ContainerMetrics createEmptyMetrics(String containerId) {
        return new ImmutableContainerMetrics(containerId, 0, 0, 0, 0, 0, 0.0, 0, Long.MAX_VALUE, null, null, "CREATED", 0);
    }
    
    /**
     * 내부 사용용 가변 메트릭 클래스
     * 
     * Thread-safe한 메트릭 수집을 위해 AtomicLong과 volatile 변수를 사용합니다.
     * 외부에서는 Immutable 객체로 변환하여 제공하여 데이터 무결성을 보장합니다.
     * 
     * <h4>Thread 안전성 전략</h4>
     * <ul>
     *   <li><strong>AtomicLong</strong>: 카운터 및 누적 값들의 원자적 연산</li>
     *   <li><strong>volatile</strong>: 단순 값 및 참조의 가시성 보장</li>
     *   <li><strong>synchronized</strong>: 배타적 업데이트에서만 사용</li>
     * </ul>
     */
    private static class MutableContainerMetrics {
        private final String containerId;
        private final AtomicLong processedCount = new AtomicLong(0);
        private final AtomicLong failedCount = new AtomicLong(0);
        private final AtomicLong totalRetryAttempts = new AtomicLong(0);
        private final AtomicLong dlqSuccessCount = new AtomicLong(0);
        private final AtomicLong dlqFailureCount = new AtomicLong(0);
        private final AtomicLong totalProcessingTimeMs = new AtomicLong(0);
        private final AtomicLong stateChanges = new AtomicLong(0);
        private volatile long maxProcessingTimeMs = 0;
        private volatile long minProcessingTimeMs = Long.MAX_VALUE;
        private volatile Instant lastProcessedTime;
        private volatile Instant lastFailureTime;
        private volatile String currentState = "CREATED";
        
        /**
         * 내부 메트릭 인스턴스 생성자
         * 
         * @param containerId 컨테이너 고유 식별자
         */
        public MutableContainerMetrics(String containerId) {
            this.containerId = containerId;
        }
        
        /**
         * 메시지 처리 성공 기록
         * 
         * 성공 카운터를 원자적으로 증가시키고 마지막 처리 시간을 업데이트합니다.
         */
        public void recordMessageProcessed() {
            processedCount.incrementAndGet();
            lastProcessedTime = Instant.now();
        }
        
        /**
         * 메시지 처리 시간 기록
         * 
         * 총 처리 시간을 누적하고 최대/최소 처리 시간을 업데이트합니다.
         * synchronized를 사용하여 최대/최소값 비교 연산의 원자성을 보장합니다.
         * 
         * @param processingTimeMs 처리 소요 시간(밀리초)
         */
        public synchronized void recordProcessingTime(long processingTimeMs) {
            totalProcessingTimeMs.addAndGet(processingTimeMs);
            
            if (processingTimeMs > maxProcessingTimeMs) {
                maxProcessingTimeMs = processingTimeMs;
            }
            if (processingTimeMs < minProcessingTimeMs) {
                minProcessingTimeMs = processingTimeMs;
            }
        }
        
        /**
         * 메시지 처리 실패 기록
         * 
         * 실패 카운터를 원자적으로 증가시키고 마지막 실패 시간을 업데이트합니다.
         * 
         * @param exception 발생한 예외 (현재는 저장하지 않음, 추후 확장 가능)
         */
        public void recordMessageFailed(Exception exception) {
            failedCount.incrementAndGet();
            lastFailureTime = Instant.now();
        }
        
        /**
         * 컨테이너 상태 변경 기록
         * 
         * 현재 상태를 업데이트하고 상태 변경 카운터를 증가시킵니다.
         * 
         * @param fromState 이전 상태 (현재는 사용하지 않음, 추후 로깅용도로 확장 가능)
         * @param toState 새로운 상태
         */
        public void recordStateChange(String fromState, String toState) {
            this.currentState = toState;
            stateChanges.incrementAndGet();
        }
        
        /**
         * DLQ(Dead Letter Queue) 작업 결과 기록
         * 
         * 실패한 메시지를 DLQ로 전송하는 작업의 성공/실패 여부를 기록합니다.
         * DLQ 전송 성능과 안정성을 모니터링하는 데 사용됩니다.
         * 
         * @param success DLQ 작업 성공 여부
         */
        public void recordDlqOperation(boolean success) {
            if (success) {
                dlqSuccessCount.incrementAndGet();
            } else {
                dlqFailureCount.incrementAndGet();
            }
        }
        
        /**
         * 재시도 시도 횟수 기록
         * 
         * 메시지 처리 실패 시 재시도 시도 횟수를 기록합니다.
         * 재시도 전략의 효과성과 패턴을 분석하는 데 사용됩니다.
         * 
         * @param retryAttempts 현재까지의 재시도 시도 횟수
         */
        public void recordRetryAttempts(int retryAttempts) {
            totalRetryAttempts.addAndGet(retryAttempts);
        }
        
        /**
         * 불변 메트릭 객체로 변환
         * 
         * 현재 수집된 모든 메트릭 데이터를 불변 객체로 변환하여 외부에 안전하게 노출합니다.
         * 평균 처리 시간은 실시간으로 계산되고, 최소값의 특수 처리도 포함됩니다.
         * 
         * @return 외부 노출용 불변 메트릭 객체
         */
        public ContainerMetrics toImmutable() {
            long processed = processedCount.get();
            // 처리된 메시지가 있을 경우만 평균 계산
            double averageProcessingTime = processed > 0 ? 
                    (double) totalProcessingTimeMs.get() / processed : 0.0;
            
            return new ImmutableContainerMetrics(
                    containerId,
                    processed,
                    failedCount.get(),
                    totalRetryAttempts.get(),
                    dlqSuccessCount.get(),
                    dlqFailureCount.get(),
                    averageProcessingTime,
                    maxProcessingTimeMs,
                    minProcessingTimeMs == Long.MAX_VALUE ? 0 : minProcessingTimeMs, // 초기값 처리
                    lastProcessedTime,
                    lastFailureTime,
                    currentState,
                    stateChanges.get()
            );
        }
    }
    
    /**
     * 불변 메트릭 구현 클래스
     * 
     * 외부에 노출되는 메트릭 데이터를 담는 불변 객체입니다.
     * 외부 노출용 불변 메트릭 데이터를 담는 record입니다.
     * 데이터 무결성을 보장하기 위해 모든 필드는 final로 선언됩니다.
     */
    private record ImmutableContainerMetrics(
            String containerId,
            long processedCount,
            long failedCount,
            long totalRetryAttempts,
            long dlqSuccessCount,
            long dlqFailureCount,
            double averageProcessingTimeMs,
            long maxProcessingTimeMs,
            long minProcessingTimeMs,
            Instant lastProcessedTime,
            Instant lastFailureTime,
            String currentState,
            long stateChanges
    ) implements ContainerMetrics {

        /**
         * 평균 처리 시간 반환 (롱 타입 변환)
         *
         * 내부에서는 double로 계산되지만, 인터페이스 호환성을 위해 long으로 반환합니다.
         *
         * @return 평균 처리 시간(밀리초, 소수점 절사)
         */
        @Override
        public long getAverageProcessingTime() {
            return (long) averageProcessingTimeMs;
        }

        /**
         * 마지막 활동 시간 반환
         *
         * 마지막 성공 처리 시간과 마지막 실패 시간 중 더 최근의 시간을 반환합니다.
         * 컨테이너의 활동 상태와 마지막 동작 시점을 파악하는 데 사용됩니다.
         *
         * @return 마지막 활동 시각 (성공 또는 실패 중 최근)
         */
        @Override
        public Instant getLastActivity() {
            if (lastProcessedTime != null && lastFailureTime != null) {
                return lastProcessedTime.isAfter(lastFailureTime) ? lastProcessedTime : lastFailureTime;
            }
            return lastProcessedTime != null ? lastProcessedTime : lastFailureTime;
        }

        /**
         * 상태 변경 횟수 반환
         *
         * @return 컨테이너 생성 이후 발생한 상태 전환 총 횟수
         */
        @Override
        public long getStateChanges() {
            return stateChanges;
        }

        /**
         * 최대 처리 시간 반환
         *
         * @return 지금까지 처리된 메시지 중 가장 오래 걸린 처리 시간(밀리초)
         */
        @Override
        public long getMaxProcessingTime() {
            return maxProcessingTimeMs;
        }

        /**
         * 처리 실패한 메시지 총 개수 반환
         *
         * @return 실패한 메시지 수
         */
        @Override
        public long getFailedCount() {
            return failedCount;
        }

        /**
         * 성공적으로 처리된 메시지 총 개수 반환
         *
         * @return 처리된 메시지 수
         */
        @Override
        public long getProcessedCount() {
            return processedCount;
        }

        /**
         * 컨테이너 고유 식별자 반환
         *
         * @return 컨테이너 ID
         */
        @Override
        public String getContainerId() {
            return containerId;
        }

        /**
         * 현재 컨테이너 상태 반환
         *
         * @return 현재 컨테이너의 상태
         */
        @Override
        public String getCurrentState() {
            return currentState;
        }
    }
}