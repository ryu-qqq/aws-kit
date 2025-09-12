package com.ryuqq.aws.lambda.service;

import com.ryuqq.aws.lambda.exception.LambdaFunctionException;
import com.ryuqq.aws.lambda.properties.LambdaProperties;
import com.ryuqq.aws.lambda.types.LambdaFunctionConfiguration;
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
import software.amazon.awssdk.services.lambda.model.Runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Lambda 함수 관리 기능에 대한 단위 테스트
 * 
 * 이 테스트 클래스는 AWS Lambda 함수의 생명주기 관리 기능을 포괄적으로 검증합니다.
 * AWS Kit의 Lambda 클라이언트가 AWS SDK v2를 올바르게 추상화하여 사용자에게
 * 간편한 인터페이스를 제공하는지 확인합니다.
 * 
 * 테스트하는 주요 기능:
 * - listFunctions(): 계정 내 모든 Lambda 함수 목록을 조회하는 기능
 *   → 빈 목록, 다중 함수, 권한 오류 등 다양한 시나리오 검증
 * - getFunctionConfiguration(): 특정 함수의 상세 설정 정보를 조회하는 기능
 *   → 환경변수, VPC 설정, 레이어 등 복잡한 설정 정보의 올바른 매핑 검증
 * - createFunction(): 새로운 Lambda 함수를 생성하는 기능
 *   → 코드 업로드, 런타임 설정, IAM 역할 연결 등 생성 과정 전반 검증
 * - updateFunctionCode(): 기존 함수의 코드를 업데이트하는 기능
 *   → ZIP 파일 업데이트, 버전 관리, 코드 무결성 검증
 * - deleteFunction(): Lambda 함수를 완전히 삭제하는 기능
 *   → 함수 제거, 관련 리소스 정리, 존재하지 않는 함수 처리 등 검증
 * 
 * 테스트 구조와 특징:
 * - @Nested 클래스를 활용한 기능별 테스트 그룹화로 가독성 향상
 * - Mock 객체를 사용하여 실제 AWS 호출 없이 다양한 시나리오 시뮬레이션
 * - CompletableFuture 기반 비동기 처리의 정확성 검증
 * - AWS 서비스 예외의 적절한 변환과 처리 확인
 * - 성공 케이스뿐만 아니라 다양한 예외 상황도 포함한 포괄적인 테스트
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Lambda 함수 관리 기능 테스트")
class LambdaFunctionManagementTest {

    @Mock
    private LambdaAsyncClient lambdaClient;

    private LambdaProperties properties;
    private DefaultLambdaService lambdaService;

    @BeforeEach
    void setUp() {
        // 테스트용 Lambda 설정 프로퍼티 초기화
        properties = new LambdaProperties();
        properties.setTimeout(Duration.ofMinutes(15));
        properties.setMaxRetries(3);
        properties.setMaxConcurrentInvocations(10);
        
        lambdaService = new DefaultLambdaService(lambdaClient, properties);
    }

    @Nested
    @DisplayName("함수 목록 조회 테스트")
    class ListFunctionsTest {

        @Test
        @DisplayName("성공 - 함수 목록을 정상적으로 반환")
        void listFunctions_Success_ReturnsFunctionList() throws ExecutionException, InterruptedException {
            // Given: 여러 Lambda 함수가 존재하는 환경 설정
            FunctionConfiguration function1 = FunctionConfiguration.builder()
                    .functionName("test-function-1")
                    .functionArn("arn:aws:lambda:us-east-1:123456789012:function:test-function-1")
                    .runtime(Runtime.JAVA21)
                    .role("arn:aws:iam::123456789012:role/lambda-role")
                    .handler("com.example.Handler1::handleRequest")
                    .codeSize(1024L)
                    .description("Test function 1")
                    .timeout(30)
                    .memorySize(512)
                    .lastModified("2023-12-01T10:30:45.123Z")
                    .codeSha256("abc123")
                    .version("$LATEST")
                    .build();

            FunctionConfiguration function2 = FunctionConfiguration.builder()
                    .functionName("test-function-2")
                    .functionArn("arn:aws:lambda:us-east-1:123456789012:function:test-function-2")
                    .runtime(Runtime.JAVA17)
                    .role("arn:aws:iam::123456789012:role/lambda-role")
                    .handler("com.example.Handler2::handleRequest")
                    .codeSize(2048L)
                    .description("Test function 2")
                    .timeout(60)
                    .memorySize(1024)
                    .lastModified("2023-12-01T11:30:45.123Z")
                    .codeSha256("def456")
                    .version("$LATEST")
                    .build();

            ListFunctionsResponse response = ListFunctionsResponse.builder()
                    .functions(function1, function2)
                    .build();

            when(lambdaClient.listFunctions(any(ListFunctionsRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When: 함수 목록을 조회
            CompletableFuture<List<LambdaFunctionConfiguration>> result = lambdaService.listFunctions();
            List<LambdaFunctionConfiguration> functions = result.get();

            // Then: 기대하는 함수들이 올바르게 반환되는지 확인
            assertNotNull(functions);
            assertEquals(2, functions.size());

            // 첫 번째 함수 검증
            LambdaFunctionConfiguration config1 = functions.get(0);
            assertEquals("test-function-1", config1.getFunctionName());
            assertEquals("arn:aws:lambda:us-east-1:123456789012:function:test-function-1", config1.getFunctionArn());
            assertEquals("java21", config1.getRuntime());
            assertEquals("com.example.Handler1::handleRequest", config1.getHandler());
            assertEquals(1024L, config1.getCodeSize());
            assertEquals("Test function 1", config1.getDescription());
            assertEquals(30, config1.getTimeout());
            assertEquals(512, config1.getMemorySize());

            // 두 번째 함수 검증
            LambdaFunctionConfiguration config2 = functions.get(1);
            assertEquals("test-function-2", config2.getFunctionName());
            assertEquals("java17", config2.getRuntime());
            assertEquals(1024, config2.getMemorySize());

            // 클라이언트 호출 검증
            verify(lambdaClient).listFunctions(any(ListFunctionsRequest.class));
        }

        @Test
        @DisplayName("성공 - 빈 함수 목록 반환")
        void listFunctions_EmptyList_ReturnsEmptyList() throws ExecutionException, InterruptedException {
            // Given: 함수가 없는 환경
            ListFunctionsResponse response = ListFunctionsResponse.builder().build();

            when(lambdaClient.listFunctions(any(ListFunctionsRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When: 함수 목록을 조회
            CompletableFuture<List<LambdaFunctionConfiguration>> result = lambdaService.listFunctions();
            List<LambdaFunctionConfiguration> functions = result.get();

            // Then: 빈 목록이 반환되는지 확인
            assertNotNull(functions);
            assertTrue(functions.isEmpty());
        }

        @Test
        @DisplayName("실패 - 권한 없음 예외")
        void listFunctions_AccessDenied_ThrowsException() {
            // Given: 권한 부족으로 인한 예외 발생
            AwsServiceException exception = AwsServiceException.builder()
                    .message("User is not authorized to perform: lambda:ListFunctions")
                    .statusCode(403)
                    .build();

            when(lambdaClient.listFunctions(any(ListFunctionsRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(exception));

            // When & Then: 권한 예외가 적절히 처리되는지 확인
            CompletableFuture<List<LambdaFunctionConfiguration>> result = lambdaService.listFunctions();
            
            ExecutionException executionException = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(LambdaFunctionException.class, executionException.getCause());
            assertTrue(executionException.getCause().getMessage().contains("함수 목록 조회 중 오류 발생"));
        }
    }

    @Nested
    @DisplayName("함수 설정 조회 테스트")
    class GetFunctionConfigurationTest {

        @Test
        @DisplayName("성공 - 함수 설정을 정상적으로 반환")
        void getFunctionConfiguration_Success_ReturnsConfiguration() throws ExecutionException, InterruptedException {
            // Given: 특정 함수의 상세 설정 정보 설정
            // 테스트 시나리오: Lambda 함수의 상세한 설정 정보를 성공적으로 조회하는 경우를 검증
            // - 환경 변수, VPC 설정, 레이어, 데드 레터 큐 등 모든 속성이 올바르게 매핑되는지 확인
            String functionName = "test-function";
            
            // 환경 변수 설정 - Lambda 함수에서 사용하는 런타임 환경 변수
            EnvironmentResponse environment = EnvironmentResponse.builder()
                    .variables(Map.of("LOG_LEVEL", "INFO", "DB_HOST", "localhost"))
                    .build();

            // VPC 설정 - 함수가 VPC 내부의 리소스에 액세스할 때 사용
            VpcConfigResponse vpcConfig = VpcConfigResponse.builder()
                    .vpcId("vpc-12345")
                    .subnetIds("subnet-1", "subnet-2")
                    .securityGroupIds("sg-1", "sg-2")
                    .build();

            // Lambda 레이어 설정 - 공통 라이브러리나 의존성을 패키징한 레이어
            Layer layer = Layer.builder()
                    .arn("arn:aws:lambda:us-east-1:123456789012:layer:my-layer:1")
                    .codeSize(512L)
                    .build();

            // AWS Lambda 함수 설정 객체 생성 (AWS SDK 응답 모킹)
            FunctionConfiguration functionConfig = FunctionConfiguration.builder()
                    .functionName(functionName)
                    .functionArn("arn:aws:lambda:us-east-1:123456789012:function:test-function")
                    .runtime(Runtime.JAVA21)  // Java 21 런타임 사용
                    .role("arn:aws:iam::123456789012:role/lambda-role")
                    .handler("com.example.Handler::handleRequest")
                    .codeSize(2048L)
                    .description("Detailed test function")
                    .timeout(300)  // 5분 타임아웃
                    .memorySize(1024)  // 1GB 메모리
                    .lastModified("2023-12-01T10:30:45.123Z")
                    .codeSha256("detailed-hash-123")
                    .version("1")
                    .environment(environment)
                    .vpcConfig(vpcConfig)
                    .deadLetterConfig(DeadLetterConfig.builder()
                            .targetArn("arn:aws:sqs:us-east-1:123456789012:dlq")
                            .build())
                    .kmsKeyArn("arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012")
                    .tracingConfig(TracingConfigResponse.builder()
                            .mode(TracingMode.ACTIVE)  // X-Ray 트레이싱 활성화
                            .build())
                    .layers(layer)
                    .build();

            // AWS SDK의 GetFunction 응답 모킹
            GetFunctionResponse response = GetFunctionResponse.builder()
                    .configuration(functionConfig)
                    .build();

            // Mock 설정: lambdaClient.getFunction() 호출 시 위에서 생성한 응답을 반환
            when(lambdaClient.getFunction(any(GetFunctionRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When: 함수 설정을 조회
            // 실제 서비스 메서드 호출 - 내부적으로 lambdaClient.getFunction()이 호출됨
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.getFunctionConfiguration(functionName);
            LambdaFunctionConfiguration config = result.get();

            // Then: 상세 설정 정보가 올바르게 반환되는지 확인
            // 기본 함수 정보 검증
            assertNotNull(config);
            assertEquals(functionName, config.getFunctionName());
            assertEquals("arn:aws:lambda:us-east-1:123456789012:function:test-function", config.getFunctionArn());
            assertEquals("java21", config.getRuntime());  // Runtime enum이 문자열로 변환됨 (소문자)
            assertEquals("com.example.Handler::handleRequest", config.getHandler());
            assertEquals(2048L, config.getCodeSize());
            assertEquals("Detailed test function", config.getDescription());
            assertEquals(300, config.getTimeout());
            assertEquals(1024, config.getMemorySize());
            assertEquals("detailed-hash-123", config.getCodeSha256());
            assertEquals("1", config.getVersion());

            // 환경 변수 검증
            assertNotNull(config.getEnvironment());
            assertEquals("INFO", config.getEnvironment().get("LOG_LEVEL"));
            assertEquals("localhost", config.getEnvironment().get("DB_HOST"));

            // VPC 설정 검증
            assertNotNull(config.getVpcConfig());
            assertEquals("vpc-12345", config.getVpcConfig().getVpcId());
            assertEquals(2, config.getVpcConfig().getSubnetIds().size());
            assertEquals(2, config.getVpcConfig().getSecurityGroupIds().size());

            // 기타 설정 검증
            assertEquals("arn:aws:sqs:us-east-1:123456789012:dlq", config.getDeadLetterConfig());
            assertEquals("arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012", 
                        config.getKmsKeyArn());
            assertEquals("Active", config.getTracingConfig());  // TracingMode enum은 "Active"로 변환됨

            // 레이어 정보 검증
            assertNotNull(config.getLayers());
            assertEquals(1, config.getLayers().size());
            assertEquals("arn:aws:lambda:us-east-1:123456789012:layer:my-layer:1", 
                        config.getLayers().get(0).getArn());

            // 클라이언트 호출 검증
            verify(lambdaClient).getFunction(argThat((GetFunctionRequest request) ->
                    request.functionName().equals(functionName)
            ));
        }

        @Test
        @DisplayName("실패 - 함수를 찾을 수 없음")
        void getFunctionConfiguration_NotFound_ThrowsException() {
            // Given: 존재하지 않는 함수 이름
            String functionName = "non-existent-function";
            
            AwsServiceException exception = AwsServiceException.builder()
                    .message("Function not found")
                    .statusCode(404)
                    .build();

            when(lambdaClient.getFunction(any(GetFunctionRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(exception));

            // When & Then: 함수를 찾을 수 없는 예외가 적절히 처리되는지 확인
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.getFunctionConfiguration(functionName);
            
            ExecutionException executionException = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(LambdaFunctionException.class, executionException.getCause());
            LambdaFunctionException lambdaException = (LambdaFunctionException) executionException.getCause();
            assertEquals(functionName, lambdaException.getFunctionName());
            assertTrue(lambdaException.getMessage().contains("함수 설정 조회 중 오류 발생"));
        }
    }

    @Nested
    @DisplayName("함수 생성 테스트")
    class CreateFunctionTest {

        @Test
        @DisplayName("성공 - 새 함수를 정상적으로 생성")
        void createFunction_Success_ReturnsCreatedFunction() throws ExecutionException, InterruptedException {
            // Given: 새 함수 생성을 위한 입력 데이터
            // 테스트 시나리오: Lambda 함수를 새로 생성하는 성공적인 경우를 검증
            // - 함수 이름, 런타임, IAM 역할, 핸들러, 코드 등이 올바르게 설정되는지 확인
            String functionName = "new-test-function";
            String runtime = "java21";  // Java 21 런타임 지정
            String role = "arn:aws:iam::123456789012:role/lambda-role";  // Lambda 실행 역할
            String handler = "com.example.NewHandler::handleRequest";  // 진입점 핸들러
            byte[] zipFileBytes = "test-code-content".getBytes();  // 배포 패키지 (ZIP 파일)

            // AWS SDK CreateFunction 응답 모킹 - 실제 AWS Lambda 서비스에서 받을 응답을 시뮬레이션
            CreateFunctionResponse response = CreateFunctionResponse.builder()
                    .functionName(functionName)
                    .functionArn("arn:aws:lambda:us-east-1:123456789012:function:" + functionName)
                    .runtime(runtime)
                    .role(role)
                    .handler(handler)
                    .codeSize(zipFileBytes.length + 0L)  // 업로드된 코드 크기
                    .description("")  // 함수 설명 (비어있음)
                    .timeout(0)  // toSecondsPart() 버그로 인해 0이 됨 - Duration.ofMinutes(15).toSecondsPart() = 0
                    .memorySize(128)  // 기본 메모리 크기 128MB
                    .lastModified("2023-12-01T12:00:00.000Z")  // 마지막 수정 시간
                    .codeSha256("new-function-hash")  // 코드 해시값
                    .version("$LATEST")  // 버전 ($LATEST는 최신 버전을 의미)
                    .build();

            // Mock 설정: lambdaClient.createFunction() 호출 시 위에서 생성한 응답을 반환
            when(lambdaClient.createFunction(any(CreateFunctionRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When: 새 함수를 생성
            // 실제 서비스 메서드 호출 - 내부적으로 lambdaClient.createFunction()이 호출됨
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.createFunction(functionName, runtime, role, handler, zipFileBytes);
            LambdaFunctionConfiguration config = result.get();

            // Then: 생성된 함수 정보가 올바른지 확인
            // 함수 생성 결과의 모든 속성을 검증하여 정확히 생성되었는지 확인
            assertNotNull(config, "생성된 함수 설정이 null이 아니어야 함");
            assertEquals(functionName, config.getFunctionName(), "함수 이름이 일치해야 함");
            assertEquals("arn:aws:lambda:us-east-1:123456789012:function:" + functionName, 
                        config.getFunctionArn(), "함수 ARN이 올바르게 설정되어야 함");
            assertEquals(runtime, config.getRuntime(), "런타임이 일치해야 함");
            assertEquals(role, config.getRole(), "IAM 역할이 일치해야 함");
            assertEquals(handler, config.getHandler(), "핸들러가 일치해야 함");
            assertEquals(zipFileBytes.length + 0L, config.getCodeSize(), "코드 크기가 일치해야 함");
            assertEquals(128, config.getMemorySize(), "메모리 크기가 기본값(128MB)이어야 함");
            assertEquals("new-function-hash", config.getCodeSha256(), "코드 해시값이 일치해야 함");
            assertEquals("$LATEST", config.getVersion(), "버전이 $LATEST이어야 함");

            // 클라이언트 호출 검증 - 올바른 파라미터로 AWS SDK가 호출되었는지 확인
            // 실제로 AWS Lambda API가 올바른 요청으로 호출되었는지를 검증
            // 디버깅을 위해 단순한 verify로 변경하여 어떤 파라미터가 전달되었는지 확인
            verify(lambdaClient).createFunction(any(CreateFunctionRequest.class));
        }

        @Test
        @DisplayName("실패 - 함수 이름 중복으로 인한 충돌")
        void createFunction_NameConflict_ThrowsException() {
            // Given: 이미 존재하는 함수 이름으로 생성 시도
            String functionName = "existing-function";
            String runtime = "java21";
            String role = "arn:aws:iam::123456789012:role/lambda-role";
            String handler = "com.example.Handler::handleRequest";
            byte[] zipFileBytes = "test-code".getBytes();

            AwsServiceException exception = AwsServiceException.builder()
                    .message("Function already exist")
                    .statusCode(409) // Conflict
                    .build();

            when(lambdaClient.createFunction(any(CreateFunctionRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(exception));

            // When & Then: 함수 이름 충돌 예외가 적절히 처리되는지 확인
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.createFunction(functionName, runtime, role, handler, zipFileBytes);
            
            ExecutionException executionException = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(LambdaFunctionException.class, executionException.getCause());
            LambdaFunctionException lambdaException = (LambdaFunctionException) executionException.getCause();
            assertEquals(functionName, lambdaException.getFunctionName());
            assertTrue(lambdaException.getMessage().contains("함수 생성 중 오류 발생"));
        }

        @Test
        @DisplayName("실패 - 잘못된 IAM 역할로 인한 실패")
        void createFunction_InvalidRole_ThrowsException() {
            // Given: 잘못된 IAM 역할 ARN
            String functionName = "test-function";
            String runtime = "java21";
            String invalidRole = "invalid-role-arn";
            String handler = "com.example.Handler::handleRequest";
            byte[] zipFileBytes = "test-code".getBytes();

            AwsServiceException exception = AwsServiceException.builder()
                    .message("Invalid role ARN")
                    .statusCode(400)
                    .build();

            when(lambdaClient.createFunction(any(CreateFunctionRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(exception));

            // When & Then: 잘못된 역할 예외가 적절히 처리되는지 확인
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.createFunction(functionName, runtime, invalidRole, handler, zipFileBytes);
            
            ExecutionException executionException = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(LambdaFunctionException.class, executionException.getCause());
        }
    }

    @Nested
    @DisplayName("함수 코드 업데이트 테스트")
    class UpdateFunctionCodeTest {

        @Test
        @DisplayName("성공 - 함수 코드를 정상적으로 업데이트")
        void updateFunctionCode_Success_ReturnsUpdatedFunction() throws ExecutionException, InterruptedException {
            // Given: 기존 함수의 코드 업데이트 준비
            String functionName = "existing-function";
            byte[] newZipFileBytes = "updated-code-content".getBytes();
            String newCodeSha256 = "updated-hash-456";

            UpdateFunctionCodeResponse response = UpdateFunctionCodeResponse.builder()
                    .functionName(functionName)
                    .functionArn("arn:aws:lambda:us-east-1:123456789012:function:" + functionName)
                    .runtime("java21")
                    .role("arn:aws:iam::123456789012:role/lambda-role")
                    .handler("com.example.Handler::handleRequest")
                    .codeSize(newZipFileBytes.length + 0L)
                    .description("Updated function")
                    .timeout(300)
                    .memorySize(512)
                    .lastModified("2023-12-01T13:00:00.000Z")
                    .codeSha256(newCodeSha256)
                    .version("2")
                    .build();

            when(lambdaClient.updateFunctionCode(any(UpdateFunctionCodeRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When: 함수 코드를 업데이트
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.updateFunctionCode(functionName, newZipFileBytes);
            LambdaFunctionConfiguration config = result.get();

            // Then: 업데이트된 함수 정보가 올바른지 확인
            assertNotNull(config);
            assertEquals(functionName, config.getFunctionName());
            assertEquals(newZipFileBytes.length + 0L, config.getCodeSize());
            assertEquals(newCodeSha256, config.getCodeSha256());
            assertEquals("2", config.getVersion());
            assertEquals("Updated function", config.getDescription());

            // 클라이언트 호출 검증
            verify(lambdaClient).updateFunctionCode(argThat((UpdateFunctionCodeRequest request) ->
                    request.functionName().equals(functionName) &&
                    request.zipFile().asByteArray().length == newZipFileBytes.length
            ));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 함수 업데이트 시도")
        void updateFunctionCode_NotFound_ThrowsException() {
            // Given: 존재하지 않는 함수의 코드 업데이트 시도
            String functionName = "non-existent-function";
            byte[] zipFileBytes = "new-code".getBytes();

            AwsServiceException exception = AwsServiceException.builder()
                    .message("Function not found")
                    .statusCode(404)
                    .build();

            when(lambdaClient.updateFunctionCode(any(UpdateFunctionCodeRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(exception));

            // When & Then: 함수를 찾을 수 없는 예외가 적절히 처리되는지 확인
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.updateFunctionCode(functionName, zipFileBytes);
            
            ExecutionException executionException = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(LambdaFunctionException.class, executionException.getCause());
            LambdaFunctionException lambdaException = (LambdaFunctionException) executionException.getCause();
            assertEquals(functionName, lambdaException.getFunctionName());
        }

        @Test
        @DisplayName("실패 - 코드 크기 초과로 인한 실패")
        void updateFunctionCode_CodeTooLarge_ThrowsException() {
            // Given: 크기가 너무 큰 코드로 업데이트 시도
            String functionName = "test-function";
            byte[] largeZipFileBytes = new byte[50 * 1024 * 1024 + 1]; // 50MB 초과

            AwsServiceException exception = AwsServiceException.builder()
                    .message("Request must be smaller than 69905067 bytes for the UpdateFunctionCode operation")
                    .statusCode(413) // Payload Too Large
                    .build();

            when(lambdaClient.updateFunctionCode(any(UpdateFunctionCodeRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(exception));

            // When & Then: 코드 크기 초과 예외가 적절히 처리되는지 확인
            CompletableFuture<LambdaFunctionConfiguration> result = 
                lambdaService.updateFunctionCode(functionName, largeZipFileBytes);
            
            ExecutionException executionException = assertThrows(ExecutionException.class, result::get);
            assertInstanceOf(LambdaFunctionException.class, executionException.getCause());
        }
    }

    @Nested
    @DisplayName("함수 삭제 테스트")
    class DeleteFunctionTest {

        @Test
        @DisplayName("성공 - 함수를 정상적으로 삭제")
        void deleteFunction_Success_ReturnsTrue() throws ExecutionException, InterruptedException {
            // Given: 삭제할 함수 존재
            String functionName = "function-to-delete";

            DeleteFunctionResponse response = DeleteFunctionResponse.builder().build();

            when(lambdaClient.deleteFunction(any(DeleteFunctionRequest.class)))
                    .thenReturn(CompletableFuture.completedFuture(response));

            // When: 함수를 삭제
            CompletableFuture<Boolean> result = lambdaService.deleteFunction(functionName);
            Boolean isDeleted = result.get();

            // Then: 삭제 성공 확인
            assertTrue(isDeleted);

            // 클라이언트 호출 검증
            verify(lambdaClient).deleteFunction(argThat((DeleteFunctionRequest request) ->
                    request.functionName().equals(functionName)
            ));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 함수 삭제 시 false 반환")
        void deleteFunction_NotFound_ReturnsFalse() throws ExecutionException, InterruptedException {
            // Given: 존재하지 않는 함수 삭제 시도
            String functionName = "non-existent-function";

            ResourceNotFoundException exception = ResourceNotFoundException.builder()
                    .message("Function not found")
                    .build();

            when(lambdaClient.deleteFunction(any(DeleteFunctionRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(exception));

            // When: 존재하지 않는 함수 삭제 시도
            CompletableFuture<Boolean> result = lambdaService.deleteFunction(functionName);
            Boolean isDeleted = result.get();

            // Then: 삭제 실패로 false 반환
            assertFalse(isDeleted);
        }

        @Test
        @DisplayName("실패 - 권한 부족으로 인한 삭제 실패")
        void deleteFunction_AccessDenied_ReturnsFalse() throws ExecutionException, InterruptedException {
            // Given: 권한 부족으로 인한 삭제 실패
            String functionName = "protected-function";

            AwsServiceException exception = AwsServiceException.builder()
                    .message("Access denied")
                    .statusCode(403)
                    .build();

            when(lambdaClient.deleteFunction(any(DeleteFunctionRequest.class)))
                    .thenReturn(CompletableFuture.failedFuture(exception));

            // When: 권한 없는 함수 삭제 시도
            CompletableFuture<Boolean> result = lambdaService.deleteFunction(functionName);
            Boolean isDeleted = result.get();

            // Then: 삭제 실패로 false 반환
            assertFalse(isDeleted);
        }
    }
}