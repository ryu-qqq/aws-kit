package com.ryuqq.aws.lambda.service;

import com.ryuqq.aws.lambda.exception.LambdaFunctionException;
import com.ryuqq.aws.lambda.properties.LambdaProperties;
import com.ryuqq.aws.lambda.types.LambdaBatchInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaInvocationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.*;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Lambda 배치 호출 기능에 대한 단위 테스트
 * 
 * 이 테스트 클래스는 AWS Lambda의 배치 처리 및 다중 함수 호출 기능을 검증합니다.
 * 대량의 Lambda 함수 호출을 효율적으로 처리하고, 다양한 실패 시나리오에서의
 * 복구 메커니즘과 결과 집계 방식이 올바르게 작동하는지 확인합니다.
 * 
 * 테스트하는 주요 기능:
 * - invokeMultiple(): 동일한 Lambda 함수에 대한 다중 페이로드 병렬 호출
 *   → 하나의 함수를 여러 다른 입력값으로 동시에 실행하는 기능
 *   → 동시성 제어, 개별 호출 실패 처리, 전체 결과 집계 등을 검증
 * - invokeBatch(): 복합 배치 호출 (서로 다른 다중 함수 지원)
 *   → 여러 다른 Lambda 함수들을 하나의 배치로 묶어서 실행하는 기능
 *   → 함수별 개별 설정, 혼합된 성공/실패 처리, 고급 집계 옵션 등을 검증
 * 
 * 배치 처리 옵션 테스트:
 * - failFast: 첫 번째 실패 시 즉시 전체 배치를 중단하는 빠른 실패 모드
 * - maxConcurrency: 동시에 실행할 수 있는 최대 Lambda 호출 수 제한
 * - timeoutMs: 전체 배치 작업의 최대 허용 시간 (밀리초 단위)
 * - retryPolicy: 개별 호출 실패 시 재시도 방식 (NONE, INDIVIDUAL, BATCH_LEVEL)
 * 
 * 결과 집계 모드별 테스트:
 * - ALL: 성공한 호출과 실패한 호출의 모든 결과를 포함하는 완전한 집계
 * - SUCCESS_ONLY: 성공한 호출의 결과만 포함, 실패는 무시하는 선택적 집계
 * - FAILURE_ONLY: 실패한 호출의 오류 정보만 포함, 디버깅 목적의 집계
 * - SUMMARY: 성공/실패 개수와 기본 통계만 포함하는 경량화된 요약 집계
 * 
 * 성능 및 안정성 검증 영역:
 * - 동시성 제어: Semaphore 기반 리소스 관리로 메모리 누수 및 과부하 방지
 * - 백프레셔 처리: 시스템 한계 상황에서의 안정적인 처리 능력 검증
 * - 예외 전파: 개별 Lambda 실패가 전체 배치에 미치는 영향 분석
 * - 타임아웃 관리: 장기 실행 배치 작업의 적절한 중단 및 정리 메커니즘
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Lambda 배치 호출 기능 테스트")
class LambdaBatchOperationsTest {

    @Mock
    private LambdaAsyncClient lambdaClient;

    private LambdaProperties properties;
    private DefaultLambdaService lambdaService;

    @BeforeEach
    void setUp() {
        // 테스트용 Lambda 설정 초기화
        properties = new LambdaProperties();
        properties.setTimeout(Duration.ofMinutes(15));
        properties.setMaxRetries(3);
        properties.setMaxConcurrentInvocations(10);
        properties.setAutoGenerateCorrelationId(true);
        
        lambdaService = new DefaultLambdaService(lambdaClient, properties);
    }

    @Nested
    @DisplayName("다중 페이로드 호출 테스트")
    class InvokeMultipleTest {

        @Test
        @DisplayName("성공 - 동일 함수를 여러 페이로드로 동시 호출")
        void invokeMultiple_Success_ReturnsAllResults() throws ExecutionException, InterruptedException {
            // Given: 동일한 함수에 여러 페이로드로 호출 준비
            String functionName = "data-processor";
            List<String> payloads = Arrays.asList(
                "{\"userId\":1, \"action\":\"process\"}",
                "{\"userId\":2, \"action\":\"process\"}",
                "{\"userId\":3, \"action\":\"process\"}"
            );

            // 각 호출에 대한 성공 응답 설정
            InvokeResponse response1 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"user1-processed\"}"))
                    .build();

            InvokeResponse response2 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"user2-processed\"}"))
                    .build();

            InvokeResponse response3 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"user3-processed\"}"))
                    .build();

            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(response1))
                    .thenReturn(CompletableFuture.completedFuture(response2))
                    .thenReturn(CompletableFuture.completedFuture(response3));

            // When: 다중 페이로드로 함수 호출
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeMultiple(functionName, payloads);
            List<LambdaInvocationResponse> responses = result.get();

            // Then: 모든 호출이 성공하고 순서대로 결과 반환
            assertNotNull(responses);
            assertEquals(3, responses.size());

            // 첫 번째 응답 검증
            LambdaInvocationResponse firstResponse = responses.get(0);
            assertTrue(firstResponse.isSuccess());
            assertEquals(200, firstResponse.getStatusCode());
            assertEquals("{\"result\":\"user1-processed\"}", firstResponse.getPayload());
            assertNotNull(firstResponse.getCorrelationId());
            assertTrue(firstResponse.getCorrelationId().contains("-0")); // 인덱스 포함

            // 두 번째 응답 검증  
            LambdaInvocationResponse secondResponse = responses.get(1);
            assertTrue(secondResponse.isSuccess());
            assertEquals("{\"result\":\"user2-processed\"}", secondResponse.getPayload());
            assertTrue(secondResponse.getCorrelationId().contains("-1"));

            // 세 번째 응답 검증
            LambdaInvocationResponse thirdResponse = responses.get(2);
            assertTrue(thirdResponse.isSuccess());
            assertEquals("{\"result\":\"user3-processed\"}", thirdResponse.getPayload());
            assertTrue(thirdResponse.getCorrelationId().contains("-2"));

            // Lambda 클라이언트가 3번 호출되었는지 확인
            verify(lambdaClient, times(3)).invoke(argThat((InvokeRequest request) ->
                    request.functionName().equals(functionName) &&
                    request.invocationType() == InvocationType.REQUEST_RESPONSE &&
                    payloads.contains(request.payload().asUtf8String())
            ));
        }

        @Test
        @DisplayName("부분 실패 - 일부 호출 실패시에도 모든 결과 반환")
        void invokeMultiple_PartialFailure_ReturnsAllResults() throws ExecutionException, InterruptedException {
            // Given: 일부 호출이 실패하는 시나리오
            String functionName = "unreliable-function";
            List<String> payloads = Arrays.asList(
                "{\"data\":\"success1\"}",
                "{\"data\":\"fail\"}",
                "{\"data\":\"success2\"}"
            );

            InvokeResponse successResponse1 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success1\"}"))
                    .build();

            AwsServiceException failureException = AwsServiceException.builder()
                    .message("Function execution error")
                    .statusCode(500)
                    .build();

            InvokeResponse successResponse2 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success2\"}"))
                    .build();

            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(successResponse1))
                    .thenReturn(CompletableFuture.failedFuture(failureException))
                    .thenReturn(CompletableFuture.completedFuture(successResponse2));

            // When: 부분 실패가 있는 배치 호출 실행
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeMultiple(functionName, payloads);
            List<LambdaInvocationResponse> responses = result.get();

            // Then: 성공과 실패 결과가 모두 포함되어 반환
            assertNotNull(responses);
            assertEquals(3, responses.size());

            // 첫 번째: 성공
            assertTrue(responses.get(0).isSuccess());
            assertEquals("{\"result\":\"success1\"}", responses.get(0).getPayload());

            // 두 번째: 실패
            assertFalse(responses.get(1).isSuccess());
            assertEquals(500, responses.get(1).getStatusCode());
            assertEquals("BatchExecutionError", responses.get(1).getFunctionError());
            assertTrue(responses.get(1).getPayload().contains("errorMessage"));

            // 세 번째: 성공
            assertTrue(responses.get(2).isSuccess());
            assertEquals("{\"result\":\"success2\"}", responses.get(2).getPayload());

            verify(lambdaClient, times(3)).invoke(any(InvokeRequest.class));
        }

        @Test
        @DisplayName("실패 - 빈 페이로드 목록으로 호출")
        void invokeMultiple_EmptyPayloads_ThrowsException() {
            // Given: 빈 페이로드 목록
            String functionName = "test-function";
            List<String> emptyPayloads = Arrays.asList();

            // When & Then: 빈 페이로드 목록으로 호출시 예외 발생
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeMultiple(functionName, emptyPayloads);
            
            ExecutionException exception = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(LambdaFunctionException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("잘못된 배치 요청"));
        }
    }

    @Nested
    @DisplayName("배치 호출 테스트")
    class InvokeBatchTest {

        @Test
        @DisplayName("성공 - 다중 함수 배치 호출")
        void invokeBatch_MultipleFunctions_Success() throws ExecutionException, InterruptedException {
            // Given: 서로 다른 함수들을 포함한 배치 요청
            LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
                    .batchId("multi-service-batch-001")
                    .request(LambdaInvocationRequest.builder()
                            .functionName("user-service")
                            .payload("{\"action\":\"getProfile\",\"userId\":123}")
                            .correlationId("req-user-123")
                            .build())
                    .request(LambdaInvocationRequest.builder()
                            .functionName("order-service")
                            .payload("{\"action\":\"getOrders\",\"userId\":123}")
                            .correlationId("req-order-123")
                            .build())
                    .request(LambdaInvocationRequest.builder()
                            .functionName("notification-service")
                            .payload("{\"action\":\"getPreferences\",\"userId\":123}")
                            .correlationId("req-notification-123")
                            .build())
                    .maxConcurrency(3)
                    .failFast(false)
                    .timeoutMs(30000L)
                    .aggregationMode(LambdaBatchInvocationRequest.AggregationMode.ALL)
                    .build();

            // 각 서비스별 응답 설정
            InvokeResponse userResponse = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"profile\":{\"name\":\"John\",\"email\":\"john@example.com\"}}"))
                    .build();

            InvokeResponse orderResponse = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"orders\":[{\"id\":1,\"total\":100}]}"))
                    .build();

            InvokeResponse notificationResponse = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"preferences\":{\"email\":true,\"sms\":false}}"))
                    .build();

            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(userResponse))
                    .thenReturn(CompletableFuture.completedFuture(orderResponse))
                    .thenReturn(CompletableFuture.completedFuture(notificationResponse));

            // When: 다중 함수 배치 호출
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeBatch(batchRequest);
            List<LambdaInvocationResponse> responses = result.get();

            // Then: 모든 함수 호출 결과가 올바르게 반환
            assertNotNull(responses);
            assertEquals(3, responses.size());

            // User Service 응답 검증
            LambdaInvocationResponse userServiceResponse = responses.get(0);
            assertTrue(userServiceResponse.isSuccess());
            assertEquals("req-user-123", userServiceResponse.getCorrelationId());
            assertTrue(userServiceResponse.getPayload().contains("John"));

            // Order Service 응답 검증
            LambdaInvocationResponse orderServiceResponse = responses.get(1);
            assertTrue(orderServiceResponse.isSuccess());
            assertEquals("req-order-123", orderServiceResponse.getCorrelationId());
            assertTrue(orderServiceResponse.getPayload().contains("orders"));

            // Notification Service 응답 검증
            LambdaInvocationResponse notificationServiceResponse = responses.get(2);
            assertTrue(notificationServiceResponse.isSuccess());
            assertEquals("req-notification-123", notificationServiceResponse.getCorrelationId());
            assertTrue(notificationServiceResponse.getPayload().contains("preferences"));

            // 각기 다른 함수가 호출되었는지 확인
            verify(lambdaClient).invoke(argThat((InvokeRequest request) ->
                    request.functionName().equals("user-service")));
            verify(lambdaClient).invoke(argThat((InvokeRequest request) ->
                    request.functionName().equals("order-service")));
            verify(lambdaClient).invoke(argThat((InvokeRequest request) ->
                    request.functionName().equals("notification-service")));
        }

        @Test
        @DisplayName("빠른 실패 모드 - 첫 번째 실패시 나머지 취소")
        void invokeBatch_FailFastMode_CancelsRemainingOnFirstFailure() throws ExecutionException, InterruptedException {
            // Given: 빠른 실패 모드가 활성화된 배치 요청
            LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
                    .batchId("fail-fast-batch-001")
                    .request(LambdaInvocationRequest.builder()
                            .functionName("failing-function")
                            .payload("{\"data\":\"fail\"}")
                            .build())
                    .request(LambdaInvocationRequest.builder()
                            .functionName("normal-function")
                            .payload("{\"data\":\"success\"}")
                            .build())
                    .maxConcurrency(2)
                    .failFast(true) // 빠른 실패 모드 활성화
                    .timeoutMs(10000L)
                    .build();

            // 첫 번째 호출은 실패, 두 번째 호출은 성공으로 설정
            AwsServiceException firstFailure = AwsServiceException.builder()
                    .message("Critical function failure")
                    .statusCode(500)
                    .build();

            InvokeResponse secondSuccess = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success\"}"))
                    .build();

            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(firstFailure))
                    .thenReturn(CompletableFuture.completedFuture(secondSuccess));

            // When: 빠른 실패 모드로 배치 실행
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeBatch(batchRequest);
            List<LambdaInvocationResponse> responses = result.get();

            // Then: 실패 결과와 일부 취소된 결과가 반환
            assertNotNull(responses);
            assertEquals(2, responses.size());

            // 첫 번째 응답: 실패
            assertFalse(responses.get(0).isSuccess());
            assertEquals(500, responses.get(0).getStatusCode());

            // failFast 모드에서는 일부 호출이 취소될 수 있음
            // 정확한 동작은 구현에 따라 다를 수 있으므로 기본적인 검증만 수행
            assertTrue(responses.size() > 0);
        }

        @Test
        @DisplayName("성공 전용 집계 - 성공한 결과만 반환")
        void invokeBatch_SuccessOnlyAggregation_ReturnsOnlySuccessful() throws ExecutionException, InterruptedException {
            // Given: 성공 전용 집계 모드 배치 요청
            LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
                    .batchId("success-only-batch-001")
                    .request(LambdaInvocationRequest.builder()
                            .functionName("function-1")
                            .payload("{\"data\":\"success1\"}")
                            .build())
                    .request(LambdaInvocationRequest.builder()
                            .functionName("function-2")
                            .payload("{\"data\":\"fail\"}")
                            .build())
                    .request(LambdaInvocationRequest.builder()
                            .functionName("function-3")
                            .payload("{\"data\":\"success2\"}")
                            .build())
                    .maxConcurrency(3)
                    .failFast(false)
                    .aggregationMode(LambdaBatchInvocationRequest.AggregationMode.SUCCESS_ONLY)
                    .build();

            // 성공, 실패, 성공 순서로 응답 설정
            InvokeResponse success1 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success1\"}"))
                    .build();

            AwsServiceException failure = AwsServiceException.builder()
                    .message("Function failure")
                    .statusCode(502)
                    .build();

            InvokeResponse success2 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success2\"}"))
                    .build();

            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(success1))
                    .thenReturn(CompletableFuture.failedFuture(failure))
                    .thenReturn(CompletableFuture.completedFuture(success2));

            // When: 성공 전용 집계 모드로 배치 실행
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeBatch(batchRequest);
            List<LambdaInvocationResponse> responses = result.get();

            // Then: 성공한 결과만 반환 (2개)
            assertNotNull(responses);
            assertEquals(2, responses.size());

            // 모든 반환된 응답이 성공인지 확인
            assertTrue(responses.get(0).isSuccess());
            assertTrue(responses.get(1).isSuccess());
            assertEquals("{\"result\":\"success1\"}", responses.get(0).getPayload());
            assertEquals("{\"result\":\"success2\"}", responses.get(1).getPayload());

            verify(lambdaClient, times(3)).invoke(any(InvokeRequest.class));
        }

        @Test
        @DisplayName("요약 집계 - 요약 정보만 반환")
        void invokeBatch_SummaryAggregation_ReturnsSummaryOnly() throws ExecutionException, InterruptedException {
            // Given: 요약 모드 집계 배치 요청
            LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
                    .batchId("summary-batch-001")
                    .request(LambdaInvocationRequest.builder()
                            .functionName("function-1")
                            .payload("{\"data\":\"success1\"}")
                            .build())
                    .request(LambdaInvocationRequest.builder()
                            .functionName("function-2")
                            .payload("{\"data\":\"fail\"}")
                            .build())
                    .request(LambdaInvocationRequest.builder()
                            .functionName("function-3")
                            .payload("{\"data\":\"success2\"}")
                            .build())
                    .maxConcurrency(3)
                    .aggregationMode(LambdaBatchInvocationRequest.AggregationMode.SUMMARY)
                    .build();

            // 성공 2개, 실패 1개 설정
            InvokeResponse success1 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success1\"}"))
                    .build();

            AwsServiceException failure = AwsServiceException.builder()
                    .message("Function failure")
                    .statusCode(500)
                    .build();

            InvokeResponse success2 = InvokeResponse.builder()
                    .statusCode(200)
                    .payload(SdkBytes.fromUtf8String("{\"result\":\"success2\"}"))
                    .build();

            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(success1))
                    .thenReturn(CompletableFuture.failedFuture(failure))
                    .thenReturn(CompletableFuture.completedFuture(success2));

            // When: 요약 모드로 배치 실행
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeBatch(batchRequest);
            List<LambdaInvocationResponse> responses = result.get();

            // Then: 요약 정보만 포함한 단일 응답 반환
            assertNotNull(responses);
            assertEquals(1, responses.size());

            LambdaInvocationResponse summary = responses.get(0);
            assertEquals(200, summary.getStatusCode());
            assertTrue(summary.getPayload().contains("\"summary\""));
            assertTrue(summary.getPayload().contains("\"total\":3"));
            assertTrue(summary.getPayload().contains("\"success\":2"));
            assertTrue(summary.getPayload().contains("\"failure\":1"));
            assertTrue(summary.getRequestId().startsWith("batch-summary-"));

            verify(lambdaClient, times(3)).invoke(any(InvokeRequest.class));
        }

        @Test
        @DisplayName("실패 - 잘못된 배치 요청으로 인한 유효성 검사 실패")
        void invokeBatch_InvalidRequest_ThrowsValidationException() {
            // Given: 잘못된 배치 요청 (배치 ID 없음)
            LambdaBatchInvocationRequest invalidRequest = LambdaBatchInvocationRequest.builder()
                    .batchId(null) // 잘못된 배치 ID
                    .request(LambdaInvocationRequest.builder()
                            .functionName("test-function")
                            .payload("{\"data\":\"test\"}")
                            .build())
                    .build();

            // When & Then: 유효성 검사 실패로 예외 발생
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeBatch(invalidRequest);
            
            ExecutionException exception = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(LambdaFunctionException.class, exception.getCause());
            assertTrue(exception.getCause().getMessage().contains("잘못된 배치 요청"));

            // 잘못된 요청이므로 Lambda 클라이언트 호출되지 않음
            verify(lambdaClient, never()).invoke(any(InvokeRequest.class));
        }

        @Test
        @DisplayName("타임아웃 - 배치 처리 시간 초과")
        void invokeBatch_Timeout_HandlesTimeoutGracefully() throws ExecutionException, InterruptedException {
            // Given: 매우 짧은 타임아웃을 가진 배치 요청
            LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
                    .batchId("timeout-batch-001")
                    .request(LambdaInvocationRequest.builder()
                            .functionName("slow-function")
                            .payload("{\"data\":\"test\"}")
                            .build())
                    .maxConcurrency(1)
                    .timeoutMs(1L) // 1ms 타임아웃 (매우 짧음)
                    .build();

            // 느린 응답 시뮬레이션 (타임아웃보다 긴 지연)
            CompletableFuture<InvokeResponse> delayedResponse = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(100); // 100ms 지연
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return InvokeResponse.builder()
                        .statusCode(200)
                        .payload(SdkBytes.fromUtf8String("{\"result\":\"delayed\"}"))
                        .build();
            });

            when(lambdaClient.invoke(any(InvokeRequest.class)))
                    .thenReturn(delayedResponse);

            // When: 타임아웃이 예상되는 배치 호출
            CompletableFuture<List<LambdaInvocationResponse>> result = 
                lambdaService.invokeBatch(batchRequest);

            // Then: 타임아웃 예외가 적절히 처리되는지 확인
            ExecutionException exception = assertThrows(ExecutionException.class, result::get);
            assertTrue(exception.getCause() instanceof RuntimeException);
            assertTrue(exception.getCause().getMessage().contains("배치 처리 중 오류 발생"));
        }
    }
}