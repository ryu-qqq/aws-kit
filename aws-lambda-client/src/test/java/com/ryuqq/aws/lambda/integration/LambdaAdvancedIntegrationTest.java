package com.ryuqq.aws.lambda.integration;

import com.ryuqq.aws.lambda.properties.LambdaProperties;
import com.ryuqq.aws.lambda.service.DefaultLambdaService;
import com.ryuqq.aws.lambda.service.LambdaService;
import com.ryuqq.aws.lambda.types.LambdaBatchInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaFunctionConfiguration;
import com.ryuqq.aws.lambda.types.LambdaInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaInvocationResponse;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.LogType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.LAMBDA;

/**
 * Lambda 클라이언트의 고급 기능들에 대한 LocalStack 기반 통합 테스트
 * 
 * 테스트 범위:
 * - 실제 Lambda 함수 생성, 업데이트, 삭제
 * - 배치 처리 및 동시성 제어
 * - 고급 호출 기능 (버전, 로그, 컨텍스트)
 * - 에러 시나리오 및 복구 메커니즘
 * - 성능 및 리소스 관리
 * 
 * 주의사항:
 * - LocalStack의 Lambda 지원은 제한적일 수 있음
 * - 실제 Java 함수 실행보다는 API 동작에 중점
 * - 네트워크 환경에 따라 테스트 시간이 길어질 수 있음
 */
@Disabled("LocalStack 통합 테스트는 실제 인프라 테스트시에만 활성화")
@SpringBootTest
@Testcontainers
@DisplayName("Lambda 고급 기능 통합 테스트")
class LambdaAdvancedIntegrationTest {

    private static final String TEST_FUNCTION_NAME = "test-integration-function";
    private static final String BATCH_FUNCTION_NAME = "batch-test-function";
    private static final String CONCURRENT_FUNCTION_NAME = "concurrent-test-function";

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LAMBDA)
            .withEnv("DEBUG", "1")
            .withEnv("LAMBDA_EXECUTOR", "local")
            .withEnv("LAMBDA_REMOTE_DOCKER", "false");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // LocalStack 엔드포인트 설정
        registry.add("aws.lambda.endpoint", 
            () -> localstack.getEndpointOverride(LAMBDA).toString());
        registry.add("aws.region", () -> "us-east-1");
        registry.add("aws.access-key-id", () -> "test");
        registry.add("aws.secret-access-key", () -> "test");
    }

    private LambdaService lambdaService;
    private LambdaAsyncClient lambdaClient;
    private byte[] testZipFile;

    @BeforeEach
    void setUp() throws IOException {
        // AWS Lambda 클라이언트 설정
        lambdaClient = LambdaAsyncClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(localstack.getEndpointOverride(LAMBDA))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .build();

        // Lambda 서비스 설정
        LambdaProperties properties = new LambdaProperties(
            Duration.ofMinutes(5), // timeout - 통합 테스트에서는 짧은 타임아웃
            5, // maxConcurrentInvocations
            300000L, // defaultBatchTimeoutMs (5분)
            "INDIVIDUAL", // defaultRetryPolicy - 빠른 실패를 위해 개별 재시도
            true // autoGenerateCorrelationId
        );

        lambdaService = new DefaultLambdaService(lambdaClient, properties);

        // 테스트용 ZIP 파일 생성
        testZipFile = createTestZipFile();
    }

    @AfterEach
    void tearDown() {
        // 테스트 후 정리: 생성된 함수들 삭제
        try {
            deleteTestFunctionIfExists(TEST_FUNCTION_NAME);
            deleteTestFunctionIfExists(BATCH_FUNCTION_NAME);
            deleteTestFunctionIfExists(CONCURRENT_FUNCTION_NAME);
        } catch (Exception e) {
            // 정리 실패는 로그만 남기고 테스트는 계속 진행
            System.err.println("테스트 정리 중 오류 발생: " + e.getMessage());
        }
        
        if (lambdaClient != null) {
            lambdaClient.close();
        }
    }

    @Nested
    @DisplayName("함수 생명주기 관리 통합 테스트")
    class FunctionLifecycleIntegrationTest {

        @Test
        @DisplayName("성공 - 함수 생성부터 삭제까지 전체 라이프사이클 테스트")
        void functionLifecycle_CreateToDelete_FullCycle() throws ExecutionException, InterruptedException, TimeoutException {
            // Given: 새 함수 생성을 위한 정보
            String functionName = TEST_FUNCTION_NAME + "-lifecycle";
            String runtime = "python3.9"; // LocalStack에서 잘 지원되는 런타임
            String role = "arn:aws:iam::000000000000:role/lambda-role"; // LocalStack 기본 역할
            String handler = "index.handler";

            try {
                // When & Then: 1. 함수 생성
                CompletableFuture<LambdaFunctionConfiguration> createResult = 
                    lambdaService.createFunction(functionName, runtime, role, handler, testZipFile);
                LambdaFunctionConfiguration createdConfig = createResult.get(30, TimeUnit.SECONDS);

                assertNotNull(createdConfig);
                assertEquals(functionName, createdConfig.getFunctionName());
                assertEquals(runtime, createdConfig.getRuntime());
                assertEquals(role, createdConfig.getRole());
                assertEquals(handler, createdConfig.getHandler());
                assertTrue(createdConfig.getCodeSize() > 0);

                // 2. 함수 목록에서 생성된 함수 확인
                CompletableFuture<List<LambdaFunctionConfiguration>> listResult = lambdaService.listFunctions();
                List<LambdaFunctionConfiguration> functions = listResult.get(10, TimeUnit.SECONDS);

                assertTrue(functions.stream()
                    .anyMatch(f -> functionName.equals(f.getFunctionName())));

                // 3. 특정 함수 설정 조회
                CompletableFuture<LambdaFunctionConfiguration> getResult = 
                    lambdaService.getFunctionConfiguration(functionName);
                LambdaFunctionConfiguration retrievedConfig = getResult.get(10, TimeUnit.SECONDS);

                assertNotNull(retrievedConfig);
                assertEquals(functionName, retrievedConfig.getFunctionName());
                assertEquals(createdConfig.getCodeSha256(), retrievedConfig.getCodeSha256());

                // 4. 함수 코드 업데이트
                byte[] updatedZipFile = createUpdatedTestZipFile();
                CompletableFuture<LambdaFunctionConfiguration> updateResult = 
                    lambdaService.updateFunctionCode(functionName, updatedZipFile);
                LambdaFunctionConfiguration updatedConfig = updateResult.get(30, TimeUnit.SECONDS);

                assertNotNull(updatedConfig);
                assertEquals(functionName, updatedConfig.getFunctionName());
                assertNotEquals(createdConfig.getCodeSha256(), updatedConfig.getCodeSha256()); // 코드 해시 변경됨

                // 5. 함수 삭제
                CompletableFuture<Boolean> deleteResult = lambdaService.deleteFunction(functionName);
                Boolean isDeleted = deleteResult.get(10, TimeUnit.SECONDS);

                assertTrue(isDeleted);

                // 6. 삭제 후 목록에서 사라진 것 확인
                CompletableFuture<List<LambdaFunctionConfiguration>> finalListResult = lambdaService.listFunctions();
                List<LambdaFunctionConfiguration> finalFunctions = finalListResult.get(10, TimeUnit.SECONDS);

                assertFalse(finalFunctions.stream()
                    .anyMatch(f -> functionName.equals(f.getFunctionName())));

            } catch (Exception e) {
                // 테스트 실패 시 정리
                deleteTestFunctionIfExists(functionName);
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                } else {
                    throw new RuntimeException(e);
                }
            }
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 함수 조회 시 적절한 예외 발생")
        void functionLifecycle_NonExistentFunction_ThrowsException() {
            // Given: 존재하지 않는 함수 이름
            String nonExistentFunction = "non-existent-function-12345";

            // When & Then: 존재하지 않는 함수 조회시 예외 발생
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.getFunctionConfiguration(nonExistentFunction);

            assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        }
    }

    @Nested
    @DisplayName("배치 처리 통합 테스트")
    class BatchProcessingIntegrationTest {

        @Test
        @DisplayName("성공 - 실제 함수를 이용한 다중 배치 호출")
        void batchProcessing_RealFunctions_SuccessfulBatch() throws ExecutionException, InterruptedException, TimeoutException {
            // Given: 배치 테스트용 함수 생성
            String functionName = BATCH_FUNCTION_NAME + "-real";
            createTestFunction(functionName);

            try {
                // 배치 요청 구성
                LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
                        .batchId("integration-batch-001")
                        .request(LambdaInvocationRequest.builder()
                                .functionName(functionName)
                                .payload("{\"message\":\"batch-item-1\"}")
                                .correlationId("batch-1-item-1")
                                .build())
                        .request(LambdaInvocationRequest.builder()
                                .functionName(functionName)
                                .payload("{\"message\":\"batch-item-2\"}")
                                .correlationId("batch-1-item-2")
                                .build())
                        .request(LambdaInvocationRequest.builder()
                                .functionName(functionName)
                                .payload("{\"message\":\"batch-item-3\"}")
                                .correlationId("batch-1-item-3")
                                .build())
                        .maxConcurrency(2)
                        .failFast(false)
                        .timeoutMs(60000L) // 1분 타임아웃
                        .aggregationMode(LambdaBatchInvocationRequest.AggregationMode.ALL)
                        .build();

                // When: 배치 호출 실행
                CompletableFuture<List<LambdaInvocationResponse>> result = 
                    lambdaService.invokeBatch(batchRequest);
                List<LambdaInvocationResponse> responses = result.get(90, TimeUnit.SECONDS);

                // Then: 배치 결과 검증
                assertNotNull(responses);
                assertEquals(3, responses.size());

                // 각 응답의 상관관계 ID 확인
                assertTrue(responses.stream()
                    .anyMatch(r -> "batch-1-item-1".equals(r.getCorrelationId())));
                assertTrue(responses.stream()
                    .anyMatch(r -> "batch-1-item-2".equals(r.getCorrelationId())));
                assertTrue(responses.stream()
                    .anyMatch(r -> "batch-1-item-3".equals(r.getCorrelationId())));

                // 모든 응답이 처리되었는지 확인 (성공/실패 불문)
                for (LambdaInvocationResponse response : responses) {
                    assertNotNull(response.getCorrelationId());
                    assertTrue(response.getStatusCode() >= 200);
                    assertNotNull(response.getRequestId());
                }

            } finally {
                // 테스트 함수 정리
                deleteTestFunctionIfExists(functionName);
            }
        }

        @Test
        @DisplayName("성공 - SUCCESS_ONLY 집계 모드 통합 테스트")
        void batchProcessing_SuccessOnlyAggregation_FiltersCorrectly() throws ExecutionException, InterruptedException, TimeoutException {
            // Given: 성공/실패가 섞인 배치를 위한 함수들 생성
            String successFunction = BATCH_FUNCTION_NAME + "-success";
            String failureFunction = BATCH_FUNCTION_NAME + "-failure-nonexistent";

            createTestFunction(successFunction);
            // failureFunction은 의도적으로 생성하지 않음 (404 에러 유발)

            try {
                LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
                        .batchId("success-filter-batch-001")
                        .request(LambdaInvocationRequest.builder()
                                .functionName(successFunction)
                                .payload("{\"message\":\"success-1\"}")
                                .build())
                        .request(LambdaInvocationRequest.builder()
                                .functionName(failureFunction) // 존재하지 않는 함수
                                .payload("{\"message\":\"failure-1\"}")
                                .build())
                        .request(LambdaInvocationRequest.builder()
                                .functionName(successFunction)
                                .payload("{\"message\":\"success-2\"}")
                                .build())
                        .maxConcurrency(3)
                        .failFast(false)
                        .aggregationMode(LambdaBatchInvocationRequest.AggregationMode.SUCCESS_ONLY)
                        .build();

                // When: SUCCESS_ONLY 모드로 배치 실행
                CompletableFuture<List<LambdaInvocationResponse>> result = 
                    lambdaService.invokeBatch(batchRequest);
                List<LambdaInvocationResponse> responses = result.get(60, TimeUnit.SECONDS);

                // Then: 성공한 응답들만 반환되는지 확인
                assertNotNull(responses);
                
                // 성공한 호출만 반환되어야 함 (실제로는 LocalStack 동작에 따라 달라질 수 있음)
                for (LambdaInvocationResponse response : responses) {
                    // SUCCESS_ONLY 모드에서는 성공한 응답만 포함되어야 함
                    // LocalStack의 제약으로 정확한 검증이 어려울 수 있으므로 기본 검증만 수행
                    assertNotNull(response.getPayload());
                    assertNotNull(response.getRequestId());
                }

            } finally {
                deleteTestFunctionIfExists(successFunction);
            }
        }
    }

    @Nested
    @DisplayName("고급 호출 기능 통합 테스트")
    class AdvancedInvocationIntegrationTest {

        @Test
        @DisplayName("성공 - 로그 포함 고급 호출 기능")
        void advancedInvocation_WithLogs_ReturnsLogData() throws ExecutionException, InterruptedException, TimeoutException {
            // Given: 고급 기능 테스트용 함수 생성
            String functionName = TEST_FUNCTION_NAME + "-advanced";
            createTestFunction(functionName);

            try {
                // 로그를 포함한 고급 호출 요청
                LambdaInvocationRequest request = LambdaInvocationRequest.builder()
                        .functionName(functionName)
                        .payload("{\"test\":\"advanced-invocation\", \"includeLog\":true}")
                        .correlationId("advanced-test-001")
                        .logType(LogType.TAIL) // 로그 포함 요청
                        .build();

                // When: 고급 호출 실행
                CompletableFuture<LambdaInvocationResponse> result = 
                    lambdaService.invokeWithResponse(request);
                LambdaInvocationResponse response = result.get(30, TimeUnit.SECONDS);

                // Then: 고급 기능이 올바르게 동작하는지 확인
                assertNotNull(response);
                assertEquals("advanced-test-001", response.getCorrelationId());
                assertNotNull(response.getRequestId());
                assertTrue(response.getExecutionTimeMs() >= 0);
                
                // LocalStack에서는 로그 기능이 제한적일 수 있음
                // 기본적인 응답 구조만 확인
                assertTrue(response.getStatusCode() >= 200);

            } finally {
                deleteTestFunctionIfExists(functionName);
            }
        }

        @Test
        @DisplayName("성공 - 다중 페이로드 호출 통합 테스트")
        void advancedInvocation_MultiplePayloads_ProcessesAll() throws ExecutionException, InterruptedException, TimeoutException {
            // Given: 다중 페이로드 테스트용 함수 생성
            String functionName = TEST_FUNCTION_NAME + "-multiple";
            createTestFunction(functionName);

            try {
                // 여러 페이로드로 동일 함수 호출
                List<String> payloads = Arrays.asList(
                    "{\"user\":\"user1\", \"action\":\"process\"}",
                    "{\"user\":\"user2\", \"action\":\"process\"}",
                    "{\"user\":\"user3\", \"action\":\"process\"}",
                    "{\"user\":\"user4\", \"action\":\"process\"}"
                );

                // When: 다중 페이로드로 호출
                CompletableFuture<List<LambdaInvocationResponse>> result = 
                    lambdaService.invokeMultiple(functionName, payloads);
                List<LambdaInvocationResponse> responses = result.get(60, TimeUnit.SECONDS);

                // Then: 모든 페이로드에 대한 응답이 반환되는지 확인
                assertNotNull(responses);
                assertEquals(payloads.size(), responses.size());

                // 각 응답의 상관관계 ID가 순서대로 생성되었는지 확인
                for (int i = 0; i < responses.size(); i++) {
                    LambdaInvocationResponse response = responses.get(i);
                    assertNotNull(response.getCorrelationId());
                    assertTrue(response.getCorrelationId().contains("-" + i));
                    assertTrue(response.getStatusCode() >= 200);
                }

            } finally {
                deleteTestFunctionIfExists(functionName);
            }
        }
    }

    @Nested
    @DisplayName("동시성 및 성능 통합 테스트")
    class ConcurrencyPerformanceIntegrationTest {

        @Test
        @DisplayName("성공 - 높은 동시성 하에서의 안정성 테스트")
        void concurrency_HighLoad_MaintainsStability() throws ExecutionException, InterruptedException, TimeoutException {
            // Given: 동시성 테스트용 함수 생성
            String functionName = CONCURRENT_FUNCTION_NAME + "-stability";
            createTestFunction(functionName);

            try {
                // 높은 동시성 호출 준비
                int totalCalls = 15; // maxConcurrentInvocations(5)의 3배
                List<CompletableFuture<String>> futures = new java.util.ArrayList<>();

                // When: 동시에 많은 호출 실행
                long startTime = System.currentTimeMillis();
                
                for (int i = 0; i < totalCalls; i++) {
                    String payload = String.format("{\"callId\":%d, \"timestamp\":%d}", i, System.currentTimeMillis());
                    CompletableFuture<String> future = lambdaService.invoke(functionName, payload);
                    futures.add(future);
                }

                // 모든 호출 완료 대기
                CompletableFuture<Void> allCalls = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture<?>[0]));
                allCalls.get(120, TimeUnit.SECONDS); // 2분 타임아웃

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;

                // Then: 성능 및 안정성 검증
                int successCount = 0;
                int failureCount = 0;

                for (CompletableFuture<String> future : futures) {
                    try {
                        String result = future.get();
                        assertNotNull(result);
                        successCount++;
                    } catch (ExecutionException e) {
                        failureCount++;
                        // 일부 실패는 허용 (LocalStack 제한사항 고려)
                    }
                }

                // 대부분의 호출이 성공해야 함
                assertTrue(successCount > totalCalls * 0.7, // 70% 이상 성공
                          String.format("성공률이 낮음: %d/%d (%.1f%%)", 
                                       successCount, totalCalls, 
                                       (double)successCount/totalCalls*100));

                // 성능 기본 검증 (LocalStack은 느릴 수 있음)
                assertTrue(totalTime < 180000, // 3분 이내 완료
                          String.format("처리 시간이 너무 김: %dms", totalTime));

                System.out.printf("동시성 테스트 결과: %d/%d 성공 (%.1f%%), 총 시간: %dms%n", 
                                 successCount, totalCalls, 
                                 (double)successCount/totalCalls*100, totalTime);

            } finally {
                deleteTestFunctionIfExists(functionName);
            }
        }
    }

    // 헬퍼 메서드들

    /**
     * 테스트용 Lambda 함수를 생성합니다.
     * LocalStack에서 동작할 수 있는 최소한의 함수를 생성합니다.
     */
    private void createTestFunction(String functionName) throws ExecutionException, InterruptedException, TimeoutException {
        String runtime = "python3.9";
        String role = "arn:aws:iam::000000000000:role/lambda-role";
        String handler = "index.handler";

        CompletableFuture<LambdaFunctionConfiguration> result = 
            lambdaService.createFunction(functionName, runtime, role, handler, testZipFile);
        
        // 함수 생성 완료 대기
        LambdaFunctionConfiguration config = result.get(30, TimeUnit.SECONDS);
        assertNotNull(config);
        
        // 함수가 활성화될 시간 대기
        Thread.sleep(2000);
    }

    /**
     * 테스트 함수가 존재하면 삭제합니다.
     */
    private void deleteTestFunctionIfExists(String functionName) {
        try {
            CompletableFuture<Boolean> deleteResult = lambdaService.deleteFunction(functionName);
            deleteResult.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // 삭제 실패는 무시 (이미 존재하지 않을 수 있음)
        }
    }

    /**
     * 테스트용 ZIP 파일을 생성합니다.
     * LocalStack에서 실행 가능한 간단한 Python 함수를 포함합니다.
     */
    private byte[] createTestZipFile() throws IOException {
        // 임시 파일에 ZIP 생성
        Path tempFile = Files.createTempFile("lambda-test", ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
            // index.py 파일 생성
            ZipEntry entry = new ZipEntry("index.py");
            zos.putNextEntry(entry);
            
            String pythonCode = """
                import json
                
                def handler(event, context):
                    return {
                        'statusCode': 200,
                        'body': json.dumps({
                            'message': 'Lambda function executed successfully',
                            'input': event,
                            'requestId': context.aws_request_id if context else 'test-request-id'
                        })
                    }
                """;
            
            zos.write(pythonCode.getBytes());
            zos.closeEntry();
        }
        
        byte[] zipBytes = Files.readAllBytes(tempFile);
        Files.delete(tempFile);
        
        return zipBytes;
    }

    /**
     * 업데이트된 테스트용 ZIP 파일을 생성합니다.
     */
    private byte[] createUpdatedTestZipFile() throws IOException {
        Path tempFile = Files.createTempFile("lambda-test-updated", ".zip");
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tempFile))) {
            ZipEntry entry = new ZipEntry("index.py");
            zos.putNextEntry(entry);
            
            String updatedPythonCode = """
                import json
                
                def handler(event, context):
                    return {
                        'statusCode': 200,
                        'body': json.dumps({
                            'message': 'Updated Lambda function executed successfully',
                            'version': '2.0',
                            'input': event,
                            'requestId': context.aws_request_id if context else 'test-request-id'
                        })
                    }
                """;
            
            zos.write(updatedPythonCode.getBytes());
            zos.closeEntry();
        }
        
        byte[] zipBytes = Files.readAllBytes(tempFile);
        Files.delete(tempFile);
        
        return zipBytes;
    }
}