package com.ryuqq.aws.lambda.integration;

import com.ryuqq.aws.lambda.properties.LambdaProperties;
import com.ryuqq.aws.lambda.service.DefaultLambdaService;
import com.ryuqq.aws.lambda.service.LambdaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.*;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * Lambda Service 통합 테스트
 * 
 * 간소화된 LambdaService를 LocalStack과 함께 테스트합니다.
 * 실행 방법: ./gradlew test -Dintegration.tests=true
 */
@Testcontainers
@EnabledIfSystemProperty(named = "integration.tests", matches = "true")
class LambdaServiceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(LambdaServiceIntegrationTest.class);
    
    private static LambdaAsyncClient lambdaClient;
    private static LambdaService lambdaService;
    
    private final String testFunctionName = "test-integration-function";

    @Container
    protected static final LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.LAMBDA)
            .withEnv("DEFAULT_REGION", "us-east-1");

    @BeforeAll
    static void setUpClients() {
        lambdaClient = LambdaAsyncClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.LAMBDA))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .region(Region.US_EAST_1)
                .build();

        LambdaProperties lambdaProperties = new LambdaProperties(
            Duration.ofMinutes(5), // timeout
            5, // maxConcurrentInvocations
            300000L, // defaultBatchTimeoutMs (5분)
            "NONE", // defaultRetryPolicy
            true // autoGenerateCorrelationId
        );
        
        lambdaService = new DefaultLambdaService(lambdaClient, lambdaProperties);
    }

    @BeforeEach
    void setUp() throws Exception {
        createTestFunction();
    }

    @Test
    @DisplayName("LambdaService - Synchronous Invocation")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testSynchronousInvocation() throws Exception {
        String testPayload = "{\"name\": \"Integration Test\", \"value\": 42}";

        log.info("Testing synchronous Lambda invocation");
        String result = lambdaService.invoke(testFunctionName, testPayload).get();
        
        assertThat(result).isNotNull();
        assertThat(result).contains("Hello from Lambda!");
        assertThat(result).contains("Integration Test");
        log.info("Synchronous invocation successful. Result: {}", result);
    }

    @Test
    @DisplayName("LambdaService - Asynchronous Invocation")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testAsynchronousInvocation() throws Exception {
        String testPayload = "{\"name\": \"Async Test\", \"value\": 99}";

        log.info("Testing asynchronous Lambda invocation");
        String requestId = lambdaService.invokeAsync(testFunctionName, testPayload).get();
        
        assertThat(requestId).isNotNull();
        assertThat(requestId).isNotBlank();
        log.info("Asynchronous invocation successful. Request ID: {}", requestId);
    }

    @Test
    @DisplayName("LambdaService - Invocation with Retry")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void testInvocationWithRetry() throws Exception {
        String testPayload = "{\"name\": \"Retry Test\", \"value\": 123}";

        log.info("Testing Lambda invocation with retry");
        String result = lambdaService.invokeWithRetry(testFunctionName, testPayload, 2).get();
        
        assertThat(result).isNotNull();
        assertThat(result).contains("Hello from Lambda!");
        assertThat(result).contains("Retry Test");
        log.info("Invocation with retry successful. Result: {}", result);
    }

    @Test
    @DisplayName("LambdaService - Error Handling for Non-existent Function")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testErrorHandlingForNonExistentFunction() {
        String nonExistentFunction = "non-existent-function-12345";
        String testPayload = "{\"test\": \"data\"}";

        log.info("Testing error handling for non-existent function");
        
        assertThatThrownBy(() -> 
            lambdaService.invoke(nonExistentFunction, testPayload).get())
            .hasCauseInstanceOf(ResourceNotFoundException.class);
            
        assertThatThrownBy(() -> 
            lambdaService.invokeAsync(nonExistentFunction, testPayload).get())
            .hasCauseInstanceOf(ResourceNotFoundException.class);
            
        assertThatThrownBy(() -> 
            lambdaService.invokeWithRetry(nonExistentFunction, testPayload, 1).get())
            .hasCauseInstanceOf(ResourceNotFoundException.class);

        log.info("Error handling tests completed successfully");
    }

    /**
     * Helper method to create a simple test function
     */
    private void createTestFunction() throws Exception {
        String pythonCode = """
                def lambda_handler(event, context):
                    return {
                        'statusCode': 200,
                        'body': {
                            'message': 'Hello from Lambda!',
                            'input': event,
                            'context': {
                                'function_name': context.function_name,
                                'request_id': context.aws_request_id
                            }
                        }
                    }
                """;

        CreateFunctionRequest createRequest = CreateFunctionRequest.builder()
                .functionName(testFunctionName)
                .runtime(software.amazon.awssdk.services.lambda.model.Runtime.PYTHON3_9)
                .role("arn:aws:iam::000000000000:role/lambda-role")
                .handler("lambda_function.lambda_handler")
                .code(FunctionCode.builder()
                        .zipFile(SdkBytes.fromUtf8String(pythonCode))
                        .build())
                .description("Integration test function")
                .timeout(30)
                .memorySize(256)
                .build();

        lambdaClient.createFunction(createRequest).get();
        waitForFunctionActive(testFunctionName);
        log.info("Test function '{}' created and ready", testFunctionName);
    }

    /**
     * Wait for function to be active
     */
    private void waitForFunctionActive(String functionName) throws Exception {
        GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder()
                .functionName(functionName)
                .build();

        for (int i = 0; i < 30; i++) { // Wait up to 30 seconds
            GetFunctionResponse response = lambdaClient.getFunction(getFunctionRequest).get();
            State state = response.configuration().state();
            
            if (state == State.ACTIVE) {
                return;
            }
            
            if (state == State.FAILED) {
                throw new RuntimeException("Function creation failed: " + response.configuration().stateReason());
            }
            
            Thread.sleep(1000); // Wait 1 second
        }
        
        throw new RuntimeException("Function did not become active within timeout");
    }

    /**
     * Wait for function update to complete
     */
    private void waitForFunctionUpdated(String functionName) throws Exception {
        GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder()
                .functionName(functionName)
                .build();

        for (int i = 0; i < 30; i++) { // Wait up to 30 seconds
            GetFunctionResponse response = lambdaClient.getFunction(getFunctionRequest).get();
            LastUpdateStatus status = response.configuration().lastUpdateStatus();
            
            if (status == LastUpdateStatus.SUCCESSFUL) {
                return;
            }
            
            if (status == LastUpdateStatus.FAILED) {
                throw new RuntimeException("Function update failed: " + response.configuration().lastUpdateStatusReason());
            }
            
            Thread.sleep(1000); // Wait 1 second
        }
        
        throw new RuntimeException("Function update did not complete within timeout");
    }
}