package com.ryuqq.aws.sqs.consumer.component;

import java.time.Instant;
import java.util.Map;

/**
 * SQS Consumer 메트릭 수집 및 관리 인터페이스
 * 
 * SQS Listener Container의 성능 및 상태 메트릭을 추적하는 메서드를 제공합니다.
 * 모니터링, 알림, 성능 분석에 필수적인 지표들을 수집합니다.
 * 
 * <h3>수집하는 주요 메트릭</h3>
 * <ul>
 *   <li><strong>메시지 처리 통계</strong>: 성공/실패 건수, 처리 시간</li>
 *   <li><strong>컨테이너 상태</strong>: 상태 전환 횟수, 현재 상태</li>
 *   <li><strong>성능 지표</strong>: 평균/최대/최소 처리 시간</li>
 *   <li><strong>오류 처리</strong>: DLQ 전송, 재시도 통계</li>
 * </ul>
 * 
 * @see InMemoryMetricsCollector
 */
public interface MetricsCollector {
    
    /**
     * 메시지 처리 성공 이벤트 기록
     * 
     * 메시지가 성공적으로 처리된 경우 호출하여 성공 카운터를 증가시킵니다.
     * 마지막 처리 시간도 현재 시간으로 업데이트됩니다.
     * 
     * @param containerId 메시지를 처리한 컨테이너 ID
     */
    void recordMessageProcessed(String containerId);
    
    /**
     * 메시지 처리 실패 이벤트 기록
     * 
     * 메시지 처리 중 예외가 발생한 경우 호출하여 실패 카운터를 증가시킵니다.
     * 마지막 오류 시간과 예외 정보도 저장됩니다.
     * 
     * @param containerId 메시지 처리를 시도한 컨테이너 ID
     * @param exception 발생한 예외 객체
     */
    void recordMessageFailed(String containerId, Exception exception);
    
    /**
     * 메시지 처리 시간 기록
     * 
     * 개별 메시지의 처리 소요 시간을 기록하여 성능 메트릭을 계산합니다.
     * 평균, 최대, 최소 처리 시간 계산에 사용됩니다.
     * 
     * @param containerId 메시지를 처리한 컨테이너 ID
     * @param processingTimeMillis 처리 소요 시간 (밀리초 단위)
     */
    void recordProcessingTime(String containerId, long processingTimeMillis);
    
    /**
     * 컨테이너 상태 변경 이벤트 기록
     * 
     * 컨테이너의 상태가 변경될 때마다 호출하여 상태 전환 횟수를 추적합니다.
     * 컨테이너의 안정성과 상태 관리를 모니터링하는 데 사용됩니다.
     * 
     * @param containerId 상태가 변경된 컨테이너 ID
     * @param fromState 이전 상태 (CREATED, STARTING, RUNNING, STOPPING, STOPPED, FAILED)
     * @param toState 새로운 상태
     */
    void recordStateChange(String containerId, String fromState, String toState);
    
    /**
     * 특정 컨테이너의 메트릭 조회
     * 
     * 지정된 컨테이너 ID에 대한 모든 성능 지표와 상태 정보를 반환합니다.
     * 컨테이너가 존재하지 않을 경우 기본값으로 초기화된 메트릭을 반환합니다.
     * 
     * @param containerId 조회할 컨테이너 ID
     * @return 컨테이너의 메트릭 정보
     */
    ContainerMetrics getContainerMetrics(String containerId);
    
    /**
     * 전체 컨테이너의 메트릭 조회
     * 
     * 현재 등록된 모든 컨테이너의 메트릭 정보를 Map 형태로 반환합니다.
     * 대시보드나 모니터링 시스템에서 전체 현황을 파악하는 데 사용됩니다.
     * 
     * @return 컨테이너 ID를 키로 하고 메트릭 정보를 값으로 하는 Map
     */
    Map<String, ContainerMetrics> getAllMetrics();
    
    /**
     * 특정 컨테이너의 메트릭 초기화
     * 
     * 지정된 컨테이너의 모든 메트릭 데이터를 삭제하고 초기 상태로 되돌립니다.
     * 컨테이너 재시작이나 문제 해결 후 메트릭 새로고침에 사용됩니다.
     * 
     * @param containerId 초기화할 컨테이너 ID
     */
    void resetContainerMetrics(String containerId);
    
    /**
     * 전체 메트릭 초기화
     * 
     * 모든 컨테이너의 메트릭 데이터를 삭제하고 깨끗한 상태로 되돌립니다.
     * 시스템 재시작이나 메트릭 데이터 전체 리셋이 필요한 경우 사용됩니다.
     */
    void resetAllMetrics();
    
    /**
     * DLQ 작업 결과 기록
     * 
     * Dead Letter Queue로의 메시지 전송 작업 결과를 기록합니다.
     * DLQ 전송 성능과 안정성을 모니터링하는 데 사용됩니다.
     * 
     * @param containerId 메시지를 처리한 컨테이너 ID
     * @param success DLQ 작업 성공 여부 (true: 성공, false: 실패)
     */
    default void recordDlqOperation(String containerId, boolean success) {
        // 기본 구현: 아무 작업 안함 (선택적 기능)
    }
    
    /**
     * 재시도 시도 횟수 기록
     * 
     * 메시지 처리 실패 시 발생한 재시도 시도 횟수를 기록합니다.
     * 재시도 전략의 효과성과 패턴을 분석하는 데 사용됩니다.
     * 
     * @param containerId 메시지를 처리한 컨테이너 ID
     * @param retryAttempts 총 재시도 시도 횟수
     */
    default void recordRetryAttempts(String containerId, int retryAttempts) {
        // 기본 구현: 아무 작업 안함 (선택적 기능)
    }
    
    /**
     * 컨테이너 메트릭 데이터 인터페이스
     * 
     * 개별 컨테이너의 성능 지표와 상태 정보를 제공하는 읽기 전용 인터페이스입니다.
     * 모니터링 및 대시보드에서 컨테이너의 현재 상태를 파악하는 데 사용됩니다.
     */
    interface ContainerMetrics {
        /** 컨테이너 고유 식별자 반환 */
        String getContainerId();
        /** 성공적으로 처리된 메시지 총 개수 반환 */
        long getProcessedCount();
        /** 처리 실패한 메시지 총 개수 반환 */
        long getFailedCount();
        /** 메시지 평균 처리 시간(밀리초) 반환 */
        long getAverageProcessingTime();
        /** 메시지 최대 처리 시간(밀리초) 반환 */
        long getMaxProcessingTime();
        /** 마지막 활동 시간(처리 성공 또는 실패) 반환 */
        Instant getLastActivity();
        /** 현재 컨테이너 상태 반환 */
        String getCurrentState();
        /** 컨테이너 상태 변경 총 횟수 반환 */
        long getStateChanges();
    }
}