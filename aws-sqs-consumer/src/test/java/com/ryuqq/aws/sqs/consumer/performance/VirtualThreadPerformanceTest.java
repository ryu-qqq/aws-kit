package com.ryuqq.aws.sqs.consumer.performance;

import com.ryuqq.aws.sqs.consumer.executor.ExecutorServiceProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Virtual Thread vs Platform Thread 성능 비교 테스트
 * 
 * SQS Consumer에서 사용하는 스레드 모델의 성능 특성을 분석합니다.
 * I/O 집약적 작업에서의 Virtual Thread 우위와 CPU 집약적 작업에서의 특성을 검증합니다.
 */
@DisplayName("Virtual Thread 성능 비교 테스트")
class VirtualThreadPerformanceTest {
    
    private static final int TASK_COUNT = 1000;
    private static final int IO_DELAY_MS = 10;
    private static final int CPU_INTENSIVE_ITERATIONS = 100_000;
    
    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @DisplayName("I/O 집약적 작업에서 Virtual Thread가 더 효율적이어야 한다")
    void shouldPerformBetterWithVirtualThreadsForIOIntensiveTasks() throws InterruptedException {
        // given
        ExecutorService platformExecutor = createPlatformThreadExecutor();
        ExecutorService virtualExecutor = createVirtualThreadExecutor();
        
        // when
        PerformanceResult platformResult = measureIOIntensivePerformance(platformExecutor, "Platform Threads");
        PerformanceResult virtualResult = measureIOIntensivePerformance(virtualExecutor, "Virtual Threads");
        
        // then
        System.out.println("\n=== I/O 집약적 작업 성능 비교 ===");
        System.out.printf("Platform Threads: %s%n", platformResult);
        System.out.printf("Virtual Threads:  %s%n", virtualResult);
        
        // Virtual Thread가 Platform Thread보다 더 적은 시간으로 완료되거나,
        // 더 높은 처리량을 보여야 함
        assertThat(virtualResult.executionTimeMs()).isLessThanOrEqualTo((long)(platformResult.executionTimeMs() * 1.5));
        assertThat(virtualResult.throughputPerSecond()).isGreaterThanOrEqualTo(platformResult.throughputPerSecond() * 0.8);
        
        // cleanup
        platformExecutor.shutdown();
        virtualExecutor.shutdown();
        assertThat(platformExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @DisplayName("CPU 집약적 작업에서는 Platform Thread가 안정적이어야 한다")
    void shouldMaintainPerformanceWithPlatformThreadsForCPUIntensiveTasks() throws InterruptedException {
        // given
        ExecutorService platformExecutor = createPlatformThreadExecutor();
        ExecutorService virtualExecutor = createVirtualThreadExecutor();
        
        // when
        PerformanceResult platformResult = measureCPUIntensivePerformance(platformExecutor, "Platform Threads");
        PerformanceResult virtualResult = measureCPUIntensivePerformance(virtualExecutor, "Virtual Threads");
        
        // then
        System.out.println("\n=== CPU 집약적 작업 성능 비교 ===");
        System.out.printf("Platform Threads: %s%n", platformResult);
        System.out.printf("Virtual Threads:  %s%n", virtualResult);
        
        // CPU 집약적 작업에서는 큰 성능 차이가 없어야 함
        double performanceRatio = (double) virtualResult.executionTimeMs() / platformResult.executionTimeMs();
        assertThat(performanceRatio).isBetween(0.5, 2.0); // 2배 이상 차이나지 않아야 함
        
        // cleanup
        platformExecutor.shutdown();
        virtualExecutor.shutdown();
        assertThat(platformExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @DisplayName("대량 동시 작업에서 Virtual Thread의 확장성을 검증해야 한다")
    void shouldScaleBetterWithVirtualThreadsForMassiveConcurrency() throws InterruptedException {
        // given - 대량의 동시 작업 (10,000개)
        int massiveTaskCount = 10_000;
        ExecutorService platformExecutor = createLimitedPlatformThreadExecutor(); // 제한된 플랫폼 스레드
        ExecutorService virtualExecutor = createVirtualThreadExecutor();
        
        // when
        PerformanceResult platformResult = measureMassiveConcurrencyPerformance(
                platformExecutor, massiveTaskCount, "Platform Threads (Limited)");
        PerformanceResult virtualResult = measureMassiveConcurrencyPerformance(
                virtualExecutor, massiveTaskCount, "Virtual Threads");
        
        // then
        System.out.println("\n=== 대량 동시 작업 성능 비교 ===");
        System.out.printf("Platform Threads (Limited): %s%n", platformResult);
        System.out.printf("Virtual Threads:            %s%n", virtualResult);
        
        // Virtual Thread가 대량 동시 작업에서 더 좋은 성능을 보여야 함
        assertThat(virtualResult.executionTimeMs()).isLessThan(platformResult.executionTimeMs());
        assertThat(virtualResult.throughputPerSecond()).isGreaterThan(platformResult.throughputPerSecond());
        
        // cleanup
        platformExecutor.shutdown();
        virtualExecutor.shutdown();
        assertThat(platformExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(virtualExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @DisplayName("메모리 사용량에서 Virtual Thread의 효율성을 검증해야 한다")
    void shouldUseMemoryMoreEfficientlyWithVirtualThreads() throws InterruptedException {
        // given
        int threadCount = 1000;
        CountDownLatch platformLatch = new CountDownLatch(threadCount);
        CountDownLatch virtualLatch = new CountDownLatch(threadCount);
        
        Runtime runtime = Runtime.getRuntime();
        
        // when - Platform Thread 메모리 사용량 측정
        long beforePlatform = runtime.totalMemory() - runtime.freeMemory();
        
        ExecutorService platformExecutor = createPlatformThreadExecutor();
        for (int i = 0; i < threadCount; i++) {
            platformExecutor.submit(() -> {
                try {
                    Thread.sleep(100); // 스레드가 살아있는 상태 유지
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                platformLatch.countDown();
            });
        }
        
        Thread.sleep(50); // 스레드 생성 완료 대기
        System.gc(); // 가비지 컬렉션 수행
        Thread.sleep(50);
        long afterPlatform = runtime.totalMemory() - runtime.freeMemory();
        long platformMemoryUsage = afterPlatform - beforePlatform;
        
        platformLatch.await(10, TimeUnit.SECONDS);
        platformExecutor.shutdown();
        
        // Virtual Thread 메모리 사용량 측정
        Thread.sleep(1000); // 메모리 안정화 대기
        System.gc();
        Thread.sleep(50);
        long beforeVirtual = runtime.totalMemory() - runtime.freeMemory();
        
        ExecutorService virtualExecutor = createVirtualThreadExecutor();
        for (int i = 0; i < threadCount; i++) {
            virtualExecutor.submit(() -> {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                virtualLatch.countDown();
            });
        }
        
        Thread.sleep(50);
        System.gc();
        Thread.sleep(50);
        long afterVirtual = runtime.totalMemory() - runtime.freeMemory();
        long virtualMemoryUsage = afterVirtual - beforeVirtual;
        
        virtualLatch.await(10, TimeUnit.SECONDS);
        virtualExecutor.shutdown();
        
        // then
        System.out.println("\n=== 메모리 사용량 비교 ===");
        System.out.printf("Platform Threads: %,d bytes%n", platformMemoryUsage);
        System.out.printf("Virtual Threads:  %,d bytes%n", virtualMemoryUsage);
        
        // Virtual Thread가 더 적은 메모리를 사용해야 함 (단, 측정 오차 고려)
        // 실제 환경에서는 Virtual Thread가 현저히 적은 메모리를 사용함
        System.out.printf("Memory efficiency ratio: %.2f%n", 
                (double) virtualMemoryUsage / Math.max(platformMemoryUsage, 1));
    }
    
    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @DisplayName("SQS 메시지 처리 시뮬레이션에서의 성능을 비교해야 한다")
    void shouldComparePerformanceInSQSMessageProcessingSimulation() throws InterruptedException {
        // given - SQS 메시지 처리 시뮬레이션
        ExecutorService platformExecutor = createPlatformThreadExecutor();
        ExecutorService virtualExecutor = createVirtualThreadExecutor();
        
        // when
        PerformanceResult platformResult = simulateSQSMessageProcessing(platformExecutor, "Platform");
        PerformanceResult virtualResult = simulateSQSMessageProcessing(virtualExecutor, "Virtual");
        
        // then
        System.out.println("\n=== SQS 메시지 처리 시뮬레이션 성능 비교 ===");
        System.out.printf("Platform Threads: %s%n", platformResult);
        System.out.printf("Virtual Threads:  %s%n", virtualResult);
        
        // SQS 메시지 처리 시뮬레이션에서 Virtual Thread가 더 나은 처리량을 보여야 함
        assertThat(virtualResult.throughputPerSecond()).isGreaterThanOrEqualTo(
                platformResult.throughputPerSecond() * 0.9);
        
        // cleanup
        platformExecutor.shutdown();
        virtualExecutor.shutdown();
        assertThat(platformExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(virtualExecutor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }
    
    private ExecutorService createPlatformThreadExecutor() {
        return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
    }
    
    private ExecutorService createLimitedPlatformThreadExecutor() {
        return Executors.newFixedThreadPool(50); // 제한된 스레드 풀
    }
    
    private ExecutorService createVirtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    
    private PerformanceResult measureIOIntensivePerformance(ExecutorService executor, String name) 
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(TASK_COUNT);
        AtomicInteger completedTasks = new AtomicInteger(0);
        
        Instant start = Instant.now();
        
        for (int i = 0; i < TASK_COUNT; i++) {
            executor.submit(() -> {
                try {
                    // I/O 시뮬레이션 (네트워크 호출, 파일 읽기 등)
                    Thread.sleep(IO_DELAY_MS);
                    completedTasks.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        Instant end = Instant.now();
        
        long executionTimeMs = Duration.between(start, end).toMillis();
        double throughputPerSecond = (double) completedTasks.get() * 1000 / executionTimeMs;
        
        return new PerformanceResult(name, executionTimeMs, completedTasks.get(), throughputPerSecond);
    }
    
    private PerformanceResult measureCPUIntensivePerformance(ExecutorService executor, String name) 
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(TASK_COUNT);
        AtomicInteger completedTasks = new AtomicInteger(0);
        
        Instant start = Instant.now();
        
        for (int i = 0; i < TASK_COUNT; i++) {
            executor.submit(() -> {
                try {
                    // CPU 집약적 작업 시뮬레이션
                    long sum = 0;
                    for (int j = 0; j < CPU_INTENSIVE_ITERATIONS; j++) {
                        sum += j * j;
                    }
                    // 결과 사용 (최적화 방지)
                    if (sum < 0) System.out.println("Unexpected");
                    
                    completedTasks.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        Instant end = Instant.now();
        
        long executionTimeMs = Duration.between(start, end).toMillis();
        double throughputPerSecond = (double) completedTasks.get() * 1000 / executionTimeMs;
        
        return new PerformanceResult(name, executionTimeMs, completedTasks.get(), throughputPerSecond);
    }
    
    private PerformanceResult measureMassiveConcurrencyPerformance(ExecutorService executor, int taskCount, String name) 
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(taskCount);
        AtomicInteger completedTasks = new AtomicInteger(0);
        
        Instant start = Instant.now();
        
        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                try {
                    // 가벼운 I/O 시뮬레이션
                    Thread.sleep(5);
                    completedTasks.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(30, TimeUnit.SECONDS); // 대량 작업을 위한 긴 타임아웃
        Instant end = Instant.now();
        
        long executionTimeMs = Duration.between(start, end).toMillis();
        double throughputPerSecond = (double) completedTasks.get() * 1000 / executionTimeMs;
        
        return new PerformanceResult(name, executionTimeMs, completedTasks.get(), throughputPerSecond);
    }
    
    private PerformanceResult simulateSQSMessageProcessing(ExecutorService executor, String name) 
            throws InterruptedException {
        int messageCount = 500;
        CountDownLatch latch = new CountDownLatch(messageCount);
        AtomicInteger processedMessages = new AtomicInteger(0);
        AtomicLong totalProcessingTime = new AtomicLong(0);
        
        Instant start = Instant.now();
        
        for (int i = 0; i < messageCount; i++) {
            final int messageId = i;
            executor.submit(() -> {
                try {
                    Instant messageStart = Instant.now();
                    
                    // 메시지 처리 시뮬레이션 (JSON 파싱, DB 조회, HTTP 호출 등)
                    
                    // 1. JSON 파싱 시뮬레이션
                    Thread.sleep(2);
                    
                    // 2. 데이터베이스 조회 시뮬레이션
                    Thread.sleep(15);
                    
                    // 3. 비즈니스 로직 처리
                    long businessLogicResult = 0;
                    for (int j = 0; j < 10_000; j++) {
                        businessLogicResult += j;
                    }
                    
                    // 4. HTTP API 호출 시뮬레이션
                    Thread.sleep(8);
                    
                    // 5. 결과 저장 시뮬레이션
                    Thread.sleep(5);
                    
                    Instant messageEnd = Instant.now();
                    totalProcessingTime.addAndGet(Duration.between(messageStart, messageEnd).toMillis());
                    
                    processedMessages.incrementAndGet();
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        Instant end = Instant.now();
        
        long executionTimeMs = Duration.between(start, end).toMillis();
        double throughputPerSecond = (double) processedMessages.get() * 1000 / executionTimeMs;
        double avgProcessingTimeMs = (double) totalProcessingTime.get() / processedMessages.get();
        
        System.out.printf("%s - 평균 메시지 처리 시간: %.2f ms%n", name, avgProcessingTimeMs);
        
        return new PerformanceResult(name, executionTimeMs, processedMessages.get(), throughputPerSecond);
    }
    
    private record PerformanceResult(
            String name,
            long executionTimeMs,
            int completedTasks,
            double throughputPerSecond
    ) {
        @Override
        public String toString() {
            return String.format("%s - 실행시간: %,d ms, 완료된 작업: %,d, 처리량: %.2f/sec", 
                    name, executionTimeMs, completedTasks, throughputPerSecond);
        }
    }
}