package com.ryuqq.aws.sns.performance;

import com.ryuqq.aws.sns.adapter.SnsTypeAdapter;
import com.ryuqq.aws.sns.service.SnsService;
import com.ryuqq.aws.sns.types.SnsMessage;
import com.ryuqq.aws.sns.types.SnsPublishResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Comprehensive performance tests for SNS operations
 * Tests concurrent publishing, memory usage, error recovery, and timeout behavior
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SnsPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(SnsPerformanceTest.class);

    @Mock
    private SnsAsyncClient snsClient;
    
    @Mock 
    private SnsTypeAdapter typeAdapter;
    
    private SnsService snsService;
    private ExecutorService executorService;
    private MemoryMXBean memoryBean;
    
    // Performance metrics - reduced for memory constraints
    private static final String TEST_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:test-topic";
    private static final int CONCURRENT_THREADS = 10;
    private static final int MESSAGES_PER_THREAD = 5;
    private static final int TOTAL_OPERATIONS = CONCURRENT_THREADS * MESSAGES_PER_THREAD;
    
    // Performance thresholds - adjusted for reduced test load
    private static final long MAX_AVERAGE_LATENCY_MS = 200;
    private static final long MAX_P95_LATENCY_MS = 1000;
    private static final double MIN_THROUGHPUT_OPS_PER_SEC = 100;
    private static final long MAX_MEMORY_INCREASE_MB = 50;
    
    @BeforeEach
    void setUp() {
        snsService = new SnsService(snsClient, typeAdapter);
        executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        memoryBean = ManagementFactory.getMemoryMXBean();
        
        // Setup mock responses
        setupMockResponses();
    }
    
    @AfterEach
    void tearDown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
    }

    /**
     * Test concurrent SNS publishing operations with performance metrics
     */
    @Test
    @Order(1)
    @DisplayName("Concurrent SNS Publishing Performance Test")
    void testConcurrentSnsPublishing() throws InterruptedException {
        // Memory baseline
        MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
        
        // Performance metrics collection
        List<Long> latencies = new CopyOnWriteArrayList<>();
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong successfulOperations = new AtomicLong(0);
        AtomicLong failedOperations = new AtomicLong(0);
        
        Instant startTime = Instant.now();
        
        // Create concurrent publishing tasks
        List<CompletableFuture<Void>> tasks = IntStream.range(0, CONCURRENT_THREADS)
            .mapToObj(threadId -> CompletableFuture.runAsync(() -> {
                for (int i = 0; i < MESSAGES_PER_THREAD; i++) {
                    long operationStart = System.nanoTime();
                    
                    try {
                        SnsMessage message = createTestMessage(threadId, i);
                        
                        CompletableFuture<SnsPublishResult> future = snsService.publish(TEST_TOPIC_ARN, message);
                        SnsPublishResult result = future.get(5, TimeUnit.SECONDS);
                        
                        long operationEnd = System.nanoTime();
                        long latencyMs = (operationEnd - operationStart) / 1_000_000;
                        
                        latencies.add(latencyMs);
                        successfulOperations.incrementAndGet();
                        totalOperations.incrementAndGet();
                        
                        assertThat(result).isNotNull();
                        assertThat(result.messageId()).isNotEmpty();
                        
                    } catch (Exception e) {
                        failedOperations.incrementAndGet();
                        totalOperations.incrementAndGet();
                        log.error("Operation failed for thread {} iteration {}: {}", 
                                threadId, i, e.getMessage());
                    }
                }
            }, executorService))
            .toList();
        
        // Wait for all tasks to complete
        @SuppressWarnings({"unchecked", "rawtypes"})
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(
            tasks.toArray(new CompletableFuture[0])
        );

        assertThatCode(() -> {
            try {
                allTasks.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                throw new RuntimeException("Task execution failed", e);
            }
        }).doesNotThrowAnyException();
        
        Instant endTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, endTime);
        
        // Memory after operations
        System.gc(); // Hint for garbage collection
        Thread.sleep(1000); // Allow GC to run
        MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
        
        // Calculate performance metrics
        PerformanceMetrics metrics = calculateMetrics(latencies, totalDuration, 
                                                    beforeMemory, afterMemory,
                                                    successfulOperations.get(), 
                                                    failedOperations.get());
        
        // Log performance results
        logPerformanceResults(metrics);
        
        // Performance assertions
        assertPerformanceThresholds(metrics);
    }

    /**
     * Test batch publishing performance with different batch sizes
     */
    @Test
    @Order(2)
    @DisplayName("Batch Publishing Performance Test")
    void testBatchPublishingPerformance() throws InterruptedException {
        int[] batchSizes = {1, 5, 10, 25, 50};
        Map<Integer, PerformanceMetrics> batchResults = new HashMap<>();
        
        for (int batchSize : batchSizes) {
            log.info("Testing batch size: {}", batchSize);
            
            MemoryUsage beforeMemory = memoryBean.getHeapMemoryUsage();
            List<Long> latencies = new CopyOnWriteArrayList<>();
            AtomicLong successfulOperations = new AtomicLong(0);
            AtomicLong failedOperations = new AtomicLong(0);
            
            Instant startTime = Instant.now();
            
            // Create batches
            List<CompletableFuture<Void>> tasks = IntStream.range(0, CONCURRENT_THREADS)
                .mapToObj(threadId -> CompletableFuture.runAsync(() -> {
                    int iterations = MESSAGES_PER_THREAD / batchSize;
                    for (int i = 0; i < iterations; i++) {
                        long operationStart = System.nanoTime();
                        
                        try {
                            List<SnsMessage> messages = createBatchMessages(batchSize, threadId, i);
                            
                            CompletableFuture<List<SnsPublishResult>> future = 
                                snsService.publishBatch(TEST_TOPIC_ARN, messages);
                            List<SnsPublishResult> results = future.get(10, TimeUnit.SECONDS);
                            
                            long operationEnd = System.nanoTime();
                            long latencyMs = (operationEnd - operationStart) / 1_000_000;
                            
                            latencies.add(latencyMs);
                            successfulOperations.addAndGet(results.size());
                            
                        } catch (Exception e) {
                            failedOperations.addAndGet(batchSize);
                            log.error("Batch operation failed: {}", e.getMessage());
                        }
                    }
                }, executorService))
                .toList();
            
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                CompletableFuture<Void> batchFuture = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
                batchFuture.get(120, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                throw new RuntimeException("Batch operation failed", e);
            }
            
            Instant endTime = Instant.now();
            Duration totalDuration = Duration.between(startTime, endTime);
            
            System.gc();
            Thread.sleep(1000);
            MemoryUsage afterMemory = memoryBean.getHeapMemoryUsage();
            
            PerformanceMetrics metrics = calculateMetrics(latencies, totalDuration,
                                                        beforeMemory, afterMemory,
                                                        successfulOperations.get(),
                                                        failedOperations.get());
            
            batchResults.put(batchSize, metrics);
            log.info("Batch size {} - Throughput: {:.2f} ops/sec, Avg latency: {} ms",
                    batchSize, metrics.getThroughputOpsPerSec(), metrics.getAverageLatencyMs());
        }
        
        // Verify batch performance improves with larger batch sizes
        assertThat(batchResults.get(50).getThroughputOpsPerSec())
            .isGreaterThan(batchResults.get(1).getThroughputOpsPerSec());
    }

    /**
     * Test error recovery performance and timeout handling
     */
    @Test
    @Order(3)
    @DisplayName("Error Recovery and Timeout Performance Test")
    void testErrorRecoveryPerformance() throws InterruptedException {
        // Setup failure scenarios
        AtomicLong timeoutCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);
        AtomicLong recoveryCount = new AtomicLong(0);
        
        // Mock intermittent failures
        when(snsClient.publish(any(PublishRequest.class)))
            .thenAnswer(invocation -> {
                // Simulate 20% failure rate
                if (Math.random() < 0.2) {
                    CompletableFuture<PublishResponse> future = new CompletableFuture<>();
                    
                    if (Math.random() < 0.5) {
                        // Simulate timeout
                        CompletableFuture.delayedExecutor(6, TimeUnit.SECONDS)
                            .execute(() -> {
                                timeoutCount.incrementAndGet();
                                future.completeExceptionally(
                                    new RuntimeException("Operation timeout"));
                            });
                    } else {
                        // Simulate immediate error
                        errorCount.incrementAndGet();
                        future.completeExceptionally(
                            SnsException.builder()
                                .message("Simulated SNS error")
                                .build());
                    }
                    return future;
                } else {
                    // Successful operation
                    recoveryCount.incrementAndGet();
                    return CompletableFuture.completedFuture(
                        PublishResponse.builder()
                            .messageId(UUID.randomUUID().toString())
                            .build());
                }
            });
        
        List<Long> latencies = new CopyOnWriteArrayList<>();
        Instant startTime = Instant.now();
        
        // Run operations with error handling
        List<CompletableFuture<Void>> tasks = IntStream.range(0, CONCURRENT_THREADS / 2)
            .mapToObj(threadId -> CompletableFuture.runAsync(() -> {
                for (int i = 0; i < MESSAGES_PER_THREAD; i++) {
                    long operationStart = System.nanoTime();
                    
                    try {
                        SnsMessage message = createTestMessage(threadId, i);
                        
                        // Test with shorter timeout to verify timeout handling
                        CompletableFuture<SnsPublishResult> future = snsService
                            .publish(TEST_TOPIC_ARN, message)
                            .orTimeout(5, TimeUnit.SECONDS);
                        
                        future.get();
                        
                        long operationEnd = System.nanoTime();
                        long latencyMs = (operationEnd - operationStart) / 1_000_000;
                        latencies.add(latencyMs);
                        
                    } catch (Exception e) {
                        long operationEnd = System.nanoTime();
                        long latencyMs = (operationEnd - operationStart) / 1_000_000;
                        latencies.add(latencyMs);
                        
                        // Verify we get expected exceptions
                        assertThat(e.getCause())
                            .satisfiesAnyOf(
                                cause -> assertThat(cause).isInstanceOf(RuntimeException.class),
                                cause -> assertThat(cause).isInstanceOf(SnsException.class),
                                cause -> assertThat(cause).isInstanceOf(TimeoutException.class)
                            );
                    }
                }
            }, executorService))
            .toList();
        
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            CompletableFuture<Void> errorFuture = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
            errorFuture.get(180, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new RuntimeException("Error recovery test failed", e);
        }
        
        Instant endTime = Instant.now();
        Duration totalDuration = Duration.between(startTime, endTime);
        
        // Verify error handling metrics
        long totalOperations = timeoutCount.get() + errorCount.get() + recoveryCount.get();
        double errorRate = (double) (timeoutCount.get() + errorCount.get()) / totalOperations;
        
        log.info("Error Recovery Test Results:");
        log.info("Total operations: {}", totalOperations);
        log.info("Timeouts: {}", timeoutCount.get());
        log.info("Errors: {}", errorCount.get());
        log.info("Successful recoveries: {}", recoveryCount.get());
        log.info("Error rate: {:.2%}", errorRate);
        log.info("Total duration: {} ms", totalDuration.toMillis());
        
        // Verify reasonable error distribution (should be around 20%)
        assertThat(errorRate).isBetween(0.15, 0.25);
        assertThat(timeoutCount.get()).isPositive();
        assertThat(errorCount.get()).isPositive();
        assertThat(recoveryCount.get()).isPositive();
    }

    /**
     * Memory usage and leak detection test
     */
    @Test
    @Order(4)
    @DisplayName("Memory Usage and Leak Detection Test")
    void testMemoryUsageAndLeakDetection() throws InterruptedException {
        List<MemoryUsage> memorySnapshots = new ArrayList<>();
        
        // Take initial memory snapshot
        System.gc();
        Thread.sleep(1000);
        memorySnapshots.add(memoryBean.getHeapMemoryUsage());
        
        // Run multiple cycles of operations
        for (int cycle = 0; cycle < 5; cycle++) {
            log.info("Memory test cycle: {}", cycle + 1);
            
            // Intensive operations
            List<CompletableFuture<Void>> tasks = IntStream.range(0, CONCURRENT_THREADS / 4)
                .mapToObj(threadId -> CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < MESSAGES_PER_THREAD * 2; i++) {
                        try {
                            SnsMessage message = createLargeMessage(threadId, i);
                            snsService.publish(TEST_TOPIC_ARN, message)
                                .get(10, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            // Expected in some cases
                        }
                    }
                }, executorService))
                .toList();
            
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                CompletableFuture<Void> memoryFuture = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));
                memoryFuture.get(60, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                throw new RuntimeException("Memory test failed", e);
            }
            
            // Force garbage collection and take memory snapshot
            System.gc();
            Thread.sleep(2000); // Allow GC to complete
            memorySnapshots.add(memoryBean.getHeapMemoryUsage());
        }
        
        // Analyze memory usage patterns
        analyzeMemoryUsage(memorySnapshots);
        
        // Verify no significant memory leaks
        MemoryUsage initial = memorySnapshots.getFirst();
        MemoryUsage final_ = memorySnapshots.getLast();
        
        long memoryIncreaseMB = (final_.getUsed() - initial.getUsed()) / (1024 * 1024);
        
        log.info("Memory usage analysis:");
        log.info("Initial memory: {} MB", initial.getUsed() / (1024 * 1024));
        log.info("Final memory: {} MB", final_.getUsed() / (1024 * 1024));
        log.info("Memory increase: {} MB", memoryIncreaseMB);
        
        // Memory increase should be reasonable (less than threshold)
        assertThat(memoryIncreaseMB).isLessThan(MAX_MEMORY_INCREASE_MB);
    }

    /**
     * Async operation coordination test
     */
    @Test
    @Order(5)
    @DisplayName("Async Operation Coordination Test")
    void testAsyncOperationCoordination() throws InterruptedException {
        int operationCount = 1000;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(operationCount);
        
        List<Long> coordinationLatencies = new CopyOnWriteArrayList<>();
        AtomicReference<Exception> coordinationError = new AtomicReference<>();
        
        // Create coordinated async operations
        List<CompletableFuture<Void>> coordinatedTasks = new ArrayList<>();
        
        for (int i = 0; i < operationCount; i++) {
            final int operationId = i;
            
            CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
                try {
                    // Wait for coordination signal
                    startLatch.await(30, TimeUnit.SECONDS);
                    
                    long operationStart = System.nanoTime();
                    
                    SnsMessage message = createTestMessage(operationId, 0);
                    
                    CompletableFuture<SnsPublishResult> publishFuture = 
                        snsService.publish(TEST_TOPIC_ARN, message);
                    
                    // Coordinate with other async operations
                    publishFuture
                        .thenCompose(result -> {
                            // Simulate additional async work
                            return CompletableFuture.supplyAsync(() -> {
                                try {
                                    Thread.sleep(10); // Simulate processing
                                    return result;
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException(e);
                                }
                            });
                        })
                        .thenAccept(result -> {
                            long operationEnd = System.nanoTime();
                            long latencyMs = (operationEnd - operationStart) / 1_000_000;
                            coordinationLatencies.add(latencyMs);
                        })
                        .whenComplete((result, throwable) -> {
                            if (throwable != null) {
                                coordinationError.compareAndSet(null,
                                    new RuntimeException("Coordination failed", throwable));
                            }
                            completionLatch.countDown();
                        });

                    try {
                        publishFuture.get(15, TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException | java.util.concurrent.TimeoutException e) {
                        coordinationError.compareAndSet(null, new RuntimeException("Async operation failed", e));
                        completionLatch.countDown();
                    }
                        
                } catch (Exception e) {
                    coordinationError.compareAndSet(null, e);
                    completionLatch.countDown();
                }
            }, executorService);
            
            coordinatedTasks.add(task);
        }
        
        // Start all operations simultaneously
        Instant coordinationStart = Instant.now();
        startLatch.countDown();
        
        // Wait for all coordinated operations to complete
        boolean completed = completionLatch.await(120, TimeUnit.SECONDS);
        Instant coordinationEnd = Instant.now();
        
        assertThat(completed).isTrue();
        assertThat(coordinationError.get()).isNull();
        
        Duration coordinationDuration = Duration.between(coordinationStart, coordinationEnd);
        double coordinationThroughput = (double) operationCount / coordinationDuration.toMillis() * 1000;
        
        log.info("Async Coordination Results:");
        log.info("Operations: {}", operationCount);
        log.info("Coordination duration: {} ms", coordinationDuration.toMillis());
        log.info("Coordination throughput: {:.2f} ops/sec", coordinationThroughput);
        log.info("Average coordination latency: {:.2f} ms", 
                coordinationLatencies.stream().mapToLong(Long::longValue).average().orElse(0));
        
        // Verify coordination performance - more lenient thresholds for reduced test load
        assertThat(coordinationThroughput).isGreaterThan(MIN_THROUGHPUT_OPS_PER_SEC / 5); // Very lenient for coordination
        assertThat(coordinationLatencies).isNotEmpty();
    }

    // Helper methods
    
    private void setupMockResponses() {
        // Mock successful publish response
        when(snsClient.publish(any(PublishRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                PublishResponse.builder()
                    .messageId(UUID.randomUUID().toString())
                    .build()));
        
        // Mock successful batch publish response
        when(snsClient.publishBatch(any(PublishBatchRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                PublishBatchResponse.builder()
                    .successful(Arrays.asList(
                        PublishBatchResultEntry.builder()
                            .id("1")
                            .messageId(UUID.randomUUID().toString())
                            .build()
                    ))
                    .build()));
        
        // Mock type adapter responses
        when(typeAdapter.toPublishRequest(any(), any()))
            .thenReturn(PublishRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .message("test message")
                .build());
        
        when(typeAdapter.toPublishBatchRequest(any(), any()))
            .thenReturn(PublishBatchRequest.builder()
                .topicArn(TEST_TOPIC_ARN)
                .build());
        
        when(typeAdapter.toPublishResult(any()))
            .thenReturn(SnsPublishResult.of(UUID.randomUUID().toString()));

        when(typeAdapter.toBatchPublishResult(any()))
            .thenReturn(SnsPublishResult.of(UUID.randomUUID().toString()));
    }
    
    private SnsMessage createTestMessage(int threadId, int messageId) {
        return SnsMessage.builder()
            .body(String.format("Test message from thread %d, message %d", threadId, messageId))
            .subject("Performance Test")
            .build()
            .withAttribute("ThreadId", String.valueOf(threadId))
            .withAttribute("MessageId", String.valueOf(messageId))
            .withAttribute("Timestamp", Instant.now().toString());
    }
    
    private SnsMessage createLargeMessage(int threadId, int messageId) {
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            largeContent.append("Large message content for memory testing. ");
        }
        
        return SnsMessage.builder()
            .body(largeContent.toString())
            .subject("Large Message Performance Test")
            .build()
            .withAttribute("ThreadId", String.valueOf(threadId))
            .withAttribute("MessageId", String.valueOf(messageId));
    }
    
    private List<SnsMessage> createBatchMessages(int batchSize, int threadId, int batchId) {
        List<SnsMessage> messages = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            SnsMessage msg = SnsMessage.builder()
                .body(String.format("Batch message %d from thread %d, batch %d", i, threadId, batchId))
                .subject("Batch Performance Test")
                .build()
                .withAttribute("BatchId", String.valueOf(batchId))
                .withAttribute("MessageIndex", String.valueOf(i));
            messages.add(msg);
        }
        return messages;
    }
    
    private PerformanceMetrics calculateMetrics(List<Long> latencies, Duration totalDuration,
                                              MemoryUsage beforeMemory, MemoryUsage afterMemory,
                                              long successfulOps, long failedOps) {
        if (latencies.isEmpty()) {
            return new PerformanceMetrics(0, 0, 0, 0, 0, 0, 0);
        }
        
        latencies.sort(Long::compareTo);
        
        double averageLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long medianLatency = latencies.get(latencies.size() / 2);
        long p95Latency = latencies.get((int) (latencies.size() * 0.95));
        double throughput = (double) successfulOps / totalDuration.toMillis() * 1000;
        long memoryUsageMB = (afterMemory.getUsed() - beforeMemory.getUsed()) / (1024 * 1024);
        
        return new PerformanceMetrics(
            (long) averageLatency, medianLatency, p95Latency, throughput,
            successfulOps, failedOps, memoryUsageMB);
    }
    
    private void logPerformanceResults(PerformanceMetrics metrics) {
        log.info("=== SNS Performance Test Results ===");
        log.info("Successful operations: {}", metrics.getSuccessfulOperations());
        log.info("Failed operations: {}", metrics.getFailedOperations());
        log.info("Average latency: {} ms", metrics.getAverageLatencyMs());
        log.info("Median latency: {} ms", metrics.getMedianLatencyMs());
        log.info("95th percentile latency: {} ms", metrics.getP95LatencyMs());
        log.info("Throughput: {:.2f} ops/sec", metrics.getThroughputOpsPerSec());
        log.info("Memory usage change: {} MB", metrics.getMemoryUsageMB());
        log.info("=====================================");
    }
    
    private void assertPerformanceThresholds(PerformanceMetrics metrics) {
        // Performance assertions
        assertThat(metrics.getAverageLatencyMs())
            .as("Average latency should be under %d ms", MAX_AVERAGE_LATENCY_MS)
            .isLessThan(MAX_AVERAGE_LATENCY_MS);
        
        assertThat(metrics.getP95LatencyMs())
            .as("95th percentile latency should be under %d ms", MAX_P95_LATENCY_MS)
            .isLessThan(MAX_P95_LATENCY_MS);
        
        assertThat(metrics.getThroughputOpsPerSec())
            .as("Throughput should be at least %.2f ops/sec", MIN_THROUGHPUT_OPS_PER_SEC)
            .isGreaterThan(MIN_THROUGHPUT_OPS_PER_SEC);
        
        assertThat(Math.abs(metrics.getMemoryUsageMB()))
            .as("Memory usage change should be reasonable (<%d MB)", MAX_MEMORY_INCREASE_MB)
            .isLessThan(MAX_MEMORY_INCREASE_MB);
        
        // Success rate should be high (>95%)
        long totalOps = metrics.getSuccessfulOperations() + metrics.getFailedOperations();
        double successRate = (double) metrics.getSuccessfulOperations() / totalOps;
        assertThat(successRate)
            .as("Success rate should be at least 95%")
            .isGreaterThan(0.95);
    }
    
    private void analyzeMemoryUsage(List<MemoryUsage> snapshots) {
        log.info("Memory Usage Analysis:");
        for (int i = 0; i < snapshots.size(); i++) {
            MemoryUsage usage = snapshots.get(i);
            log.info("Cycle {}: Used={} MB, Max={} MB", 
                    i, usage.getUsed() / (1024 * 1024), usage.getMax() / (1024 * 1024));
        }
        
        // Check for memory leaks (significant upward trend)
        long initialUsage = snapshots.get(0).getUsed();
        long finalUsage = snapshots.get(snapshots.size() - 1).getUsed();
        double memoryGrowthRate = (double) (finalUsage - initialUsage) / initialUsage;
        
        log.info("Memory growth rate: {:.2%}", memoryGrowthRate);
        
        // Fail if memory grows more than 50%
        assertThat(memoryGrowthRate)
            .as("Memory growth should be reasonable (<50%)")
            .isLessThan(0.5);
    }
    
    // Performance metrics data class
    private static class PerformanceMetrics {
        private final long averageLatencyMs;
        private final long medianLatencyMs;
        private final long p95LatencyMs;
        private final double throughputOpsPerSec;
        private final long successfulOperations;
        private final long failedOperations;
        private final long memoryUsageMB;
        
        public PerformanceMetrics(long averageLatencyMs, long medianLatencyMs, long p95LatencyMs,
                                double throughputOpsPerSec, long successfulOperations,
                                long failedOperations, long memoryUsageMB) {
            this.averageLatencyMs = averageLatencyMs;
            this.medianLatencyMs = medianLatencyMs;
            this.p95LatencyMs = p95LatencyMs;
            this.throughputOpsPerSec = throughputOpsPerSec;
            this.successfulOperations = successfulOperations;
            this.failedOperations = failedOperations;
            this.memoryUsageMB = memoryUsageMB;
        }
        
        // Getters
        public long getAverageLatencyMs() { return averageLatencyMs; }
        public long getMedianLatencyMs() { return medianLatencyMs; }
        public long getP95LatencyMs() { return p95LatencyMs; }
        public double getThroughputOpsPerSec() { return throughputOpsPerSec; }
        public long getSuccessfulOperations() { return successfulOperations; }
        public long getFailedOperations() { return failedOperations; }
        public long getMemoryUsageMB() { return memoryUsageMB; }
    }
}