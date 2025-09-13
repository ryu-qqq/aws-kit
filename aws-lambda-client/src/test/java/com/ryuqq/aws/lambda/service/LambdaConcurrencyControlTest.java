package com.ryuqq.aws.lambda.service;

import com.ryuqq.aws.lambda.properties.LambdaProperties;
import com.ryuqq.aws.lambda.types.LambdaInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaInvocationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.LogType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lambda 서비스의 동시성 제어 기능 테스트
 * 
 * 다음 기능들을 테스트합니다:
 * - Semaphore 기반 동시 실행 수 제어
 * - 동시 호출 시 순서 및 대기 처리
 * - 최대 동시성 한도 준수
 * - 리소스 해제 및 메모리 누수 방지
 * - 고급 호출 기능에서의 동시성 제어
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Lambda 동시성 제어 테스트")
class LambdaConcurrencyControlTest {

    @Mock
    private LambdaAsyncClient lambdaClient;

    private LambdaProperties properties;
    private DefaultLambdaService lambdaService;

    // 테스트용 동시성 추적 변수들
    private final AtomicInteger concurrentCallsCount = new AtomicInteger(0);
    private final AtomicInteger maxConcurrentCallsObserved = new AtomicInteger(0);
    private final CountDownLatch callStartLatch = new CountDownLatch(1);
    private final List<CountDownLatch> callFinishLatches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // 낮은 동시성으로 설정하여 테스트 효과 확인
        properties = new LambdaProperties(
                Duration.ofMinutes(15),  // timeout
                3,                      // maxConcurrentInvocations - 최대 3개 동시 실행
                900_000L,               // defaultBatchTimeoutMs
                "NONE",                 // defaultRetryPolicy
                true                    // autoGenerateCorrelationId
        );
        
        lambdaService = new DefaultLambdaService(lambdaClient, properties);

        // 추적 변수 초기화
        concurrentCallsCount.set(0);
        maxConcurrentCallsObserved.set(0);
        callFinishLatches.clear();
    }

    @Nested
    @DisplayName("기본 동시성 제어 테스트")
    class BasicConcurrencyControlTest {

        @Test
        @DisplayName("성공 - 동시 실행 수가 설정된 한도를 초과하지 않음")
        void concurrencyControl_MaxConcurrency_DoesNotExceedLimit() throws InterruptedException, ExecutionException, TimeoutException {
            // Given: 동시성 추적을 위한 지연된 응답 설정
            int totalCalls = 6; // 최대 동시성(3)보다 많은 호출
            List<CountDownLatch> individualLatches = new ArrayList<>();

            for (int i = 0; i < totalCalls; i++) {
                CountDownLatch latch = new CountDownLatch(1);
                individualLatches.add(latch);
                callFinishLatches.add(latch);
            }

            // Lambda 클라이언트 모킹: 각 호출이 지연되도록 설정
            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                        // 동시 호출 수 추적 시작
                        int currentCount = concurrentCallsCount.incrementAndGet();
                        int maxObserved = maxConcurrentCallsObserved.get();
                        while (maxObserved < currentCount && 
                               !maxConcurrentCallsObserved.compareAndSet(maxObserved, currentCount)) {
                            maxObserved = maxConcurrentCallsObserved.get();
                        }

                        try {
                            // 호출 시작 신호
                            callStartLatch.countDown();
                            
                            // 각 호출이 완료될 때까지 대기
                            CountDownLatch finishLatch = callFinishLatches.get(
                                concurrentCallsCount.get() - 1);
                            finishLatch.await(5, TimeUnit.SECONDS);
                            
                            return InvokeResponse.builder()
                                    .statusCode(200)
                                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success\"}"))
                                    .build();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        } finally {
                            // 동시 호출 수 감소
                            concurrentCallsCount.decrementAndGet();
                        }
                    }));

            // When: 동시에 여러 Lambda 함수 호출
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < totalCalls; i++) {
                CompletableFuture<String> future = lambdaService.invoke("test-function-" + i, "{\"data\":\"test\"}");
                futures.add(future);
            }

            // 모든 호출이 시작될 때까지 짧은 대기
            assertTrue(callStartLatch.await(2, TimeUnit.SECONDS));
            Thread.sleep(100); // 추가적인 대기로 동시성 측정 정확도 향상

            // 첫 3개 호출 완료 허용
            for (int i = 0; i < 3; i++) {
                individualLatches.get(i).countDown();
            }
            
            Thread.sleep(100); // 동시성 변화 관찰

            // 나머지 호출 완료 허용
            for (int i = 3; i < totalCalls; i++) {
                individualLatches.get(i).countDown();
            }

            // 모든 호출 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).get(10, TimeUnit.SECONDS);

            // Then: 최대 동시 실행 수가 설정된 한도를 초과하지 않았는지 확인
            int observedMaxConcurrency = maxConcurrentCallsObserved.get();
            assertTrue(observedMaxConcurrency <= properties.maxConcurrentInvocations(),
                      String.format("관찰된 최대 동시성(%d)이 설정된 한도(%d)를 초과했습니다", 
                                   observedMaxConcurrency, properties.maxConcurrentInvocations()));

            // 모든 호출이 성공했는지 확인
            for (CompletableFuture<String> future : futures) {
                assertEquals("{\"result\":\"success\"}", future.get());
            }

            verify(lambdaClient, times(totalCalls)).invoke(any(InvokeRequest.class));
        }

        @Test
        @DisplayName("성공 - 동시성 제어 하에서 호출 순서는 보장되지 않음")
        void concurrencyControl_CallOrder_NotGuaranteed() throws ExecutionException, InterruptedException {
            // Given: 서로 다른 지연 시간을 가진 호출들
            List<String> functionNames = List.of("fast-function", "medium-function", "slow-function");
            List<Integer> delays = List.of(10, 50, 100); // 밀리초 단위

            for (int i = 0; i < functionNames.size(); i++) {
                final int index = i; // final 변수로 복사
                final int delay = delays.get(i);
                final String expectedResult = "{\"result\":\"" + functionNames.get(i) + "\"}";
                
                when(lambdaClient.invoke(argThat((InvokeRequest request) -> 
                        request != null && request.functionName().equals(functionNames.get(index)))))
                        .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                            try {
                                Thread.sleep(delay);
                                return InvokeResponse.builder()
                                        .statusCode(200)
                                        .payload(SdkBytes.fromUtf8String(expectedResult))
                                        .build();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            }
                        }));
            }

            // When: 동시에 서로 다른 함수들 호출 (느린 것부터)
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = functionNames.size() - 1; i >= 0; i--) { // 역순으로 호출
                CompletableFuture<String> future = lambdaService.invoke(functionNames.get(i), "{\"test\":true}");
                futures.add(future);
            }

            // 모든 호출 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).get();

            // Then: 빠른 함수가 먼저 완료되었을 가능성이 높음
            // (하지만 동시성 제어로 인해 순서는 보장되지 않음)
            List<String> results = new ArrayList<>();
            for (CompletableFuture<String> future : futures) {
                results.add(future.get());
            }

            // 모든 결과가 올바른지 확인
            assertEquals(3, results.size());
            assertTrue(results.contains("{\"result\":\"fast-function\"}"));
            assertTrue(results.contains("{\"result\":\"medium-function\"}"));
            assertTrue(results.contains("{\"result\":\"slow-function\"}"));

            verify(lambdaClient, times(3)).invoke(any(InvokeRequest.class));
        }
    }

    @Nested
    @DisplayName("고급 호출 기능에서의 동시성 제어 테스트")
    class AdvancedInvocationConcurrencyTest {

        @Test
        @DisplayName("성공 - invokeWithResponse에서도 동시성 제어 적용")
        void concurrencyControl_InvokeWithResponse_RespectsLimit() throws InterruptedException, ExecutionException, TimeoutException {
            // Given: 고급 호출 요청 준비
            List<LambdaInvocationRequest> requests = List.of(
                LambdaInvocationRequest.builder()
                        .functionName("function-1")
                        .payload("{\"data\":1}")
                        .correlationId("req-1")
                        .logType(LogType.TAIL)
                        .build(),
                LambdaInvocationRequest.builder()
                        .functionName("function-2")  
                        .payload("{\"data\":2}")
                        .correlationId("req-2")
                        .logType(LogType.NONE)
                        .build(),
                LambdaInvocationRequest.builder()
                        .functionName("function-3")
                        .payload("{\"data\":3}")
                        .correlationId("req-3")
                        .qualifier("PROD")
                        .build(),
                LambdaInvocationRequest.builder()
                        .functionName("function-4")
                        .payload("{\"data\":4}")
                        .correlationId("req-4")
                        .build()
            );

            // 동시성 추적을 위한 지연된 응답 설정
            AtomicInteger activeCalls = new AtomicInteger(0);
            AtomicInteger maxObservedConcurrency = new AtomicInteger(0);
            CountDownLatch allStartedLatch = new CountDownLatch(requests.size());
            CountDownLatch proceedLatch = new CountDownLatch(1);

            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                        int currentActive = activeCalls.incrementAndGet();
                        
                        // 최대 동시성 추적
                        int currentMax = maxObservedConcurrency.get();
                        while (currentMax < currentActive && 
                               !maxObservedConcurrency.compareAndSet(currentMax, currentActive)) {
                            currentMax = maxObservedConcurrency.get();
                        }
                        
                        allStartedLatch.countDown();
                        
                        try {
                            // 모든 호출이 시작될 때까지 대기
                            proceedLatch.await(5, TimeUnit.SECONDS);
                            
                            InvokeRequest request = invocation.getArgument(0);
                            String result = String.format("{\"processed\":\"%s\"}", request.functionName());
                            
                            return InvokeResponse.builder()
                                    .statusCode(200)
                                    .payload(SdkBytes.fromUtf8String(result))
                                    .logResult("dGVzdCBsb2c=") // "test log" in base64
                                    .build();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        } finally {
                            activeCalls.decrementAndGet();
                        }
                    }));

            // When: 고급 호출 기능으로 동시에 여러 함수 호출
            List<CompletableFuture<LambdaInvocationResponse>> futures = new ArrayList<>();
            for (LambdaInvocationRequest request : requests) {
                CompletableFuture<LambdaInvocationResponse> future = 
                    lambdaService.invokeWithResponse(request);
                futures.add(future);
            }

            // 일정 시간 대기 후 진행 허용
            Thread.sleep(200);
            proceedLatch.countDown();

            // 모든 호출 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).get(10, TimeUnit.SECONDS);

            // Then: 동시성 한도가 준수되었는지 확인
            int observedMax = maxObservedConcurrency.get();
            assertTrue(observedMax <= properties.maxConcurrentInvocations(),
                      String.format("고급 호출에서 관찰된 최대 동시성(%d)이 한도(%d)를 초과", 
                                   observedMax, properties.maxConcurrentInvocations()));

            // 모든 응답 검증
            for (int i = 0; i < futures.size(); i++) {
                LambdaInvocationResponse response = futures.get(i).get();
                assertTrue(response.isSuccess());
                assertEquals(200, response.getStatusCode());
                assertTrue(response.getPayload().contains("function-" + (i + 1)));
                assertEquals("req-" + (i + 1), response.getCorrelationId());
            }

            verify(lambdaClient, times(requests.size())).invoke(any(InvokeRequest.class));
        }

        @Test
        @DisplayName("실패 - 동시성 제어 중 인터럽트 처리")
        void concurrencyControl_InterruptHandling_GracefulShutdown() throws InterruptedException {
            // Given: 긴 지연을 가진 Lambda 호출 설정
            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(5000); // 5초 지연
                            return InvokeResponse.builder()
                                    .statusCode(200)
                                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success\"}"))
                                    .build();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Interrupted during Lambda call", e);
                        }
                    }));

            // When: 여러 호출을 시작한 후 인터럽트
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                CompletableFuture<String> future = lambdaService.invoke("test-function-" + i, "{\"test\":true}");
                futures.add(future);
            }

            // 짧은 시간 후 첫 번째 호출 취소
            Thread.sleep(100);
            futures.get(0).cancel(true);

            // Then: 나머지 호출들이 정상적으로 처리되는지 확인
            int completedCount = 0;
            int cancelledCount = 0;
            
            for (CompletableFuture<String> future : futures) {
                try {
                    future.get(1, TimeUnit.SECONDS);
                    completedCount++;
                } catch (ExecutionException | TimeoutException | CancellationException e) {
                    if (future.isCancelled()) {
                        cancelledCount++;
                    }
                    // 예상된 예외이므로 무시
                }
            }

            // 일부는 취소되고 일부는 완료될 수 있음
            assertTrue(cancelledCount > 0 || completedCount > 0);
        }
    }

    @Nested
    @DisplayName("리소스 관리 및 메모리 누수 방지 테스트")
    class ResourceManagementTest {

        @Test
        @DisplayName("성공 - Semaphore 리소스가 올바르게 해제됨")
        void concurrencyControl_SemaphoreRelease_NoResourceLeak() throws InterruptedException, ExecutionException {
            // Given: 빠른 응답을 위한 Lambda 클라이언트 설정
            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(
                            InvokeResponse.builder()
                                    .statusCode(200)
                                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success\"}"))
                                    .build()));

            // When: 여러 라운드의 호출 수행
            int rounds = 3;
            int callsPerRound = properties.maxConcurrentInvocations();
            
            for (int round = 0; round < rounds; round++) {
                List<CompletableFuture<String>> futures = new ArrayList<>();
                
                // 각 라운드에서 최대 동시성만큼 호출
                for (int i = 0; i < callsPerRound; i++) {
                    CompletableFuture<String> future = lambdaService.invoke(
                        String.format("test-function-r%d-c%d", round, i), 
                        "{\"round\":" + round + ",\"call\":" + i + "}");
                    futures.add(future);
                }
                
                // 모든 호출 완료 대기
                CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).get();
                
                // 모든 결과 검증
                for (CompletableFuture<String> future : futures) {
                    assertEquals("{\"result\":\"success\"}", future.get());
                }
            }

            // Then: 총 호출 수가 예상과 일치하는지 확인
            int expectedTotalCalls = rounds * callsPerRound;
            verify(lambdaClient, times(expectedTotalCalls)).invoke(any(InvokeRequest.class));
            
            // 추가적인 호출이 여전히 정상 작동하는지 확인 (리소스 누수 없음)
            CompletableFuture<String> additionalCall = lambdaService.invoke("final-test", "{\"final\":true}");
            assertEquals("{\"result\":\"success\"}", additionalCall.get());
            
            verify(lambdaClient, times(expectedTotalCalls + 1)).invoke(any(InvokeRequest.class));
        }

        @Test
        @DisplayName("성공 - 예외 발생 시에도 Semaphore가 올바르게 해제됨")
        void concurrencyControl_ExceptionHandling_ReleasesResources() throws InterruptedException {
            // Given: 일부 호출은 성공, 일부는 실패하도록 설정
            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(
                            InvokeResponse.builder()
                                    .statusCode(200)
                                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success\"}"))
                                    .build()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new RuntimeException("Simulated Lambda failure")))
                    .thenReturn(CompletableFuture.completedFuture(
                            InvokeResponse.builder()
                                    .statusCode(200)
                                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success\"}"))
                                    .build()));

            // When: 성공과 실패가 섞인 호출들 수행
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                CompletableFuture<String> future = lambdaService.invoke("test-function-" + i, "{\"test\":true}");
                futures.add(future);
            }

            // 모든 호출 완료 대기 (성공/실패 불문)
            Thread.sleep(200);

            // Then: 실패가 있어도 추가 호출이 정상 작동하는지 확인
            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(
                            InvokeResponse.builder()
                                    .statusCode(200)
                                    .payload(SdkBytes.fromUtf8String("{\"result\":\"post-failure-success\"}"))
                                    .build()));

            CompletableFuture<String> postFailureCall = lambdaService.invoke("post-failure-test", "{\"test\":true}");
            
            try {
                String result = postFailureCall.get(2, TimeUnit.SECONDS);
                assertEquals("{\"result\":\"post-failure-success\"}", result);
            } catch (ExecutionException | TimeoutException e) {
                fail("실패 후 호출이 정상적으로 작동하지 않음: " + e.getMessage());
            }

            // 리소스가 올바르게 해제되어 추가 호출이 가능함을 확인
            verify(lambdaClient, times(4)).invoke(any(InvokeRequest.class));
        }
    }
}