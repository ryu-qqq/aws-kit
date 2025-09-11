package com.ryuqq.aws.lambda.service;

import java.util.concurrent.CompletableFuture;

/**
 * Lambda 서비스 인터페이스
 * 필수 Lambda 함수 호출 기능만 제공
 */
public interface LambdaService {

    /**
     * 동기 Lambda 함수 호출
     *
     * @param functionName Lambda 함수 이름 또는 ARN
     * @param payload JSON 페이로드
     * @return 함수 실행 결과
     */
    CompletableFuture<String> invoke(String functionName, String payload);

    /**
     * 비동기 Lambda 함수 호출
     *
     * @param functionName Lambda 함수 이름 또는 ARN
     * @param payload JSON 페이로드
     * @return 요청 ID
     */
    CompletableFuture<String> invokeAsync(String functionName, String payload);

    /**
     * 재시도를 포함한 Lambda 함수 호출
     *
     * @param functionName Lambda 함수 이름 또는 ARN
     * @param payload JSON 페이로드
     * @param maxRetries 최대 재시도 횟수
     * @return 함수 실행 결과
     */
    CompletableFuture<String> invokeWithRetry(String functionName, String payload, int maxRetries);
}
