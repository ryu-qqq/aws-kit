package com.ryuqq.aws.lambda.client;

import com.ryuqq.aws.commons.exception.AwsServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaAsyncClient;
import software.amazon.awssdk.services.lambda.model.*;

import java.util.concurrent.CompletableFuture;

/**
 * Lambda 클라이언트 기본 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultLambdaClient implements LambdaClient {

    private final LambdaAsyncClient lambdaAsyncClient;

    @Override
    public CompletableFuture<String> invokeFunction(String functionName, String payload) {
        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payload))
                .invocationType(InvocationType.REQUEST_RESPONSE)
                .build();
        
        return lambdaAsyncClient.invoke(request)
                .thenApply(response -> {
                    log.debug("Function invoked successfully: {}", functionName);
                    return response.payload().asUtf8String();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to invoke function: {}", functionName, throwable);
                    throw new AwsServiceException("lambda", "Failed to invoke function", throwable);
                });
    }

    @Override
    public CompletableFuture<String> invokeFunctionAsync(String functionName, String payload) {
        InvokeRequest request = InvokeRequest.builder()
                .functionName(functionName)
                .payload(SdkBytes.fromUtf8String(payload))
                .invocationType(InvocationType.EVENT)
                .build();
        
        return lambdaAsyncClient.invoke(request)
                .thenApply(response -> {
                    log.debug("Function invoked asynchronously: {}", functionName);
                    return response.payload().asUtf8String();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to invoke function asynchronously: {}", functionName, throwable);
                    throw new AwsServiceException("lambda", "Failed to invoke function async", throwable);
                });
    }

    @Override
    public CompletableFuture<Boolean> functionExists(String functionName) {
        GetFunctionRequest request = GetFunctionRequest.builder()
                .functionName(functionName)
                .build();
        
        return lambdaAsyncClient.getFunction(request)
                .thenApply(response -> true)
                .exceptionally(throwable -> {
                    if (throwable.getCause() instanceof ResourceNotFoundException) {
                        return false;
                    }
                    throw new AwsServiceException("lambda", "Failed to check function existence", throwable);
                });
    }
}