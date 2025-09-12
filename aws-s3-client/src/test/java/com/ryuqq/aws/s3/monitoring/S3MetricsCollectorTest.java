package com.ryuqq.aws.s3.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * S3 메트릭 수집기 테스트
 * 
 * 한국어 설명:
 * S3MetricsCollector의 메트릭 수집, 기록, 집계 기능을 테스트합니다.
 * Micrometer를 사용한 성능 모니터링 검증을 포함합니다.
 */
@DisplayName("S3 메트릭 수집기 테스트")
class S3MetricsCollectorTest {

    private MeterRegistry meterRegistry;
    private S3MetricsCollector metricsCollector;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsCollector = new S3MetricsCollector(meterRegistry);
    }

    @Test
    @DisplayName("초기화 시 모든 작업 타입의 메트릭이 생성되어야 함")
    void constructor_ShouldCreateMetricsForAllOperationTypes_WhenInitialized() {
        // Given & When - setUp에서 초기화됨
        
        // Then
        S3MetricsCollector.OperationType[] allTypes = S3MetricsCollector.OperationType.values();
        
        for (S3MetricsCollector.OperationType type : allTypes) {
            // 각 작업 타입별로 타이머와 카운터가 생성되었는지 확인
            Timer timer = meterRegistry.find("s3.operation.duration")
                    .tag("operation", type.getName())
                    .timer();
            assertThat(timer).isNotNull();
            
            Counter counter = meterRegistry.find("s3.operation.count")
                    .tag("operation", type.getName())
                    .counter();
            assertThat(counter).isNotNull();
        }
        
        // 바이트 전송 게이지 확인
        assertThat(meterRegistry.find("s3.bytes.uploaded").gauge()).isNotNull();
        assertThat(meterRegistry.find("s3.bytes.downloaded").gauge()).isNotNull();
    }

    @Test
    @DisplayName("성공한 작업 기록 - 타이머 샘플 사용")
    void recordOperation_ShouldRecordSuccessfulOperation_WhenOperationSucceeds() {
        // Given
        S3MetricsCollector.OperationType operationType = S3MetricsCollector.OperationType.UPLOAD;
        Timer.Sample sample = metricsCollector.startTimer(operationType);
        
        // 약간의 시간 경과 시뮬레이션
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // When
        metricsCollector.recordOperation(sample, operationType, true);

        // Then
        Counter successCounter = meterRegistry.find("s3.operation.count")
                .tag("operation", "upload")
                .tag("status", "success")
                .counter();
        
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1.0);
        
        Timer successTimer = meterRegistry.find("s3.operation.duration")
                .tag("operation", "upload")
                .tag("status", "success")
                .timer();
        
        assertThat(successTimer).isNotNull();
        assertThat(successTimer.count()).isEqualTo(1);
        assertThat(successTimer.totalTime(Duration.ofMillis(1).toNanos())).isGreaterThan(0);
    }

    @Test
    @DisplayName("실패한 작업 기록")
    void recordOperation_ShouldRecordFailedOperation_WhenOperationFails() {
        // Given
        S3MetricsCollector.OperationType operationType = S3MetricsCollector.OperationType.DOWNLOAD;
        Timer.Sample sample = metricsCollector.startTimer(operationType);

        // When
        metricsCollector.recordOperation(sample, operationType, false);

        // Then
        Counter failureCounter = meterRegistry.find("s3.operation.count")
                .tag("operation", "download")
                .tag("status", "failure")
                .counter();
        
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1.0);
        
        Timer failureTimer = meterRegistry.find("s3.operation.duration")
                .tag("operation", "download")
                .tag("status", "failure")
                .timer();
        
        assertThat(failureTimer).isNotNull();
        assertThat(failureTimer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("작업 시간 직접 기록")
    void recordOperationTime_ShouldRecordOperationTime_WhenDurationProvided() {
        // Given
        S3MetricsCollector.OperationType operationType = S3MetricsCollector.OperationType.DELETE;
        Duration operationDuration = Duration.ofMillis(150);

        // When
        metricsCollector.recordOperationTime(operationType, operationDuration, true);

        // Then
        Timer timer = meterRegistry.find("s3.operation.duration")
                .tag("operation", "delete")
                .tag("status", "success")
                .timer();
        
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(Duration.ofMillis(1).toNanos())).isGreaterThanOrEqualTo(150);
        
        Counter counter = meterRegistry.find("s3.operation.count")
                .tag("operation", "delete")
                .tag("status", "success")
                .counter();
        
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("업로드 바이트 수 기록")
    void recordBytesTransferred_ShouldRecordUploadBytes_WhenUploadOperation() {
        // Given
        S3MetricsCollector.OperationType operationType = S3MetricsCollector.OperationType.UPLOAD;
        long uploadedBytes = 1024 * 1024; // 1MB

        // When
        metricsCollector.recordBytesTransferred(operationType, uploadedBytes);

        // Then
        assertThat(meterRegistry.find("s3.bytes.uploaded").gauge().value()).isEqualTo(uploadedBytes);
        
        // 다운로드 게이지는 변경되지 않아야 함
        assertThat(meterRegistry.find("s3.bytes.downloaded").gauge().value()).isEqualTo(0);
    }

    @Test
    @DisplayName("다운로드 바이트 수 기록")
    void recordBytesTransferred_ShouldRecordDownloadBytes_WhenDownloadOperation() {
        // Given
        S3MetricsCollector.OperationType operationType = S3MetricsCollector.OperationType.DOWNLOAD;
        long downloadedBytes = 2048 * 1024; // 2MB

        // When
        metricsCollector.recordBytesTransferred(operationType, downloadedBytes);

        // Then
        assertThat(meterRegistry.find("s3.bytes.downloaded").gauge().value()).isEqualTo(downloadedBytes);
        
        // 업로드 게이지는 변경되지 않아야 함
        assertThat(meterRegistry.find("s3.bytes.uploaded").gauge().value()).isEqualTo(0);
    }

    @Test
    @DisplayName("누적 바이트 수 기록")
    void recordBytesTransferred_ShouldAccumulateBytes_WhenMultipleOperations() {
        // Given
        S3MetricsCollector.OperationType uploadType = S3MetricsCollector.OperationType.UPLOAD;
        long firstUpload = 500 * 1024; // 500KB
        long secondUpload = 1500 * 1024; // 1.5MB

        // When
        metricsCollector.recordBytesTransferred(uploadType, firstUpload);
        metricsCollector.recordBytesTransferred(uploadType, secondUpload);

        // Then
        assertThat(meterRegistry.find("s3.bytes.uploaded").gauge().value())
                .isEqualTo(firstUpload + secondUpload);
    }

    @Test
    @DisplayName("지원하지 않는 작업 타입의 바이트 기록 무시")
    void recordBytesTransferred_ShouldIgnoreUnsupportedOperationType_WhenDeleteOperation() {
        // Given
        S3MetricsCollector.OperationType deleteType = S3MetricsCollector.OperationType.DELETE;
        long bytes = 1024;

        // When
        metricsCollector.recordBytesTransferred(deleteType, bytes);

        // Then
        assertThat(meterRegistry.find("s3.bytes.uploaded").gauge().value()).isEqualTo(0);
        assertThat(meterRegistry.find("s3.bytes.downloaded").gauge().value()).isEqualTo(0);
    }

    @Test
    @DisplayName("오류 기록")
    void recordError_ShouldRecordError_WhenErrorOccurs() {
        // Given
        S3MetricsCollector.OperationType operationType = S3MetricsCollector.OperationType.COPY;
        String errorType = "NoSuchBucket";

        // When
        metricsCollector.recordError(operationType, errorType);

        // Then
        Counter errorCounter = meterRegistry.find("s3.operation.errors")
                .tag("operation", "copy")
                .tag("error_type", "NoSuchBucket")
                .counter();
        
        assertThat(errorCounter).isNotNull();
        assertThat(errorCounter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("지연 시간 기록")
    void recordLatency_ShouldRecordLatency_WhenLatencyProvided() {
        // Given
        S3MetricsCollector.OperationType operationType = S3MetricsCollector.OperationType.LIST;
        Duration latency = Duration.ofMillis(250);

        // When
        metricsCollector.recordLatency(operationType, latency);

        // Then
        Timer latencyTimer = meterRegistry.find("s3.operation.latency")
                .tag("operation", "list")
                .timer();
        
        assertThat(latencyTimer).isNotNull();
        assertThat(latencyTimer.count()).isEqualTo(1);
        assertThat(latencyTimer.totalTime(Duration.ofMillis(1).toNanos())).isGreaterThanOrEqualTo(250);
    }

    @Test
    @DisplayName("메트릭 요약 조회")
    void getMetricsSummary_ShouldReturnSummary_WhenMetricsRecorded() {
        // Given
        // 다양한 작업 기록
        metricsCollector.recordOperationTime(S3MetricsCollector.OperationType.UPLOAD, Duration.ofMillis(100), true);
        metricsCollector.recordOperationTime(S3MetricsCollector.OperationType.UPLOAD, Duration.ofMillis(150), true);
        metricsCollector.recordOperationTime(S3MetricsCollector.OperationType.DOWNLOAD, Duration.ofMillis(200), true);
        metricsCollector.recordOperationTime(S3MetricsCollector.OperationType.DELETE, Duration.ofMillis(50), false);
        
        metricsCollector.recordBytesTransferred(S3MetricsCollector.OperationType.UPLOAD, 1024 * 1024);
        metricsCollector.recordBytesTransferred(S3MetricsCollector.OperationType.DOWNLOAD, 2048 * 1024);

        // When
        S3MetricsCollector.S3MetricsSummary summary = metricsCollector.getMetricsSummary();

        // Then
        assertThat(summary).isNotNull();
        assertThat(summary.getOperationCount("upload")).isEqualTo(2L);
        assertThat(summary.getOperationCount("download")).isEqualTo(1L);
        assertThat(summary.getOperationCount("delete")).isEqualTo(1L);
        assertThat(summary.getTotalOperations()).isEqualTo(4L);
        assertThat(summary.getUploadedBytes()).isEqualTo(1024 * 1024);
        assertThat(summary.getDownloadedBytes()).isEqualTo(2048 * 1024);
        assertThat(summary.getTotalBytesTransferred()).isEqualTo(1024 * 1024 + 2048 * 1024);
    }

    @Test
    @DisplayName("OperationType enum 속성 테스트")
    void operationType_ShouldHaveCorrectProperties_WhenAccessingEnumValues() {
        // Given & When & Then
        S3MetricsCollector.OperationType upload = S3MetricsCollector.OperationType.UPLOAD;
        assertThat(upload.getName()).isEqualTo("upload");
        assertThat(upload.getKoreanName()).isEqualTo("업로드");

        S3MetricsCollector.OperationType download = S3MetricsCollector.OperationType.DOWNLOAD;
        assertThat(download.getName()).isEqualTo("download");
        assertThat(download.getKoreanName()).isEqualTo("다운로드");

        S3MetricsCollector.OperationType delete = S3MetricsCollector.OperationType.DELETE;
        assertThat(delete.getName()).isEqualTo("delete");
        assertThat(delete.getKoreanName()).isEqualTo("삭제");

        S3MetricsCollector.OperationType copy = S3MetricsCollector.OperationType.COPY;
        assertThat(copy.getName()).isEqualTo("copy");
        assertThat(copy.getKoreanName()).isEqualTo("복사");

        S3MetricsCollector.OperationType list = S3MetricsCollector.OperationType.LIST;
        assertThat(list.getName()).isEqualTo("list");
        assertThat(list.getKoreanName()).isEqualTo("목록 조회");

        S3MetricsCollector.OperationType head = S3MetricsCollector.OperationType.HEAD;
        assertThat(head.getName()).isEqualTo("head");
        assertThat(head.getKoreanName()).isEqualTo("메타데이터 조회");

        S3MetricsCollector.OperationType tag = S3MetricsCollector.OperationType.TAG;
        assertThat(tag.getName()).isEqualTo("tag");
        assertThat(tag.getKoreanName()).isEqualTo("태그 작업");
    }

    @Test
    @DisplayName("S3MetricsSummary 기본 동작 테스트")
    void s3MetricsSummary_ShouldHandleEmptyMetrics_WhenNoMetricsRecorded() {
        // Given
        S3MetricsCollector.S3MetricsSummary summary = new S3MetricsCollector.S3MetricsSummary();

        // When & Then
        assertThat(summary.getTotalOperations()).isEqualTo(0L);
        assertThat(summary.getOperationCount("upload")).isEqualTo(0L);
        assertThat(summary.getUploadedBytes()).isEqualTo(0L);
        assertThat(summary.getDownloadedBytes()).isEqualTo(0L);
        assertThat(summary.getTotalBytesTransferred()).isEqualTo(0L);
    }

    @Test
    @DisplayName("S3MetricsSummary toString 메서드 테스트")
    void s3MetricsSummary_ShouldReturnFormattedString_WhenToStringCalled() {
        // Given
        S3MetricsCollector.S3MetricsSummary summary = new S3MetricsCollector.S3MetricsSummary();
        summary.addOperationCount("upload", 5L);
        summary.addOperationCount("download", 3L);
        summary.setUploadedBytes(1024 * 1024); // 1MB
        summary.setDownloadedBytes(2048 * 1024); // 2MB

        // When
        String result = summary.toString();

        // Then
        assertThat(result).contains("총 작업 수=8");
        assertThat(result).contains("업로드=1048576 bytes");
        assertThat(result).contains("다운로드=2097152 bytes");
    }

    @Test
    @DisplayName("여러 작업 타입 동시 기록")
    void multipleOperationTypes_ShouldRecordIndependently_WhenRecordedSimultaneously() {
        // Given
        Duration uploadDuration = Duration.ofMillis(100);
        Duration downloadDuration = Duration.ofMillis(200);
        Duration deleteDuration = Duration.ofMillis(50);

        // When
        metricsCollector.recordOperationTime(S3MetricsCollector.OperationType.UPLOAD, uploadDuration, true);
        metricsCollector.recordOperationTime(S3MetricsCollector.OperationType.DOWNLOAD, downloadDuration, true);
        metricsCollector.recordOperationTime(S3MetricsCollector.OperationType.DELETE, deleteDuration, false);
        
        metricsCollector.recordBytesTransferred(S3MetricsCollector.OperationType.UPLOAD, 1024);
        metricsCollector.recordBytesTransferred(S3MetricsCollector.OperationType.DOWNLOAD, 2048);

        // Then
        Counter uploadCounter = meterRegistry.find("s3.operation.count")
                .tag("operation", "upload")
                .tag("status", "success")
                .counter();
        assertThat(uploadCounter.count()).isEqualTo(1.0);

        Counter downloadCounter = meterRegistry.find("s3.operation.count")
                .tag("operation", "download")
                .tag("status", "success")
                .counter();
        assertThat(downloadCounter.count()).isEqualTo(1.0);

        Counter deleteCounter = meterRegistry.find("s3.operation.count")
                .tag("operation", "delete")
                .tag("status", "failure")
                .counter();
        assertThat(deleteCounter.count()).isEqualTo(1.0);

        assertThat(meterRegistry.find("s3.bytes.uploaded").gauge().value()).isEqualTo(1024);
        assertThat(meterRegistry.find("s3.bytes.downloaded").gauge().value()).isEqualTo(2048);
    }
}