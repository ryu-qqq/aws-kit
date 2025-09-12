package com.ryuqq.aws.lambda.service;

import com.ryuqq.aws.lambda.exception.LambdaFunctionException;
import com.ryuqq.aws.lambda.properties.LambdaProperties;
import com.ryuqq.aws.lambda.types.LambdaBatchInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaFunctionConfiguration;
import com.ryuqq.aws.lambda.types.LambdaInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaInvocationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 완전한 Lambda 서비스 구현체
 * 모든 Lambda 함수 호출 및 관리 기능 제공
 * 
 * AWS Lambda 서비스의 완전한 구현체입니다.
 * 기본 호출 기능부터 고급 배치 처리, 함수 관리까지 모든 기능을 제공하며, Spring Boot와 완전 통합됩니다.
 * 
 * 주요 기능:
 * - 기본 호출: 동기/비동기 Lambda 함수 호출
 * - 고급 호출: 버전, 로그, 클라이언트 컨텍스트 지원
 * - 배치 처리: 다중 함수 동시 호출 및 결과 집계
 * - 함수 관리: 생성, 수정, 삭제, 조회
 * - 동시성 제어: Semaphore 기반 동시 실행 수 제한
 * - 자동 재시도 메커니즘
 * - 상관관계 ID 추적 지원
 * - 에러 분류 및 처리
 * - 로깅 및 모니터링 지원
 * 
 * 내부 구조:
 * - LambdaAsyncClient: AWS SDK의 비동기 클라이언트 사용
 * - LambdaProperties: 설정 프로퍼티 주입
 * - Semaphore: 동시 실행 수 제어
 * - ExecutorService: 배치 처리를 위한 스레드 풀
 * - CompletableFuture: 모든 작업을 비동기로 처리
 * 
 * 성능 최적화:
 * - 연결 풀링을 통한 효율적 연결 관리
 * - 비동기 I/O로 높은 처리량 확보
 * - Semaphore 기반 동시성 제어
 * - 배치 처리 최적화
 * - 메모리 효율적인 페이로드 처리
 */
@Slf4j
@Service
public class DefaultLambdaService implements LambdaService {

    // AWS Lambda 비동기 클라이언트
    // 내부적으로 HTTP/2와 연결 풀링을 사용하여 성능 최적화
    private final LambdaAsyncClient lambdaClient;
    
    // Lambda 관련 설정 프로퍼티
    // application.yml에서 aws.lambda.* 설정을 바인딩
    private final LambdaProperties properties;
    
    // 동시 실행 수 제어를 위한 세마포어
    // LambdaProperties.maxConcurrentInvocations 설정 기반으로 초기화
    private final Semaphore concurrencyControl;
    
    // 배치 처리를 위한 스레드 풀
    // 배치 작업의 병렬 처리와 결과 집계를 위해 사용
    private final ExecutorService batchExecutor;
    
    /**
     * 생성자 - 동시성 제어 및 스레드 풀 초기화
     */
    public DefaultLambdaService(LambdaAsyncClient lambdaClient, LambdaProperties properties) {
        this.lambdaClient = lambdaClient;
        this.properties = properties;
        
        // 동시 실행 수 제어를 위한 세마포어 초기화
        // 설정된 maxConcurrentInvocations 값으로 허가증 개수 설정
        this.concurrencyControl = new Semaphore(properties.getMaxConcurrentInvocations());
        
        // 배치 처리용 스레드 풀 초기화
        // 최대 스레드 수는 동시 실행 수의 2배로 설정 (I/O 대기 고려)
        this.batchExecutor = Executors.newFixedThreadPool(
            Math.max(properties.getMaxConcurrentInvocations() * 2, 10),
            r -> {
                Thread t = new Thread(r, "lambda-batch-executor");
                t.setDaemon(true); // 애플리케이션 종료 시 강제 종료되도록 설정
                return t;
            }
        );
        
        log.info("Lambda 서비스 초기화 완료 - 최대 동시 실행: {}, 배치 스레드 풀: {}", 
                 properties.getMaxConcurrentInvocations(), 
                 properties.getMaxConcurrentInvocations() * 2);
    }

    @Override
    public CompletableFuture<String> invoke(String functionName, String payload) {
        return invokeWithConcurrencyControl(functionName, payload, null, InvocationType.REQUEST_RESPONSE, null)
                .thenApply(response -> {
                    // 기존 호환성을 위해 함수 에러가 있으면 예외 발생
                    if (response.hasFunctionError()) {
                        throw new RuntimeException("Lambda function error: " + response.getPayload());
                    }
                    return response.getPayload();
                });
    }

    @Override
    public CompletableFuture<String> invokeAsync(String functionName, String payload) {
        return invokeWithConcurrencyControl(functionName, payload, null, InvocationType.EVENT, null)
                .thenApply(response -> response.getRequestId());
    }

    @Override
    public CompletableFuture<String> invokeWithRetry(String functionName, String payload, int maxRetries) {
        // 재시도 메커니즘 시작 로깅 - 디버그 정보 제공
        log.debug("Invoking Lambda function with retry: {} (max retries: {})", functionName, maxRetries);
        
        // 내부 재시도 로직 호출 (시도 횟수 0부터 시작)
        return invokeWithRetryInternal(functionName, payload, maxRetries, 0);
    }

    /**
     * 내부 재시도 로직 구현
     * 
     * 재시도 알고리즘:
     * 1. 기본 invokeWithConcurrencyControl() 메서드 직접 호출 (중복 호출 방지)
     * 2. 성공하면 결과 반환
     * 3. 실패하면 오류 분류 수행
     * 4. 재시도 가능한 오류면 재시도 횟수 확인
     * 5. 재시도 가능하면 다시 시도, 불가능하면 예외 발생
     * 
     * @param functionName Lambda 함수 이름
     * @param payload JSON 페이로드
     * @param maxRetries 최대 재시도 횟수
     * @param attempt 현재 시도 횟수 (0부터 시작)
     * @return 함수 실행 결과
     */
    private CompletableFuture<String> invokeWithRetryInternal(String functionName, String payload, int maxRetries, int attempt) {
        return invokeWithConcurrencyControl(functionName, payload, null, InvocationType.REQUEST_RESPONSE, null)
                .thenApply(response -> {
                    // 함수 에러가 있으면 예외 발생 (기존 호환성)
                    if (response.hasFunctionError()) {
                        throw new RuntimeException("Lambda function error: " + response.getPayload());
                    }
                    return response.getPayload();
                })
                .handle((result, throwable) -> {
                    // 성공한 경우 - 결과 반환
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    
                    // 재시도 불가능한 오류인지 확인 (4xx 클라이언트 오류 등)
                    if (!isRetryableError(throwable)) {
                        log.error("Lambda invocation failed with non-retryable error: {}", throwable.getMessage());
                        CompletableFuture<String> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(throwable);
                        return failedFuture;
                    }
                    
                    // 최대 재시도 횟수 초과 확인
                    if (attempt >= maxRetries) {
                        log.error("Lambda invocation failed after {} attempts: {}", attempt + 1, throwable.getMessage());
                        CompletableFuture<String> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(throwable);
                        return failedFuture;
                    }
                    
                    // 재시도 로직 - 경고 로깅 후 다음 시도
                    log.warn("Lambda invocation attempt {} failed, retrying: {}", attempt + 1, throwable.getMessage());
                    return invokeWithRetryInternal(functionName, payload, maxRetries, attempt + 1);
                })
                .thenCompose(future -> future);  // CompletableFuture의 중첩 제거
    }

    /**
     * 재시도 가능한 오류인지 판단하는 메서드
     * 
     * AWS Lambda 오류 분류:
     * 1. 재시도 가능한 오류 (5xx, 429, 네트워크 오류)
     * 2. 재시도 불가능한 오류 (4xx 클라이언트 오류)
     * 
     * 재시도 로직의 핵심은 일시적 오류와 영구적 오류를 구분하는 것입니다.
     * 일시적 오류는 재시도로 해결될 수 있지만, 영구적 오류는 재시도해도 계속 실패합니다.
     * 
     * @param throwable 발생한 예외
     * @return 재시도 가능 여부
     */
    private boolean isRetryableError(Throwable throwable) {
        // 중첩된 예외들을 모두 검사하여 AWS 서비스 예외 찾기
        Throwable actualThrowable = throwable;
        
        // CompletionException, RuntimeException, LambdaFunctionException 등의 래핑 해제
        while (actualThrowable != null && 
               !(actualThrowable instanceof AwsServiceException) &&
               actualThrowable.getCause() != null &&
               actualThrowable.getCause() != actualThrowable) {
            actualThrowable = actualThrowable.getCause();
        }
        
        // AWS 서비스 예외인 경우 상태 코드로 판단
        if (actualThrowable instanceof AwsServiceException) {
            AwsServiceException awsException = (AwsServiceException) actualThrowable;
            int statusCode = awsException.statusCode();
            
            log.debug("AWS 서비스 예외 감지: statusCode={}, message={}", statusCode, awsException.getMessage());
            
            // 4xx: 클라이언트 오류 - 재시도 불가능
            // 예: 400 Bad Request, 403 Forbidden, 404 Not Found
            // 이런 오류는 요청 자체에 문제가 있어서 재시도해도 계속 실패
            if (statusCode >= 400 && statusCode < 500) {
                log.debug("클라이언트 오류 감지 (재시도 불가능): {}", statusCode);
                return false;
            }
            
            // 5xx: 서버 오류 - 재시도 가능
            // 429: Too Many Requests (Throttling) - 재시도 가능
            // 예: 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable
            // 이런 오류는 일시적일 수 있어서 재시도로 해결 가능
            boolean retryable = statusCode >= 500 || statusCode == 429;
            log.debug("서버 오류 또는 스로틀링 감지 (재시도 {}): {}", retryable ? "가능" : "불가능", statusCode);
            return retryable;
        }
        
        // AWS 서비스 예외가 아닌 경우 (네트워크 오류 등)
        // 보수적으로 재시도 가능으로 분류
        // 예: 연결 타임아웃, DNS 해결 실패 등
        log.debug("비AWS 예외 감지 (재시도 가능): {}", actualThrowable.getClass().getSimpleName());
        return true;
    }

    /**
     * Lambda 응답에서 페이로드를 추출하는 메서드
     * 
     * AWS Lambda 응답 구조:
     * 1. 성공 응답: payload에 함수 반환값 포함
     * 2. 함수 에러 응답: functionError 필드 존재, payload에 에러 정보
     * 3. 시스템 에러 응답: AWS SDK 레벨에서 예외 발생
     * 
     * @param response Lambda 호출 응답
     * @return 함수 실행 결과 (JSON 문자열)
     * @throws RuntimeException 함수 실행 중 에러 발생시
     */
    private String extractPayload(InvokeResponse response) {
        // 함수 레벨 에러 확인 (Lambda 함수 내부에서 발생한 에러)
        // functionError가 null이 아니면 함수 실행 중 에러 발생
        if (response.functionError() != null) {
            String error = response.payload().asUtf8String();
            log.error("Lambda function error: {}", error);
            // 함수 에러는 비즈니스 로직 에러이므로 RuntimeException으로 래핑
            // 재시도하지 않고 즉시 실패 처리
            throw new RuntimeException("Lambda function error: " + error);
        }
        
        // 정상 응답인 경우 페이로드 반환
        // UTF-8 문자열로 디코딩하여 반환 (일반적으로 JSON 형태)
        return response.payload().asUtf8String();
    }

    // ======================================================================================
    // 새로운 고급 호출 기능 구현 (Advanced Invocation Features Implementation)
    // ======================================================================================

    @Override
    public CompletableFuture<LambdaInvocationResponse> invokeWithResponse(LambdaInvocationRequest request) {
        // 상관관계 ID 자동 생성 (없는 경우)
        String correlationId = generateCorrelationIdIfNeeded(request.getCorrelationId());
        
        log.debug("고급 Lambda 함수 호출 시작 [함수={}, 버전={}, 상관관계ID={}]", 
                  request.getFunctionName(), request.getQualifier(), correlationId);

        return invokeWithConcurrencyControl(
            request.getFunctionName(), 
            request.getPayload(), 
            request.getQualifier(),
            InvocationType.REQUEST_RESPONSE, 
            request
        );
    }

    @Override
    public CompletableFuture<List<LambdaInvocationResponse>> invokeMultiple(String functionName, List<String> payloads) {
        // 동일 함수를 여러 페이로드로 호출하는 배치 요청 생성
        LambdaBatchInvocationRequest.LambdaBatchInvocationRequestBuilder batchBuilder = 
            LambdaBatchInvocationRequest.builder()
                .batchId("multiple-" + UUID.randomUUID().toString().substring(0, 8))
                .maxConcurrency(properties.getMaxConcurrentInvocations())
                .timeoutMs(properties.getDefaultBatchTimeoutMs());

        // 각 페이로드에 대한 개별 요청 생성
        for (int i = 0; i < payloads.size(); i++) {
            String correlationId = generateCorrelationIdIfNeeded(null) + "-" + i;
            batchBuilder.request(LambdaInvocationRequest.builder()
                .functionName(functionName)
                .payload(payloads.get(i))
                .correlationId(correlationId)
                .build());
        }

        return invokeBatch(batchBuilder.build());
    }

    @Override
    public CompletableFuture<List<LambdaInvocationResponse>> invokeBatch(LambdaBatchInvocationRequest batchRequest) {
        // 배치 요청 유효성 검증
        if (!batchRequest.isValid()) {
            return CompletableFuture.failedFuture(
                new LambdaFunctionException("잘못된 배치 요청: " + batchRequest.getSummary()));
        }

        log.info("배치 Lambda 호출 시작: {}", batchRequest.getSummary());

        // 배치 처리 시작 시간 기록
        long startTime = System.currentTimeMillis();
        
        // 배치 결과를 담을 리스트 (순서 보장)
        List<CompletableFuture<LambdaInvocationResponse>> futures = new ArrayList<>();
        
        // 실패 추적을 위한 AtomicInteger
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // 각 요청을 CompletableFuture로 변환
        for (int i = 0; i < batchRequest.getRequests().size(); i++) {
            LambdaInvocationRequest request = batchRequest.getRequests().get(i);
            final int index = i; // 람다에서 사용하기 위해 final 변수로 복사
            
            CompletableFuture<LambdaInvocationResponse> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // failFast 모드에서 이미 실패가 발생한 경우 즉시 취소
                    if (batchRequest.isFailFast() && failureCount.get() > 0) {
                        throw new LambdaFunctionException("배치 실패로 인한 조기 종료", 
                                                          request.getFunctionName(), 
                                                          request.getCorrelationId());
                    }
                    
                    // 개별 Lambda 함수 호출
                    return invokeWithConcurrencyControl(
                        request.getFunctionName(),
                        request.getPayload(), 
                        request.getQualifier(),
                        InvocationType.REQUEST_RESPONSE,
                        request
                    ).join(); // 동기적으로 대기
                    
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    
                    // 실패한 경우도 LambdaInvocationResponse 형태로 반환
                    String errorMessage = e.getMessage();
                    String correlationId = request.getCorrelationId();
                    
                    log.error("배치 내 개별 호출 실패 [인덱스={}, 함수={}, 상관관계ID={}]: {}", 
                              index, request.getFunctionName(), correlationId, errorMessage);
                    
                    return LambdaInvocationResponse.builder()
                        .statusCode(500)
                        .functionError("BatchExecutionError")
                        .payload("{\"errorType\":\"BatchExecutionError\",\"errorMessage\":\"" + errorMessage + "\"}")
                        .requestId("batch-error-" + System.currentTimeMillis())
                        .correlationId(correlationId)
                        .build();
                }
            }, batchExecutor);
            
            futures.add(future);
        }

        // 모든 CompletableFuture 완료 대기
        @SuppressWarnings("rawtypes")
        CompletableFuture[] futuresArray = futures.toArray(new CompletableFuture[0]);
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futuresArray);

        // 타임아웃 처리
        CompletableFuture<Void> timeoutFuture = new CompletableFuture<>();
        ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor();
        timeoutExecutor.schedule(() -> {
            timeoutFuture.completeExceptionally(
                new TimeoutException("배치 처리 타임아웃: " + batchRequest.getTimeoutMs() + "ms"));
        }, batchRequest.getTimeoutMs(), TimeUnit.MILLISECONDS);

        // allFutures 또는 타임아웃 중 먼저 완료되는 것을 대기
        return CompletableFuture.anyOf(allFutures, timeoutFuture)
            .thenCompose(result -> {
                timeoutExecutor.shutdown(); // 타임아웃 스케줄러 정리
                
                if (result instanceof Exception) {
                    return CompletableFuture.failedFuture((Exception) result);
                }
                
                // 모든 개별 결과 수집
                List<LambdaInvocationResponse> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                
                // 결과 필터링 (집계 방식에 따라)
                List<LambdaInvocationResponse> filteredResults = filterBatchResults(results, batchRequest.getAggregationMode());
                
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                
                log.info("배치 Lambda 호출 완료 [배치ID={}, 총요청={}, 성공={}, 실패={}, 실행시간={}ms]",
                         batchRequest.getBatchId(), 
                         results.size(),
                         results.stream().mapToInt(r -> r.isSuccess() ? 1 : 0).sum(),
                         failureCount.get(),
                         executionTime);
                
                return CompletableFuture.completedFuture(filteredResults);
            })
            .exceptionally(throwable -> {
                timeoutExecutor.shutdown(); // 예외 발생시에도 정리
                throw new RuntimeException("배치 처리 중 오류 발생", throwable);
            });
    }

    // ======================================================================================
    // 함수 관리 기능 구현 (Function Management Features Implementation)  
    // ======================================================================================

    @Override
    public CompletableFuture<List<LambdaFunctionConfiguration>> listFunctions() {
        log.debug("Lambda 함수 목록 조회 시작");

        return lambdaClient.listFunctions(ListFunctionsRequest.builder().build())
            .thenApply(response -> {
                List<LambdaFunctionConfiguration> configurations = response.functions().stream()
                    .map(this::convertToLambdaFunctionConfiguration)
                    .collect(Collectors.toList());
                
                log.debug("Lambda 함수 목록 조회 완료: {}개 함수 발견", configurations.size());
                return configurations;
            })
            .exceptionally(throwable -> {
                log.error("Lambda 함수 목록 조회 실패", throwable);
                throw new LambdaFunctionException("함수 목록 조회 중 오류 발생", throwable);
            });
    }

    @Override
    public CompletableFuture<LambdaFunctionConfiguration> getFunctionConfiguration(String functionName) {
        log.debug("Lambda 함수 설정 조회: {}", functionName);

        return lambdaClient.getFunction(GetFunctionRequest.builder()
                .functionName(functionName)
                .build())
            .thenApply(response -> {
                LambdaFunctionConfiguration config = convertToLambdaFunctionConfiguration(response.configuration());
                log.debug("Lambda 함수 설정 조회 완료: {}", functionName);
                return config;
            })
            .exceptionally(throwable -> {
                log.error("Lambda 함수 설정 조회 실패: {}", functionName, throwable);
                throw new LambdaFunctionException("함수 설정 조회 중 오류 발생: " + functionName, functionName, throwable);
            });
    }

    @Override
    public CompletableFuture<LambdaFunctionConfiguration> updateFunctionCode(String functionName, byte[] zipFileBytes) {
        log.info("Lambda 함수 코드 업데이트 시작: {} (크기: {}바이트)", functionName, zipFileBytes.length);

        return lambdaClient.updateFunctionCode(UpdateFunctionCodeRequest.builder()
                .functionName(functionName)
                .zipFile(SdkBytes.fromByteArray(zipFileBytes))
                .build())
            .thenApply(response -> {
                // UpdateFunctionCodeResponse에서 FunctionConfiguration을 추출하여 변환
                FunctionConfiguration functionConfig = FunctionConfiguration.builder()
                    .functionName(response.functionName())
                    .functionArn(response.functionArn())
                    .runtime(response.runtime())
                    .role(response.role())
                    .handler(response.handler())
                    .codeSize(response.codeSize())
                    .description(response.description())
                    .timeout(response.timeout())
                    .memorySize(response.memorySize())
                    .lastModified(response.lastModified())
                    .codeSha256(response.codeSha256())
                    .version(response.version())
                    .vpcConfig(response.vpcConfig())
                    .environment(response.environment())
                    .deadLetterConfig(response.deadLetterConfig())
                    .kmsKeyArn(response.kmsKeyArn())
                    .tracingConfig(response.tracingConfig())
                    .masterArn(response.masterArn())
                    .layers(response.layers())
                    .build();
                    
                LambdaFunctionConfiguration config = convertToLambdaFunctionConfiguration(functionConfig);
                log.info("Lambda 함수 코드 업데이트 완료: {} (새 SHA256: {})", 
                         functionName, config.getCodeSha256());
                return config;
            })
            .exceptionally(throwable -> {
                log.error("Lambda 함수 코드 업데이트 실패: {}", functionName, throwable);
                throw new LambdaFunctionException("함수 코드 업데이트 중 오류 발생: " + functionName, functionName, throwable);
            });
    }

    @Override
    public CompletableFuture<LambdaFunctionConfiguration> createFunction(
            String functionName, String runtime, String role, String handler, byte[] zipFileBytes) {
        
        log.info("Lambda 함수 생성 시작: {} (런타임: {}, 크기: {}바이트)", 
                 functionName, runtime, zipFileBytes.length);

        return lambdaClient.createFunction(CreateFunctionRequest.builder()
                .functionName(functionName)
                .runtime(runtime)
                .role(role)
                .handler(handler)
                .code(FunctionCode.builder()
                    .zipFile(SdkBytes.fromByteArray(zipFileBytes))
                    .build())
                .timeout(properties.getTimeout().toSecondsPart())
                .memorySize(128) // 기본 메모리 크기
                .build())
            .thenApply(response -> {
                // CreateFunctionResponse에서 FunctionConfiguration을 추출하여 변환
                FunctionConfiguration functionConfig = FunctionConfiguration.builder()
                    .functionName(response.functionName())
                    .functionArn(response.functionArn())
                    .runtime(response.runtime())
                    .role(response.role())
                    .handler(response.handler())
                    .codeSize(response.codeSize())
                    .description(response.description())
                    .timeout(response.timeout())
                    .memorySize(response.memorySize())
                    .lastModified(response.lastModified())
                    .codeSha256(response.codeSha256())
                    .version(response.version())
                    .vpcConfig(response.vpcConfig())
                    .environment(response.environment())
                    .deadLetterConfig(response.deadLetterConfig())
                    .kmsKeyArn(response.kmsKeyArn())
                    .tracingConfig(response.tracingConfig())
                    .masterArn(response.masterArn())
                    .layers(response.layers())
                    .build();
                    
                LambdaFunctionConfiguration config = convertToLambdaFunctionConfiguration(functionConfig);
                log.info("Lambda 함수 생성 완료: {} (ARN: {})", functionName, config.getFunctionArn());
                return config;
            })
            .exceptionally(throwable -> {
                log.error("Lambda 함수 생성 실패: {}", functionName, throwable);
                throw new LambdaFunctionException("함수 생성 중 오류 발생: " + functionName, functionName, throwable);
            });
    }

    @Override
    public CompletableFuture<Boolean> deleteFunction(String functionName) {
        log.warn("Lambda 함수 삭제 시작: {} - 이 작업은 되돌릴 수 없습니다", functionName);

        return lambdaClient.deleteFunction(DeleteFunctionRequest.builder()
                .functionName(functionName)
                .build())
            .thenApply(response -> {
                log.info("Lambda 함수 삭제 완료: {}", functionName);
                return true;
            })
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof ResourceNotFoundException) {
                    log.warn("Lambda 함수 삭제 요청했으나 존재하지 않음: {}", functionName);
                    return false;
                }
                log.error("Lambda 함수 삭제 실패: {}", functionName, throwable);
                return false;
            });
    }

    // ======================================================================================
    // 내부 헬퍼 메서드들 (Internal Helper Methods)
    // ======================================================================================

    /**
     * 동시성 제어를 적용한 Lambda 함수 호출
     * 
     * @param functionName 함수 이름
     * @param payload 페이로드
     * @param qualifier 함수 버전/별칭
     * @param invocationType 호출 타입
     * @param originalRequest 원본 요청 (고급 기능용)
     * @return Lambda 호출 응답
     */
    private CompletableFuture<LambdaInvocationResponse> invokeWithConcurrencyControl(
            String functionName, String payload, String qualifier, 
            InvocationType invocationType, LambdaInvocationRequest originalRequest) {
        
        // 상관관계 ID 생성 또는 추출
        String correlationId = originalRequest != null ? 
            generateCorrelationIdIfNeeded(originalRequest.getCorrelationId()) :
            generateCorrelationIdIfNeeded(null);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 동시 실행 수 제어 - 세마포어 획득
                concurrencyControl.acquire();
                log.debug("동시성 제어 획득 [함수={}, 상관관계ID={}, 가용허가={}]", 
                          functionName, correlationId, concurrencyControl.availablePermits());
                
                try {
                    // InvokeRequest 빌더 생성
                    InvokeRequest.Builder requestBuilder = InvokeRequest.builder()
                        .functionName(functionName)
                        .payload(SdkBytes.fromUtf8String(payload != null ? payload : "{}"))
                        .invocationType(invocationType);
                    
                    // 선택적 매개변수 설정
                    if (qualifier != null && !qualifier.trim().isEmpty()) {
                        requestBuilder.qualifier(qualifier);
                    }
                    
                    if (originalRequest != null) {
                        // 로그 타입 설정
                        if (originalRequest.getLogType() != null) {
                            requestBuilder.logType(originalRequest.getLogType());
                        }
                        
                        // 클라이언트 컨텍스트 설정
                        if (originalRequest.getClientContext() != null) {
                            requestBuilder.clientContext(originalRequest.getClientContext());
                        }
                    }

                    InvokeRequest invokeRequest = requestBuilder.build();

                    // Lambda 함수 호출 실행
                    long startTime = System.currentTimeMillis();
                    InvokeResponse response = lambdaClient.invoke(invokeRequest).join();
                    long executionTime = System.currentTimeMillis() - startTime;

                    // 응답을 LambdaInvocationResponse로 변환
                    return convertToLambdaInvocationResponse(response, correlationId, executionTime);
                    
                } finally {
                    // 세마포어 해제 (finally 블록에서 반드시 실행)
                    concurrencyControl.release();
                    log.debug("동시성 제어 해제 [함수={}, 상관관계ID={}, 가용허가={}]", 
                              functionName, correlationId, concurrencyControl.availablePermits());
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LambdaFunctionException("Lambda 호출 중 인터럽트 발생", functionName, correlationId, e);
            } catch (Exception e) {
                throw new LambdaFunctionException("Lambda 호출 중 오류 발생: " + e.getMessage(), 
                                                  functionName, correlationId, e);
            }
        }, batchExecutor);
    }

    /**
     * 상관관계 ID 자동 생성 (필요한 경우)
     */
    private String generateCorrelationIdIfNeeded(String existingId) {
        if (existingId != null && !existingId.trim().isEmpty()) {
            return existingId;
        }
        
        if (properties.isAutoGenerateCorrelationId()) {
            return "lambda-" + UUID.randomUUID().toString();
        }
        
        return null;
    }

    /**
     * InvokeResponse를 LambdaInvocationResponse로 변환
     */
    private LambdaInvocationResponse convertToLambdaInvocationResponse(
            InvokeResponse response, String correlationId, long executionTime) {
        
        return LambdaInvocationResponse.builder()
            .payload(response.payload() != null ? response.payload().asUtf8String() : null)
            .statusCode(response.statusCode())
            .functionError(response.functionError())
            .logResult(response.logResult())
            .requestId(response.responseMetadata() != null ? 
                       response.responseMetadata().requestId() : 
                       "unknown-" + System.currentTimeMillis())
            .executionTimeMs(executionTime)
            .correlationId(correlationId)
            .build();
    }

    /**
     * AWS SDK의 FunctionConfiguration을 LambdaFunctionConfiguration으로 변환
     */
    private LambdaFunctionConfiguration convertToLambdaFunctionConfiguration(FunctionConfiguration awsConfig) {
        // VPC 설정 변환
        LambdaFunctionConfiguration.VpcConfig vpcConfig = null;
        if (awsConfig.vpcConfig() != null) {
            vpcConfig = LambdaFunctionConfiguration.VpcConfig.builder()
                .vpcId(awsConfig.vpcConfig().vpcId())
                .subnetIds(awsConfig.vpcConfig().subnetIds())
                .securityGroupIds(awsConfig.vpcConfig().securityGroupIds())
                .build();
        }

        // 레이어 정보 변환
        List<LambdaFunctionConfiguration.LayerInfo> layers = null;
        if (awsConfig.layers() != null) {
            layers = awsConfig.layers().stream()
                .map(layer -> LambdaFunctionConfiguration.LayerInfo.builder()
                    .arn(layer.arn())
                    .codeSize(layer.codeSize())
                    .build())
                .collect(Collectors.toList());
        }

        return LambdaFunctionConfiguration.builder()
            .functionName(awsConfig.functionName())
            .functionArn(awsConfig.functionArn())
            .runtime(awsConfig.runtime() != null ? awsConfig.runtime().toString() : null)
            .role(awsConfig.role())
            .handler(awsConfig.handler())
            .codeSize(awsConfig.codeSize())
            .description(awsConfig.description())
            .timeout(awsConfig.timeout())
            .memorySize(awsConfig.memorySize())
            .lastModified(awsConfig.lastModified() != null ? 
                         Instant.parse(awsConfig.lastModified()) : null)
            .codeSha256(awsConfig.codeSha256())
            .version(awsConfig.version())
            .vpcConfig(vpcConfig)
            .environment(awsConfig.environment() != null ? 
                        awsConfig.environment().variables() : null)
            .deadLetterConfig(awsConfig.deadLetterConfig() != null ? 
                             awsConfig.deadLetterConfig().targetArn() : null)
            .kmsKeyArn(awsConfig.kmsKeyArn())
            .tracingConfig(awsConfig.tracingConfig() != null ? 
                          awsConfig.tracingConfig().mode().toString() : null)
            .masterArn(awsConfig.masterArn())
            .reservedConcurrencyExecutions(null) // SDK version compatibility - set to null for now
            .layers(layers)
            .build();
    }

    /**
     * 배치 결과 필터링 (집계 방식에 따라)
     */
    private List<LambdaInvocationResponse> filterBatchResults(
            List<LambdaInvocationResponse> results, LambdaBatchInvocationRequest.AggregationMode mode) {
        
        switch (mode) {
            case SUCCESS_ONLY:
                return results.stream()
                    .filter(LambdaInvocationResponse::isSuccess)
                    .collect(Collectors.toList());
                    
            case FAILURE_ONLY:
                return results.stream()
                    .filter(response -> !response.isSuccess())
                    .collect(Collectors.toList());
                    
            case SUMMARY:
                // 요약 모드: 첫 번째 결과만 반환하되 요약 정보로 교체
                long successCount = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
                long failureCount = results.size() - successCount;
                
                LambdaInvocationResponse summary = LambdaInvocationResponse.builder()
                    .payload(String.format("{\"summary\":{\"total\":%d,\"success\":%d,\"failure\":%d}}", 
                             results.size(), successCount, failureCount))
                    .statusCode(200)
                    .requestId("batch-summary-" + System.currentTimeMillis())
                    .executionTimeMs(0L)
                    .build();
                
                return Collections.singletonList(summary);
                
            case ALL:
            default:
                return results;
        }
    }
}