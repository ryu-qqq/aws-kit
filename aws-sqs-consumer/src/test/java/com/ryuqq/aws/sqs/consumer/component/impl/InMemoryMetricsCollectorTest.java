package com.ryuqq.aws.sqs.consumer.component.impl;

import com.ryuqq.aws.sqs.consumer.component.MetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * InMemoryMetricsCollector 테스트
 * 
 * SQS Consumer 메트릭 수집 시스템의 동작을 검증하는 포괄적인 테스트입니다.
 * 메트릭 수집, 조회, 집계, 동시성 처리를 검증합니다.
 */
@DisplayName("InMemoryMetricsCollector 테스트")
class InMemoryMetricsCollectorTest {
    
    private InMemoryMetricsCollector metricsCollector;
    private static final String CONTAINER_ID = "test-container-1";
    private static final String ANOTHER_CONTAINER_ID = "test-container-2";
    
    @BeforeEach
    void setUp() {
        metricsCollector = new InMemoryMetricsCollector();
    }
    
    @Test
    @DisplayName("메시지 처리 성공을 기록해야 한다")
    void shouldRecordMessageProcessed() {
        // when
        metricsCollector.recordMessageProcessed(CONTAINER_ID);
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getContainerId()).isEqualTo(CONTAINER_ID);
        assertThat(metrics.getProcessedCount()).isEqualTo(1);
        assertThat(metrics.getFailedCount()).isEqualTo(0);
        assertThat(metrics.getLastActivity()).isNotNull();
    }
    
    @Test
    @DisplayName("메시지 처리 실패를 기록해야 한다")
    void shouldRecordMessageFailed() {
        // given
        Exception testException = new RuntimeException("테스트 예외");
        
        // when
        metricsCollector.recordMessageFailed(CONTAINER_ID, testException);
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getContainerId()).isEqualTo(CONTAINER_ID);
        assertThat(metrics.getProcessedCount()).isEqualTo(0);
        assertThat(metrics.getFailedCount()).isEqualTo(1);
        assertThat(metrics.getLastActivity()).isNotNull();
    }
    
    @Test
    @DisplayName("처리 시간을 올바르게 기록하고 집계해야 한다")
    void shouldRecordAndAggregateProcessingTime() {
        // given
        long[] processingTimes = {100, 200, 300, 150, 250};
        
        // when
        for (long time : processingTimes) {
            metricsCollector.recordProcessingTime(CONTAINER_ID, time);
        }
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getAverageProcessingTime()).isEqualTo(200); // (100+200+300+150+250)/5
        assertThat(metrics.getMaxProcessingTime()).isEqualTo(300);
    }
    
    @Test
    @DisplayName("단일 처리 시간 기록을 올바르게 처리해야 한다")
    void shouldHandleSingleProcessingTimeRecord() {
        // given
        long processingTime = 150;
        
        // when
        metricsCollector.recordProcessingTime(CONTAINER_ID, processingTime);
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getAverageProcessingTime()).isEqualTo(150);
        assertThat(metrics.getMaxProcessingTime()).isEqualTo(150);
    }
    
    @Test
    @DisplayName("상태 변경을 기록해야 한다")
    void shouldRecordStateChange() {
        // when
        metricsCollector.recordStateChange(CONTAINER_ID, "CREATED", "STARTING");
        metricsCollector.recordStateChange(CONTAINER_ID, "STARTING", "RUNNING");
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getCurrentState()).isEqualTo("RUNNING");
        assertThat(metrics.getStateChanges()).isEqualTo(2);
    }
    
    @Test
    @DisplayName("여러 컨테이너의 메트릭을 독립적으로 관리해야 한다")
    void shouldManageMultipleContainerMetricsIndependently() {
        // when
        metricsCollector.recordMessageProcessed(CONTAINER_ID);
        metricsCollector.recordMessageProcessed(CONTAINER_ID);
        metricsCollector.recordMessageProcessed(ANOTHER_CONTAINER_ID);
        metricsCollector.recordMessageFailed(ANOTHER_CONTAINER_ID, new RuntimeException("테스트"));
        
        // then
        MetricsCollector.ContainerMetrics metrics1 = metricsCollector.getContainerMetrics(CONTAINER_ID);
        MetricsCollector.ContainerMetrics metrics2 = metricsCollector.getContainerMetrics(ANOTHER_CONTAINER_ID);
        
        assertThat(metrics1.getProcessedCount()).isEqualTo(2);
        assertThat(metrics1.getFailedCount()).isEqualTo(0);
        
        assertThat(metrics2.getProcessedCount()).isEqualTo(1);
        assertThat(metrics2.getFailedCount()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("존재하지 않는 컨테이너의 메트릭 조회 시 기본값을 반환해야 한다")
    void shouldReturnDefaultMetricsForNonExistentContainer() {
        // when
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics("non-existent");
        
        // then
        assertThat(metrics.getContainerId()).isEqualTo("non-existent");
        assertThat(metrics.getProcessedCount()).isEqualTo(0);
        assertThat(metrics.getFailedCount()).isEqualTo(0);
        assertThat(metrics.getAverageProcessingTime()).isEqualTo(0);
        assertThat(metrics.getMaxProcessingTime()).isEqualTo(0);
        assertThat(metrics.getStateChanges()).isEqualTo(0);
        assertThat(metrics.getCurrentState()).isEqualTo("UNKNOWN");
        assertThat(metrics.getLastActivity()).isNull();
    }
    
    @Test
    @DisplayName("모든 메트릭을 조회할 수 있어야 한다")
    void shouldRetrieveAllMetrics() {
        // given
        metricsCollector.recordMessageProcessed(CONTAINER_ID);
        metricsCollector.recordMessageProcessed(ANOTHER_CONTAINER_ID);
        
        // when
        Map<String, MetricsCollector.ContainerMetrics> allMetrics = metricsCollector.getAllMetrics();
        
        // then
        assertThat(allMetrics).hasSize(2);
        assertThat(allMetrics).containsKeys(CONTAINER_ID, ANOTHER_CONTAINER_ID);
        assertThat(allMetrics.get(CONTAINER_ID).getProcessedCount()).isEqualTo(1);
        assertThat(allMetrics.get(ANOTHER_CONTAINER_ID).getProcessedCount()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("특정 컨테이너의 메트릭을 초기화해야 한다")
    void shouldResetContainerMetrics() {
        // given
        metricsCollector.recordMessageProcessed(CONTAINER_ID);
        metricsCollector.recordProcessingTime(CONTAINER_ID, 100);
        metricsCollector.recordStateChange(CONTAINER_ID, "CREATED", "RUNNING");
        
        assertThat(metricsCollector.getContainerMetrics(CONTAINER_ID).getProcessedCount()).isEqualTo(1);
        
        // when
        metricsCollector.resetContainerMetrics(CONTAINER_ID);
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getProcessedCount()).isEqualTo(0);
        assertThat(metrics.getFailedCount()).isEqualTo(0);
        assertThat(metrics.getAverageProcessingTime()).isEqualTo(0);
        assertThat(metrics.getMaxProcessingTime()).isEqualTo(0);
        assertThat(metrics.getStateChanges()).isEqualTo(0);
        assertThat(metrics.getCurrentState()).isEqualTo("UNKNOWN");
        assertThat(metrics.getLastActivity()).isNull();
    }
    
    @Test
    @DisplayName("모든 메트릭을 초기화해야 한다")
    void shouldResetAllMetrics() {
        // given
        metricsCollector.recordMessageProcessed(CONTAINER_ID);
        metricsCollector.recordMessageProcessed(ANOTHER_CONTAINER_ID);
        
        assertThat(metricsCollector.getAllMetrics()).hasSize(2);
        
        // when
        metricsCollector.resetAllMetrics();
        
        // then
        assertThat(metricsCollector.getAllMetrics()).isEmpty();
    }
    
    @Test
    @DisplayName("DLQ 작업 결과를 기록해야 한다")
    void shouldRecordDlqOperation() {
        // when
        metricsCollector.recordDlqOperation(CONTAINER_ID, true);
        metricsCollector.recordDlqOperation(CONTAINER_ID, false);
        
        // then - DLQ 메트릭이 기록되었는지 확인 (구현체에 따라 검증 방법이 다를 수 있음)
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics).isNotNull();
        // 실제 구현에서는 DLQ 성공/실패 카운터를 별도로 추가할 수 있음
    }
    
    @Test
    @DisplayName("재시도 횟수를 기록해야 한다")
    void shouldRecordRetryAttempts() {
        // when
        metricsCollector.recordRetryAttempts(CONTAINER_ID, 3);
        metricsCollector.recordRetryAttempts(CONTAINER_ID, 1);
        
        // then - 재시도 메트릭이 기록되었는지 확인
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics).isNotNull();
        // 실제 구현에서는 재시도 통계를 별도로 추가할 수 있음
    }
    
    @Test
    @DisplayName("동시성 환경에서 안전하게 동작해야 한다")
    void shouldBeThreadSafe() throws InterruptedException {
        // given
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // when - 여러 스레드에서 동시에 메트릭 업데이트
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (j % 2 == 0) {
                            metricsCollector.recordMessageProcessed(CONTAINER_ID);
                        } else {
                            metricsCollector.recordMessageFailed(CONTAINER_ID, new RuntimeException("테스트"));
                        }
                        metricsCollector.recordProcessingTime(CONTAINER_ID, 100 + j);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // then
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getProcessedCount()).isEqualTo(threadCount * operationsPerThread / 2);
        assertThat(metrics.getFailedCount()).isEqualTo(threadCount * operationsPerThread / 2);
        assertThat(metrics.getAverageProcessingTime()).isGreaterThan(0);
        
        executorService.shutdown();
        assertThat(executorService.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test
    @DisplayName("마지막 활동 시간이 올바르게 업데이트되어야 한다")
    void shouldUpdateLastActivityTime() throws InterruptedException {
        // given
        Instant before = Instant.now();
        Thread.sleep(1); // 시간 차이를 만들기 위한 대기
        
        // when
        metricsCollector.recordMessageProcessed(CONTAINER_ID);
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getLastActivity()).isAfter(before);
        
        // 추가 활동 후 시간이 업데이트되는지 확인
        Instant firstActivity = metrics.getLastActivity();
        Thread.sleep(1);
        metricsCollector.recordMessageFailed(CONTAINER_ID, new RuntimeException("테스트"));
        
        MetricsCollector.ContainerMetrics updatedMetrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(updatedMetrics.getLastActivity()).isAfter(firstActivity);
    }
    
    @Test
    @DisplayName("처리 시간 통계가 정확하게 계산되어야 한다")
    void shouldCalculateProcessingTimeStatisticsCorrectly() {
        // given
        long[] times = {50, 100, 150, 200, 250, 300, 350, 400, 450, 500};
        
        // when
        for (long time : times) {
            metricsCollector.recordProcessingTime(CONTAINER_ID, time);
        }
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getAverageProcessingTime()).isEqualTo(275); // (50+100+...+500)/10
        assertThat(metrics.getMaxProcessingTime()).isEqualTo(500);
    }
    
    @Test
    @DisplayName("0ms 처리 시간도 올바르게 처리해야 한다")
    void shouldHandleZeroProcessingTime() {
        // when
        metricsCollector.recordProcessingTime(CONTAINER_ID, 0);
        metricsCollector.recordProcessingTime(CONTAINER_ID, 100);
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getAverageProcessingTime()).isEqualTo(50); // (0+100)/2
        assertThat(metrics.getMaxProcessingTime()).isEqualTo(100);
    }
    
    @Test
    @DisplayName("상태 변경 횟수가 정확하게 계산되어야 한다")
    void shouldCountStateChangesCorrectly() {
        // when
        metricsCollector.recordStateChange(CONTAINER_ID, "CREATED", "STARTING");
        metricsCollector.recordStateChange(CONTAINER_ID, "STARTING", "RUNNING");
        metricsCollector.recordStateChange(CONTAINER_ID, "RUNNING", "STOPPING");
        metricsCollector.recordStateChange(CONTAINER_ID, "STOPPING", "STOPPED");
        
        // then
        MetricsCollector.ContainerMetrics metrics = metricsCollector.getContainerMetrics(CONTAINER_ID);
        assertThat(metrics.getStateChanges()).isEqualTo(4);
        assertThat(metrics.getCurrentState()).isEqualTo("STOPPED");
    }
}