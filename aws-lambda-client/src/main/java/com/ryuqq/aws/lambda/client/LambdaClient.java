package com.ryuqq.aws.lambda.client;

import java.util.concurrent.CompletableFuture;

/**
 * Lambda 클라이언트 인터페이스
 */
public interface LambdaClient {
    
    CompletableFuture<String> invokeFunction(String functionName, String payload);
    
    CompletableFuture<String> invokeFunctionAsync(String functionName, String payload);
    
    CompletableFuture<Boolean> functionExists(String functionName);
}