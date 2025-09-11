package com.ryuqq.aws.lambda.service;

import com.ryuqq.aws.lambda.properties.LambdaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;

import java.util.concurrent.CompletableFuture;

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


    private String extractPayload(InvokeResponse response) {
        if (response.functionError() != null) {
            String error = response.payload().asUtf8String();
            log.error("Lambda function error: {}", error);
            throw new RuntimeException("Lambda function error: " + error);
        }
        
        return response.payload().asUtf8String();
    }
}