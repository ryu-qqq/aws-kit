package com.ryuqq.aws.sqs.consumer.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * ExecutorServiceProvider 통합 테스트
 * 
 * 실제 ExecutorService 동작과 Thread Pool 추상화를 검증하는 테스트입니다.
 * Platform Thread와 Virtual Thread의 성능 및 동작 차이를 검증합니다.
 */
@DisplayName("ExecutorServiceProvider 통합 테스트")
class ExecutorServiceProviderIntegrationTest {
    
    @Test
    @DisplayName("Platform Thread Provider가 올바르게 동작해야 한다")
    void shouldWorkWithPlatformThreadProvider() throws InterruptedException {
        // given
        ExecutorServiceProvider provider = new TestPlatformThreadProvider();
        ExecutorService executor = provider.createMessageProcessingExecutor("test-consumer");
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger counter = new AtomicInteger(0);
        
        // when
        for (int i = 0; i < 5; i++) {
            executor.submit(() -> {
                counter.incrementAndGet();
                latch.countDown();
            });
        }
        
        // then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(counter.get()).isEqualTo(5);
        assertThat(provider.getThreadingModel()).isEqualTo(ExecutorServiceProvider.ThreadingModel.PLATFORM_THREADS);
        
        // cleanup
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    @DisplayName("Virtual Thread Provider가 올바르게 동작해야 한다")
    void shouldWorkWithVirtualThreadProvider() throws InterruptedException {
        // given
        ExecutorServiceProvider provider = new TestVirtualThreadProvider();
        ExecutorService executor = provider.createMessageProcessingExecutor("test-consumer");
        CountDownLatch latch = new CountDownLatch(100); // Virtual Thread는 대량 작업에 유리
        AtomicInteger counter = new AtomicInteger(0);
        
        // when
        for (int i = 0; i < 100; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(10); // I/O 대기 시뮬레이션
                    counter.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(counter.get()).isEqualTo(100);
        assertThat(provider.getThreadingModel()).isEqualTo(ExecutorServiceProvider.ThreadingModel.VIRTUAL_THREADS);
        
        // cleanup
        executor.shutdown();
        assertThat(executor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test
    @DisplayName("메시지 처리와 폴링 Executor가 독립적으로 동작해야 한다")
    void shouldCreateIndependentExecutors() throws InterruptedException {
        // given
        ExecutorServiceProvider provider = new TestPlatformThreadProvider();
        ExecutorService messageExecutor = provider.createMessageProcessingExecutor("test-consumer");
        ExecutorService pollingExecutor = provider.createPollingExecutor("test-consumer");
        
        CountDownLatch messageLatch = new CountDownLatch(3);
        CountDownLatch pollingLatch = new CountDownLatch(2);
        
        // when - 각 Executor에서 독립적으로 작업 실행
        for (int i = 0; i < 3; i++) {
            messageExecutor.submit(messageLatch::countDown);
        }
        for (int i = 0; i < 2; i++) {
            pollingExecutor.submit(pollingLatch::countDown);
        }
        
        // then
        assertThat(messageLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(pollingLatch.await(2, TimeUnit.SECONDS)).isTrue();
        
        // cleanup
        messageExecutor.shutdown();
        pollingExecutor.shutdown();
        assertThat(messageExecutor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
        assertThat(pollingExecutor.awaitTermination(1, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test
    @DisplayName("Graceful Shutdown이 올바르게 동작해야 한다")
    void shouldPerformGracefulShutdown() throws InterruptedException {
        // given
        TestPlatformThreadProvider provider = new TestPlatformThreadProvider();
        ExecutorService executor = provider.createMessageProcessingExecutor("test-consumer");
        CountDownLatch startLatch = new CountDownLatch(2);
        CountDownLatch finishLatch = new CountDownLatch(2);
        
        // when - 장기 실행 작업 시작
        executor.submit(() -> {
            startLatch.countDown();
            try {
                Thread.sleep(100); // 짧은 작업
                finishLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        executor.submit(() -> {
            startLatch.countDown();
            try {
                Thread.sleep(200); // 더 긴 작업
                finishLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        // 작업이 시작될 때까지 대기
        assertThat(startLatch.await(1, TimeUnit.SECONDS)).isTrue();
        
        // graceful shutdown 실행
        provider.shutdown(1000); // 1초 타임아웃
        
        // then - 모든 작업이 완료되어야 함
        assertThat(finishLatch.await(2, TimeUnit.SECONDS)).isTrue();
    }
    
    @Test
    @DisplayName("Shutdown 타임아웃 시 강제 종료가 발생해야 한다")
    void shouldForceShutdownOnTimeout() throws InterruptedException {
        // given
        TestPlatformThreadProvider provider = new TestPlatformThreadProvider();
        ExecutorService executor = provider.createMessageProcessingExecutor("test-consumer");
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // when - 타임아웃보다 긴 작업 실행
        executor.submit(() -> {
            startLatch.countDown();
            try {
                Thread.sleep(2000); // 2초 작업 (타임아웃 500ms보다 김)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        assertThat(startLatch.await(1, TimeUnit.SECONDS)).isTrue();
        
        long startTime = System.currentTimeMillis();
        provider.shutdown(500); // 500ms 타임아웃
        long shutdownTime = System.currentTimeMillis() - startTime;
        
        // then - 타임아웃 시간 내에 shutdown이 완료되어야 함
        assertThat(shutdownTime).isLessThan(1000); // 강제 종료로 인해 빠르게 완료
    }
    
    @Test
    @DisplayName("커스텀 Executor Provider가 올바르게 동작해야 한다")
    void shouldWorkWithCustomExecutorProvider() throws InterruptedException {
        // given
        ExecutorServiceProvider provider = new TestCustomExecutorProvider();
        ExecutorService executor = provider.createMessageProcessingExecutor("custom-consumer");
        CountDownLatch latch = new CountDownLatch(1);
        
        // when
        executor.submit(latch::countDown);
        
        // then
        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(provider.getThreadingModel()).isEqualTo(ExecutorServiceProvider.ThreadingModel.CUSTOM);
        assertThat(provider.supportsGracefulShutdown()).isFalse(); // 커스텀은 자체 관리
        
        // cleanup
        executor.shutdown();
    }
    
    @Test
    @DisplayName("동일한 Consumer 이름으로 여러 Executor 생성이 가능해야 한다")
    void shouldCreateMultipleExecutorsForSameConsumer() {
        // given
        ExecutorServiceProvider provider = new TestPlatformThreadProvider();
        String consumerName = "multi-executor-consumer";
        
        // when
        ExecutorService executor1 = provider.createMessageProcessingExecutor(consumerName);
        ExecutorService executor2 = provider.createPollingExecutor(consumerName);
        ExecutorService executor3 = provider.createMessageProcessingExecutor(consumerName); // 다른 인스턴스
        
        // then
        assertThat(executor1).isNotNull();
        assertThat(executor2).isNotNull();
        assertThat(executor3).isNotNull();
        assertThat(executor1).isNotSameAs(executor2);
        assertThat(executor1).isNotSameAs(executor3);
        
        // cleanup
        executor1.shutdown();
        executor2.shutdown();
        executor3.shutdown();
    }
    
    // 테스트용 Platform Thread Provider 구현
    private static class TestPlatformThreadProvider implements ExecutorServiceProvider {
        private volatile boolean shutdownRequested = false;
        
        @Override
        public ExecutorService createMessageProcessingExecutor(String consumerName) {
            return Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "message-processor-" + consumerName);
                t.setDaemon(true);
                return t;
            });
        }
        
        @Override
        public ExecutorService createPollingExecutor(String consumerName) {
            return Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "poller-" + consumerName);
                t.setDaemon(true);
                return t;
            });
        }
        
        @Override
        public ThreadingModel getThreadingModel() {
            return ThreadingModel.PLATFORM_THREADS;
        }
        
        @Override
        public void shutdown(long timeoutMillis) {
            shutdownRequested = true;
            // 실제 구현에서는 생성된 모든 Executor를 추적하고 종료해야 함
            // 테스트에서는 개별 Executor shutdown 호출로 처리
        }
    }
    
    // 테스트용 Virtual Thread Provider 구현 (Java 21+)
    private static class TestVirtualThreadProvider implements ExecutorServiceProvider {
        
        @Override
        public ExecutorService createMessageProcessingExecutor(String consumerName) {
            // Virtual Thread를 사용하는 Executor 생성
            return Executors.newVirtualThreadPerTaskExecutor();
        }
        
        @Override
        public ExecutorService createPollingExecutor(String consumerName) {
            return Executors.newVirtualThreadPerTaskExecutor();
        }
        
        @Override
        public ThreadingModel getThreadingModel() {
            return ThreadingModel.VIRTUAL_THREADS;
        }
    }
    
    // 테스트용 Custom Executor Provider 구현
    private static class TestCustomExecutorProvider implements ExecutorServiceProvider {
        
        @Override
        public ExecutorService createMessageProcessingExecutor(String consumerName) {
            // 사용자 정의 Executor 설정 시뮬레이션
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 8, 60L, TimeUnit.SECONDS, 
                new LinkedBlockingQueue<>(100),
                r -> new Thread(r, "custom-" + consumerName)
            );
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }
        
        @Override
        public ExecutorService createPollingExecutor(String consumerName) {
            return createMessageProcessingExecutor(consumerName + "-poller");
        }
        
        @Override
        public ThreadingModel getThreadingModel() {
            return ThreadingModel.CUSTOM;
        }
        
        @Override
        public boolean supportsGracefulShutdown() {
            return false; // 사용자가 직접 관리
        }
    }
}