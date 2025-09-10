package com.ryuqq.aws.sqs.annotation;

import com.ryuqq.aws.sqs.example.OrderDto;
import com.ryuqq.aws.sqs.example.OrderQueueService;
import com.ryuqq.aws.sqs.model.SqsMessage;
import com.ryuqq.aws.sqs.proxy.SqsClientProxyFactory;
import com.ryuqq.aws.sqs.serialization.JacksonMessageSerializer;
import com.ryuqq.aws.sqs.service.SqsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Performance tests for annotation-based SQS client.
 * Measures proxy overhead and concurrent operation performance.
 */
@ExtendWith(MockitoExtension.class)
class SqsAnnotationPerformanceTest {

    @Mock
    private SqsService sqsService;
    
    private SqsClientProxyFactory proxyFactory;
    private OrderQueueService orderService;
    
    @BeforeEach
    void setUp() {
        JacksonMessageSerializer serializer = new JacksonMessageSerializer();
        proxyFactory = new SqsClientProxyFactory(sqsService, serializer);
        orderService = proxyFactory.createProxy(OrderQueueService.class);
        
        // Mock successful responses
        when(sqsService.sendMessage(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("msg-id"));
        when(sqsService.sendMessageBatch(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of("id1", "id2", "id3")));
        when(sqsService.receiveAndProcessMessages(anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Nested
    @DisplayName("Proxy Creation Performance")
    class ProxyCreationPerformance {
        
        @Test
        @DisplayName("Should measure proxy creation time")
        void shouldMeasureProxyCreationTime() {
            // Given
            int iterations = 1000;
            
            // When
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                OrderQueueService proxy = proxyFactory.createProxy(OrderQueueService.class);
                assertThat(proxy).isNotNull();
            }
            
            long endTime = System.nanoTime();
            long totalTimeMs = (endTime - startTime) / 1_000_000;
            double avgTimeMs = (double) totalTimeMs / iterations;
            
            // Then
            System.out.printf("Proxy creation: %d iterations in %d ms (avg: %.2f ms)%n", 
                    iterations, totalTimeMs, avgTimeMs);
            
            // Should create proxies reasonably fast (under 1ms average)
            assertThat(avgTimeMs).isLessThan(1.0);
        }
        
        @Test
        @DisplayName("Should verify proxy caching effectiveness")
        void shouldVerifyProxyCachingEffectiveness() {
            // Given
            int iterations = 10000;
            
            // When - First creation (cache miss)
            long startTime = System.nanoTime();
            OrderQueueService firstProxy = proxyFactory.createProxy(OrderQueueService.class);
            long firstCreationTime = System.nanoTime() - startTime;
            
            // When - Subsequent creations (cache hits)
            startTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                OrderQueueService proxy = proxyFactory.createProxy(OrderQueueService.class);
                assertThat(proxy).isSameAs(firstProxy);
            }
            long cachedCreationTime = System.nanoTime() - startTime;
            
            double avgCachedTimeNs = (double) cachedCreationTime / iterations;
            double firstCreationTimeMs = firstCreationTime / 1_000_000.0;
            double avgCachedTimeNs_display = avgCachedTimeNs;
            
            // Then
            System.out.printf("First creation: %.2f ms, Cached avg: %.0f ns%n", 
                    firstCreationTimeMs, avgCachedTimeNs_display);
            
            // Cached access should be extremely fast (under 1000ns)
            assertThat(avgCachedTimeNs).isLessThan(1000.0);
        }
    }

    @Nested
    @DisplayName("Method Invocation Performance")
    class MethodInvocationPerformance {
        
        @Test
        @DisplayName("Should measure single message send performance")
        void shouldMeasureSingleMessageSendPerformance() throws Exception {
            // Given
            int iterations = 1000;
            OrderDto order = OrderDto.create("ORDER-PERF", "CUSTOMER-PERF", BigDecimal.valueOf(99.99));
            
            // Warmup
            for (int i = 0; i < 100; i++) {
                orderService.sendOrderForProcessing(order).get();
            }
            
            // When
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                CompletableFuture<String> future = orderService.sendOrderForProcessing(order);
                assertThat(future.get()).isNotNull();
            }
            
            long endTime = System.nanoTime();
            long totalTimeMs = (endTime - startTime) / 1_000_000;
            double avgTimeMs = (double) totalTimeMs / iterations;
            
            // Then
            System.out.printf("Single message send: %d iterations in %d ms (avg: %.2f ms)%n", 
                    iterations, totalTimeMs, avgTimeMs);
            
            // Should process messages reasonably fast
            assertThat(avgTimeMs).isLessThan(5.0);
        }
        
        @Test
        @DisplayName("Should measure batch message send performance")
        void shouldMeasureBatchMessageSendPerformance() throws Exception {
            // Given
            int iterations = 500;
            List<OrderDto> orders = List.of(
                    OrderDto.create("ORDER-1", "CUSTOMER-1", BigDecimal.valueOf(50.00)),
                    OrderDto.create("ORDER-2", "CUSTOMER-2", BigDecimal.valueOf(75.00)),
                    OrderDto.create("ORDER-3", "CUSTOMER-3", BigDecimal.valueOf(100.00))
            );
            
            // Warmup
            for (int i = 0; i < 50; i++) {
                orderService.sendOrderBatch(orders).get();
            }
            
            // When
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                CompletableFuture<List<String>> future = orderService.sendOrderBatch(orders);
                assertThat(future.get()).hasSize(3);
            }
            
            long endTime = System.nanoTime();
            long totalTimeMs = (endTime - startTime) / 1_000_000;
            double avgTimeMs = (double) totalTimeMs / iterations;
            
            // Then
            System.out.printf("Batch message send: %d iterations in %d ms (avg: %.2f ms)%n", 
                    iterations, totalTimeMs, avgTimeMs);
            
            // Should process batches reasonably fast
            assertThat(avgTimeMs).isLessThan(10.0);
        }
        
        @Test
        @DisplayName("Should measure message processing performance")
        void shouldMeasureMessageProcessingPerformance() throws Exception {
            // Given
            int iterations = 1000;
            AtomicLong processedCount = new AtomicLong(0);
            Consumer<SqsMessage> fastProcessor = message -> processedCount.incrementAndGet();
            
            // Warmup
            for (int i = 0; i < 100; i++) {
                orderService.processOrders(fastProcessor).get();
            }
            
            // When
            long startTime = System.nanoTime();
            
            for (int i = 0; i < iterations; i++) {
                CompletableFuture<Void> future = orderService.processOrders(fastProcessor);
                future.get();
            }
            
            long endTime = System.nanoTime();
            long totalTimeMs = (endTime - startTime) / 1_000_000;
            double avgTimeMs = (double) totalTimeMs / iterations;
            
            // Then
            System.out.printf("Message processing: %d iterations in %d ms (avg: %.2f ms)%n", 
                    iterations, totalTimeMs, avgTimeMs);
            
            // Should process messages reasonably fast
            assertThat(avgTimeMs).isLessThan(5.0);
        }
    }

    @Nested
    @DisplayName("Concurrent Operations Performance")
    class ConcurrentOperationsPerformance {
        
        @Test
        @DisplayName("Should handle concurrent message sending")
        void shouldHandleConcurrentMessageSending() throws Exception {
            // Given
            int threadCount = 10;
            int messagesPerThread = 100;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<Long> threadTimes = new ArrayList<>();
            
            // When
            long startTime = System.nanoTime();
            
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        long threadStartTime = System.nanoTime();
                        
                        for (int j = 0; j < messagesPerThread; j++) {
                            OrderDto order = OrderDto.create(
                                    "ORDER-" + threadId + "-" + j, 
                                    "CUSTOMER-" + threadId, 
                                    BigDecimal.valueOf(99.99)
                            );
                            orderService.sendOrderForProcessing(order).get();
                        }
                        
                        long threadEndTime = System.nanoTime();
                        synchronized (threadTimes) {
                            threadTimes.add((threadEndTime - threadStartTime) / 1_000_000);
                        }
                        
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            
            long endTime = System.nanoTime();
            long totalTimeMs = (endTime - startTime) / 1_000_000;
            
            // Then
            int totalMessages = threadCount * messagesPerThread;
            double avgThreadTime = threadTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
            double messagesPerSecond = (double) totalMessages / (totalTimeMs / 1000.0);
            
            System.out.printf("Concurrent sending: %d threads, %d messages, %d ms total%n", 
                    threadCount, totalMessages, totalTimeMs);
            System.out.printf("Average thread time: %.2f ms, Messages/sec: %.2f%n", 
                    avgThreadTime, messagesPerSecond);
            
            // Should handle concurrent operations efficiently
            assertThat(messagesPerSecond).isGreaterThan(100.0);
            assertThat(avgThreadTime).isLessThan(10000.0); // 10 seconds max per thread
        }
        
        @Test
        @DisplayName("Should handle concurrent proxy creation")
        void shouldHandleConcurrentProxyCreation() throws Exception {
            // Given
            int threadCount = 20;
            CountDownLatch latch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            List<OrderQueueService> proxies = new ArrayList<>();
            
            // When
            long startTime = System.nanoTime();
            
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        OrderQueueService proxy = proxyFactory.createProxy(OrderQueueService.class);
                        synchronized (proxies) {
                            proxies.add(proxy);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            
            long endTime = System.nanoTime();
            long totalTimeMs = (endTime - startTime) / 1_000_000;
            
            // Then
            System.out.printf("Concurrent proxy creation: %d threads in %d ms%n", 
                    threadCount, totalTimeMs);
            
            // All proxies should be the same instance (cached)
            assertThat(proxies).hasSize(threadCount);
            OrderQueueService firstProxy = proxies.get(0);
            assertThat(proxies).allSatisfy(proxy -> assertThat(proxy).isSameAs(firstProxy));
            
            // Should complete quickly
            assertThat(totalTimeMs).isLessThan(1000); // 1 second max
        }
        
        @Test
        @DisplayName("Should handle mixed concurrent operations")
        void shouldHandleMixedConcurrentOperations() throws Exception {
            // Given
            int operationsPerType = 100;
            CountDownLatch latch = new CountDownLatch(3);
            ExecutorService executor = Executors.newFixedThreadPool(3);
            
            // When
            long startTime = System.nanoTime();
            
            // Thread 1: Send single messages
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerType; i++) {
                        OrderDto order = OrderDto.create("SINGLE-" + i, "CUSTOMER", BigDecimal.valueOf(99.99));
                        orderService.sendOrderForProcessing(order).get();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
            
            // Thread 2: Send batch messages
            executor.submit(() -> {
                try {
                    List<OrderDto> orders = List.of(
                            OrderDto.create("BATCH-1", "CUSTOMER", BigDecimal.valueOf(50.00)),
                            OrderDto.create("BATCH-2", "CUSTOMER", BigDecimal.valueOf(75.00))
                    );
                    for (int i = 0; i < operationsPerType; i++) {
                        orderService.sendOrderBatch(orders).get();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
            
            // Thread 3: Process messages
            executor.submit(() -> {
                try {
                    Consumer<SqsMessage> processor = message -> { /* Fast processing */ };
                    for (int i = 0; i < operationsPerType; i++) {
                        orderService.processOrders(processor).get();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
            
            assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();
            executor.shutdown();
            
            long endTime = System.nanoTime();
            long totalTimeMs = (endTime - startTime) / 1_000_000;
            
            // Then
            System.out.printf("Mixed concurrent operations: %d ops/type in %d ms%n", 
                    operationsPerType, totalTimeMs);
            
            // Should complete all operations efficiently
            assertThat(totalTimeMs).isLessThan(15000); // 15 seconds max
        }
    }

    @Nested
    @DisplayName("Memory Usage Performance")
    class MemoryUsagePerformance {
        
        @Test
        @DisplayName("Should measure proxy memory overhead")
        void shouldMeasureProxyMemoryOverhead() {
            // Given
            Runtime runtime = Runtime.getRuntime();
            runtime.gc(); // Force garbage collection
            long baseMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // When - Create many proxies (should be cached)
            List<OrderQueueService> proxies = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                proxies.add(proxyFactory.createProxy(OrderQueueService.class));
            }
            
            runtime.gc(); // Force garbage collection
            long afterProxyMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // Then
            long memoryOverhead = afterProxyMemory - baseMemory;
            long memoryPerProxy = memoryOverhead / 1000;
            
            System.out.printf("Memory overhead: %d bytes total, %d bytes per proxy reference%n", 
                    memoryOverhead, memoryPerProxy);
            
            // Memory overhead should be minimal due to caching
            assertThat(memoryPerProxy).isLessThan(100); // Less than 100 bytes per reference
        }
        
        @Test
        @DisplayName("Should measure serialization memory usage")
        void shouldMeasureSerializationMemoryUsage() {
            // Given
            Runtime runtime = Runtime.getRuntime();
            runtime.gc();
            long baseMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // When - Serialize many objects
            List<OrderDto> orders = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                OrderDto order = OrderDto.create("ORDER-" + i, "CUSTOMER", BigDecimal.valueOf(99.99));
                orders.add(order);
                
                // Trigger serialization through send operation
                try {
                    orderService.sendOrderForProcessing(order).get();
                } catch (Exception e) {
                    // Ignore for memory test
                }
            }
            
            runtime.gc();
            long afterSerializationMemory = runtime.totalMemory() - runtime.freeMemory();
            
            // Then
            long memoryOverhead = afterSerializationMemory - baseMemory;
            long memoryPerOrder = memoryOverhead / 1000;
            
            System.out.printf("Serialization memory: %d bytes total, %d bytes per order%n", 
                    memoryOverhead, memoryPerOrder);
            
            // Memory usage should be reasonable
            assertThat(memoryPerOrder).isLessThan(1000); // Less than 1KB per order
        }
    }
}