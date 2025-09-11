package com.ryuqq.aws.lambda.service;

import com.ryuqq.aws.lambda.properties.LambdaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Simplified Lambda service implementation
 * Provides essential Lambda function invocation only
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultLambdaService implements LambdaService {

    private final LambdaAsyncClient lambdaClient;
    private final LambdaProperties properties;

    @Override
    public CompletableFuture<String> invoke(String functionName, String payload) {
        log.debug("Invoking Lambda function synchronously: {}", functionName);

        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payload))
                .invocationType(InvocationType.REQUEST_RESPONSE)
                .build();

        return lambdaClient.invoke(request)
                .thenApply(this::extractPayload);
    }

    @Override
    public CompletableFuture<String> invokeAsync(String functionName, String payload) {
        log.debug("Invoking Lambda function asynchronously: {}", functionName);

        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payload))
                .invocationType(InvocationType.EVENT)
                .build();

        return lambdaClient.invoke(request)
                .thenApply(response -> {
                    String requestId = response.responseMetadata() != null ? 
                            response.responseMetadata().requestId() : "async-" + System.currentTimeMillis();
                    log.debug("Async invocation request ID: {}", requestId);
                    return requestId;
                });
    }

    @Override
    public CompletableFuture<String> invokeWithRetry(String functionName, String payload, int maxRetries) {
        log.debug("Invoking Lambda function with retry: {} (max retries: {})", functionName, maxRetries);
        
        return invokeWithRetryInternal(functionName, payload, maxRetries, 0);
    }

    private CompletableFuture<String> invokeWithRetryInternal(String functionName, String payload, int maxRetries, int attempt) {
        return invoke(functionName, payload)
                .handle((result, throwable) -> {
                    if (throwable == null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    
                    // Check if error is retryable
                    if (!isRetryableError(throwable)) {
                        log.error("Lambda invocation failed with non-retryable error: {}", throwable.getMessage());
                        CompletableFuture<String> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(throwable);
                        return failedFuture;
                    }
                    
                    if (attempt >= maxRetries) {
                        log.error("Lambda invocation failed after {} attempts: {}", attempt + 1, throwable.getMessage());
                        CompletableFuture<String> failedFuture = new CompletableFuture<>();
                        failedFuture.completeExceptionally(throwable);
                        return failedFuture;
                    }
                    
                    log.warn("Lambda invocation attempt {} failed, retrying: {}", attempt + 1, throwable.getMessage());
                    return invokeWithRetryInternal(functionName, payload, maxRetries, attempt + 1);
                })
                .thenCompose(future -> future);
    }

    private boolean isRetryableError(Throwable throwable) {
        // Unwrap CompletionException to get the actual cause
        Throwable actualThrowable = throwable;
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            actualThrowable = throwable.getCause();
        }
        
        if (actualThrowable instanceof AwsServiceException) {
            AwsServiceException awsException = (AwsServiceException) actualThrowable;
            int statusCode = awsException.statusCode();
            
            // Non-retryable client errors (4xx)
            if (statusCode >= 400 && statusCode < 500) {
                return false;
            }
            
            // Retryable server errors (5xx) and throttling (429)
            return statusCode >= 500 || statusCode == 429;
        }
        
        // Unknown errors are considered retryable
        return true;
    }

    private String extractPayload(InvokeResponse response) {
        if (response.functionError() != null) {
            String error = response.payload().asUtf8String();
            log.error("Lambda function error: {}", error);
            throw new RuntimeException("Lambda function error: " + error);
        }
        
        return response.payload().asUtf8String();
    }
}