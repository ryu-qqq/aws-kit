package com.ryuqq.aws.s3.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * S3 작업 메트릭 수집기
 * 
 * 한국어 설명:
 * S3 작업의 성능 메트릭을 수집하고 모니터링합니다.
 * Micrometer를 통해 다양한 모니터링 시스템과 통합 가능합니다.
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class S3MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Timer> operationTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> operationCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> bytesTransferred = new ConcurrentHashMap<>();
    
    /**
     * 작업 타입 정의
     */
    public enum OperationType {
        UPLOAD("upload", "업로드"),
        DOWNLOAD("download", "다운로드"),
        DELETE("delete", "삭제"),
        COPY("copy", "복사"),
        LIST("list", "목록 조회"),
        HEAD("head", "메타데이터 조회"),
        TAG("tag", "태그 작업");
        
        private final String name;
        private final String koreanName;
        
        OperationType(String name, String koreanName) {
            this.name = name;
            this.koreanName = koreanName;
        }
        
        public String getName() {
            return name;
        }
        
        public String getKoreanName() {
            return koreanName;
        }
    }
    
    public S3MetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        initializeMetrics();
    }
    
    /**
     * 메트릭 초기화
     */
    private void initializeMetrics() {
        // 각 작업 타입별로 타이머와 카운터 생성
        for (OperationType type : OperationType.values()) {
            String timerName = "s3.operation.duration";
            String counterName = "s3.operation.count";
            
            Timer timer = Timer.builder(timerName)
                    .tag("operation", type.getName())
                    .description("S3 " + type.getKoreanName() + " 작업 소요 시간")
                    .register(meterRegistry);
            operationTimers.put(type.getName(), timer);
            
            Counter counter = Counter.builder(counterName)
                    .tag("operation", type.getName())
                    .description("S3 " + type.getKoreanName() + " 작업 횟수")
                    .register(meterRegistry);
            operationCounters.put(type.getName(), counter);
        }
        
        // 전송 바이트 게이지 등록
        meterRegistry.gauge("s3.bytes.uploaded", bytesTransferred, 
            map -> map.getOrDefault("upload", new AtomicLong(0)).get());
        meterRegistry.gauge("s3.bytes.downloaded", bytesTransferred,
            map -> map.getOrDefault("download", new AtomicLong(0)).get());
    }
    
    /**
     * 작업 시작 시간 기록
     * 
     * @param operationType 작업 타입
     * @return 타이머 샘플
     */
    public Timer.Sample startTimer(OperationType operationType) {
        return Timer.start(meterRegistry);
    }
    
    /**
     * 작업 완료 시간 기록
     * 
     * @param sample 타이머 샘플
     * @param operationType 작업 타입
     * @param success 성공 여부
     */
    public void recordOperation(Timer.Sample sample, OperationType operationType, boolean success) {
        String status = success ? "success" : "failure";
        
        Timer timer = operationTimers.get(operationType.getName());
        if (timer != null && sample != null) {
            sample.stop(Timer.builder("s3.operation.duration")
                    .tag("operation", operationType.getName())
                    .tag("status", status)
                    .register(meterRegistry));
        }
        
        Counter counter = Counter.builder("s3.operation.count")
                .tag("operation", operationType.getName())
                .tag("status", status)
                .register(meterRegistry);
        counter.increment();
        
        if (success) {
            log.debug("S3 {} 작업 완료", operationType.getKoreanName());
        } else {
            log.warn("S3 {} 작업 실패", operationType.getKoreanName());
        }
    }
    
    /**
     * 작업 시간 직접 기록
     * 
     * @param operationType 작업 타입
     * @param duration 소요 시간
     * @param success 성공 여부
     */
    public void recordOperationTime(OperationType operationType, Duration duration, boolean success) {
        String status = success ? "success" : "failure";
        
        Timer timer = Timer.builder("s3.operation.duration")
                .tag("operation", operationType.getName())
                .tag("status", status)
                .register(meterRegistry);
        timer.record(duration);
        
        Counter counter = Counter.builder("s3.operation.count")
                .tag("operation", operationType.getName())
                .tag("status", status)
                .register(meterRegistry);
        counter.increment();
    }
    
    /**
     * 전송된 바이트 수 기록
     * 
     * @param operationType 작업 타입 (UPLOAD 또는 DOWNLOAD)
     * @param bytes 바이트 수
     */
    public void recordBytesTransferred(OperationType operationType, long bytes) {
        if (operationType == OperationType.UPLOAD || operationType == OperationType.DOWNLOAD) {
            bytesTransferred.computeIfAbsent(operationType.getName(), k -> new AtomicLong(0))
                    .addAndGet(bytes);
            
            log.debug("S3 {} 바이트 전송: {} bytes", operationType.getKoreanName(), bytes);
        }
    }
    
    /**
     * 오류 발생 기록
     * 
     * @param operationType 작업 타입
     * @param errorType 오류 타입
     */
    public void recordError(OperationType operationType, String errorType) {
        Counter errorCounter = Counter.builder("s3.operation.errors")
                .tag("operation", operationType.getName())
                .tag("error_type", errorType)
                .description("S3 작업 오류 횟수")
                .register(meterRegistry);
        errorCounter.increment();
        
        log.error("S3 {} 작업 오류 발생: {}", operationType.getKoreanName(), errorType);
    }
    
    /**
     * 작업 지연 시간 기록 (큐 대기 시간 등)
     * 
     * @param operationType 작업 타입
     * @param latency 지연 시간
     */
    public void recordLatency(OperationType operationType, Duration latency) {
        Timer latencyTimer = Timer.builder("s3.operation.latency")
                .tag("operation", operationType.getName())
                .description("S3 작업 지연 시간")
                .register(meterRegistry);
        latencyTimer.record(latency);
    }
    
    /**
     * 현재 메트릭 상태 조회
     * 
     * @return 메트릭 요약 정보
     */
    public S3MetricsSummary getMetricsSummary() {
        S3MetricsSummary summary = new S3MetricsSummary();
        
        for (OperationType type : OperationType.values()) {
            Counter counter = operationCounters.get(type.getName());
            if (counter != null) {
                summary.addOperationCount(type.getName(), (long) counter.count());
            }
        }
        
        summary.setUploadedBytes(bytesTransferred.getOrDefault("upload", new AtomicLong(0)).get());
        summary.setDownloadedBytes(bytesTransferred.getOrDefault("download", new AtomicLong(0)).get());
        
        return summary;
    }
    
    /**
     * 메트릭 요약 정보
     */
    public static class S3MetricsSummary {
        private final ConcurrentHashMap<String, Long> operationCounts = new ConcurrentHashMap<>();
        private long uploadedBytes;
        private long downloadedBytes;
        
        public void addOperationCount(String operation, long count) {
            operationCounts.put(operation, count);
        }
        
        public long getOperationCount(String operation) {
            return operationCounts.getOrDefault(operation, 0L);
        }
        
        public long getTotalOperations() {
            return operationCounts.values().stream().mapToLong(Long::longValue).sum();
        }
        
        public long getUploadedBytes() {
            return uploadedBytes;
        }
        
        public void setUploadedBytes(long uploadedBytes) {
            this.uploadedBytes = uploadedBytes;
        }
        
        public long getDownloadedBytes() {
            return downloadedBytes;
        }
        
        public void setDownloadedBytes(long downloadedBytes) {
            this.downloadedBytes = downloadedBytes;
        }
        
        public long getTotalBytesTransferred() {
            return uploadedBytes + downloadedBytes;
        }
        
        @Override
        public String toString() {
            return String.format(
                "S3 메트릭 요약: 총 작업 수=%d, 업로드=%d bytes, 다운로드=%d bytes",
                getTotalOperations(), uploadedBytes, downloadedBytes
            );
        }
    }
}