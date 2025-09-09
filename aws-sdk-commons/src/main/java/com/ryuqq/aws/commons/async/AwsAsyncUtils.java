package com.ryuqq.aws.commons.async;

import com.ryuqq.aws.commons.exception.AwsServiceException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * AWS SDK 비동기 처리 유틸리티
 */
@Slf4j
@UtilityClass
public class AwsAsyncUtils {

    private static final ScheduledExecutorService scheduler = 
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("aws-async-utils-scheduler");
                return t;
            });

    /**
     * 타임아웃이 있는 비동기 작업 실행
     */
    public static <T> CompletableFuture<T> withTimeout(
            CompletableFuture<T> future,
            Duration timeout,
            String operationName) {
        
        CompletableFuture<T> timeoutFuture = new CompletableFuture<>();
        
        future.whenComplete((result, error) -> {
            if (error != null) {
                timeoutFuture.completeExceptionally(error);
            } else {
                timeoutFuture.complete(result);
            }
        });

        scheduler.schedule(() -> {
            if (!timeoutFuture.isDone()) {
                String message = String.format("Operation '%s' timed out after %s", 
                        operationName, timeout);
                timeoutFuture.completeExceptionally(
                        new TimeoutException(message));
            }
        }, timeout.toMillis(), TimeUnit.MILLISECONDS);

        return timeoutFuture;
    }

    /**
     * 재시도 로직이 있는 비동기 작업 실행
     */
    public static <T> CompletableFuture<T> withRetry(
            Supplier<CompletableFuture<T>> operation,
            int maxRetries,
            Duration retryDelay,
            String operationName) {
        
        return withRetry(operation, maxRetries, retryDelay, operationName, 0);
    }

    private static <T> CompletableFuture<T> withRetry(
            Supplier<CompletableFuture<T>> operation,
            int maxRetries,
            Duration retryDelay,
            String operationName,
            int attemptNumber) {
        
        return operation.get()
                .thenApply(CompletableFuture::completedFuture)
                .exceptionally(throwable -> {
                    if (attemptNumber >= maxRetries) {
                        log.error("Operation '{}' failed after {} retries", 
                                operationName, maxRetries);
                        throw new AwsServiceException("retry", 
                                "Max retries exceeded for " + operationName, throwable);
                    }

                    log.warn("Operation '{}' failed on attempt {}, retrying after {}",
                            operationName, attemptNumber + 1, retryDelay);

                    CompletableFuture<T> retryFuture = new CompletableFuture<>();
                    
                    scheduler.schedule(() -> {
                        withRetry(operation, maxRetries, retryDelay, 
                                operationName, attemptNumber + 1)
                                .whenComplete((result, error) -> {
                                    if (error != null) {
                                        retryFuture.completeExceptionally(error);
                                    } else {
                                        retryFuture.complete(result);
                                    }
                                });
                    }, retryDelay.toMillis(), TimeUnit.MILLISECONDS);

                    return retryFuture;
                })
                .thenCompose(Function.identity());
    }

    /**
     * 배치 작업을 병렬로 실행
     */
    public static <T, R> CompletableFuture<List<R>> executeInParallel(
            List<T> items,
            Function<T, CompletableFuture<R>> operation,
            int maxConcurrency) {
        
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        Semaphore semaphore = new Semaphore(maxConcurrency);
        
        List<CompletableFuture<R>> futures = items.stream()
                .map(item -> {
                    try {
                        semaphore.acquire();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while acquiring semaphore", e);
                    }
                    
                    return operation.apply(item)
                            .whenComplete((result, error) -> semaphore.release());
                })
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()));
    }

    /**
     * Circuit Breaker 패턴 구현
     */
    public static class CircuitBreaker<T> {
        private final int failureThreshold;
        private final Duration resetTimeout;
        private final String name;
        
        private volatile State state = State.CLOSED;
        private volatile int failureCount = 0;
        private volatile long lastFailureTime = 0;

        public CircuitBreaker(String name, int failureThreshold, Duration resetTimeout) {
            this.name = name;
            this.failureThreshold = failureThreshold;
            this.resetTimeout = resetTimeout;
        }

        public CompletableFuture<T> execute(Supplier<CompletableFuture<T>> operation) {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - lastFailureTime > resetTimeout.toMillis()) {
                    state = State.HALF_OPEN;
                    log.info("Circuit breaker '{}' transitioning to HALF_OPEN", name);
                } else {
                    return CompletableFuture.failedFuture(
                            new AwsServiceException("circuit-breaker",
                                    "Circuit breaker '" + name + "' is OPEN"));
                }
            }

            return operation.get()
                    .thenApply(result -> {
                        onSuccess();
                        return result;
                    })
                    .exceptionally(throwable -> {
                        onFailure();
                        throw new AwsServiceException("circuit-breaker",
                                "Operation failed in circuit breaker '" + name + "'", throwable);
                    });
        }

        private synchronized void onSuccess() {
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
                failureCount = 0;
                log.info("Circuit breaker '{}' is now CLOSED", name);
            }
        }

        private synchronized void onFailure() {
            failureCount++;
            lastFailureTime = System.currentTimeMillis();
            
            if (failureCount >= failureThreshold) {
                state = State.OPEN;
                log.warn("Circuit breaker '{}' is now OPEN after {} failures", 
                        name, failureCount);
            }
        }

        private enum State {
            CLOSED, OPEN, HALF_OPEN
        }
    }

    /**
     * 비동기 작업 체인 빌더
     */
    public static class AsyncChainBuilder<T> {
        private CompletableFuture<T> future;
        private String operationName;

        public AsyncChainBuilder(CompletableFuture<T> future, String operationName) {
            this.future = future;
            this.operationName = operationName;
        }

        public <R> AsyncChainBuilder<R> thenCompose(
                Function<T, CompletableFuture<R>> fn,
                String stepName) {
            
            CompletableFuture<R> nextFuture = future.thenCompose(result -> {
                log.debug("Executing step '{}' in operation '{}'", stepName, operationName);
                return fn.apply(result);
            });
            
            return new AsyncChainBuilder<>(nextFuture, operationName);
        }

        public AsyncChainBuilder<T> withTimeout(Duration timeout) {
            this.future = AwsAsyncUtils.withTimeout(future, timeout, operationName);
            return this;
        }

        public AsyncChainBuilder<T> withRetry(int maxRetries, Duration retryDelay) {
            Supplier<CompletableFuture<T>> supplier = () -> this.future;
            this.future = AwsAsyncUtils.withRetry(supplier, maxRetries, retryDelay, operationName);
            return this;
        }

        public CompletableFuture<T> build() {
            return future;
        }
    }

    /**
     * 비동기 체인 빌더 생성
     */
    public static <T> AsyncChainBuilder<T> chain(
            CompletableFuture<T> future,
            String operationName) {
        return new AsyncChainBuilder<>(future, operationName);
    }
}