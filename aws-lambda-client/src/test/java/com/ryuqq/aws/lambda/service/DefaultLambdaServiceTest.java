package com.ryuqq.aws.lambda.service;

import com.ryuqq.aws.lambda.properties.LambdaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultLambdaServiceTest {

    @Mock
    private LambdaAsyncClient lambdaClient;

    private LambdaProperties properties;
    private DefaultLambdaService lambdaService;

    @BeforeEach
    void setUp() {
        properties = new LambdaProperties();
        properties.setTimeout(Duration.ofMinutes(15));
        properties.setMaxRetries(3);
        properties.setMaxConcurrentInvocations(10);
        
        lambdaService = new DefaultLambdaService(lambdaClient, properties);
    }

    @Test
    void invoke_Success_ReturnsResult() throws ExecutionException, InterruptedException {
        // Given
        String functionName = "test-function";
        String payload = "{\"key\":\"value\"}";
        String expectedResult = "{\"result\":\"success\"}";

        InvokeResponse response = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(expectedResult))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = lambdaService.invoke(functionName, payload);

        // Then
        assertEquals(expectedResult, result.get());
        
        verify(lambdaClient).invoke(argThat((InvokeRequest request) ->
                request.functionName().equals(functionName) &&
                request.payload().asUtf8String().equals(payload) &&
                request.invocationType() == InvocationType.REQUEST_RESPONSE
        ));
    }

    @Test
    void invoke_FunctionError_ThrowsException() {
        // Given
        String functionName = "test-function";
        String payload = "{\"key\":\"value\"}";
        String errorPayload = "{\"errorMessage\":\"Function error\"}";

        InvokeResponse response = InvokeResponse.builder()
                .statusCode(200)
                .functionError("Unhandled")
                .payload(SdkBytes.fromUtf8String(errorPayload))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When & Then
        CompletableFuture<String> result = lambdaService.invoke(functionName, payload);
        
        assertThrows(ExecutionException.class, result::get);
    }

    @Test
    void invokeAsync_Success_ReturnsRequestId() throws ExecutionException, InterruptedException {
        // Given
        String functionName = "test-function";
        String payload = "{\"key\":\"value\"}";
        String requestId = "test-request-id";

        InvokeResponse response = InvokeResponse.builder()
                .statusCode(202)
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = lambdaService.invokeAsync(functionName, payload);

        // Then
        assertNotNull(result.get());
        
        verify(lambdaClient).invoke(argThat((InvokeRequest request) ->
                request.functionName().equals(functionName) &&
                request.payload().asUtf8String().equals(payload) &&
                request.invocationType() == InvocationType.EVENT
        ));
    }

    @Test
    void invokeWithRetry_Success_ReturnsResult() throws ExecutionException, InterruptedException {
        // Given
        String functionName = "test-function";
        String payload = "{\"key\":\"value\"}";
        String expectedResult = "{\"result\":\"success\"}";

        InvokeResponse response = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(expectedResult))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        // When
        CompletableFuture<String> result = lambdaService.invokeWithRetry(functionName, payload, 3);

        // Then
        assertEquals(expectedResult, result.get());
        verify(lambdaClient, times(1)).invoke(any(InvokeRequest.class));
    }

    @Test
    void invokeWithRetry_RetryableError_RetriesAndSucceeds() throws ExecutionException, InterruptedException {
        // Given
        String functionName = "test-function";
        String payload = "{\"key\":\"value\"}";
        String expectedResult = "{\"result\":\"success\"}";

        AwsServiceException retryableException = AwsServiceException.builder()
                .message("Service error")
                .statusCode(500)
                .build();

        InvokeResponse successResponse = InvokeResponse.builder()
                .statusCode(200)
                .payload(SdkBytes.fromUtf8String(expectedResult))
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(retryableException))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        // When
        CompletableFuture<String> result = lambdaService.invokeWithRetry(functionName, payload, 3);

        // Then
        assertEquals(expectedResult, result.get());
        verify(lambdaClient, times(2)).invoke(any(InvokeRequest.class));
    }

    @Test
    void invokeWithRetry_NonRetryableError_FailsImmediately() {
        // Given
        String functionName = "test-function";
        String payload = "{\"key\":\"value\"}";

        AwsServiceException nonRetryableException = AwsServiceException.builder()
                .message("Invalid function name")
                .statusCode(404)
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(nonRetryableException));

        // When
        CompletableFuture<String> result = lambdaService.invokeWithRetry(functionName, payload, 3);

        // Then
        assertThrows(ExecutionException.class, result::get);
        verify(lambdaClient, times(1)).invoke(any(InvokeRequest.class));
    }

    @Test
    void invokeWithRetry_MaxRetriesExceeded_FailsAfterRetries() {
        // Given
        String functionName = "test-function";
        String payload = "{\"key\":\"value\"}";

        AwsServiceException retryableException = AwsServiceException.builder()
                .message("Service error")
                .statusCode(500)
                .build();

        when(lambdaClient.invoke(any(InvokeRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(retryableException));

        // When
        CompletableFuture<String> result = lambdaService.invokeWithRetry(functionName, payload, 2);

        // Then
        assertThrows(ExecutionException.class, result::get);
        verify(lambdaClient, times(3)).invoke(any(InvokeRequest.class)); // Initial + 2 retries
    }
}