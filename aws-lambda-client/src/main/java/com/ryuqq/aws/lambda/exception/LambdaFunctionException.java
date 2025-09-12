package com.ryuqq.aws.lambda.exception;

import lombok.Getter;

/**
 * Lambda 함수 실행 중 발생하는 사용자 정의 예외 클래스
 * 
 * AWS Lambda 함수 호출과 관련된 모든 예외를 처리하기 위한 전용 예외 클래스입니다.
 * 상세한 에러 정보와 추적 가능한 상관관계 ID를 제공하여 디버깅과 모니터링을 지원합니다.
 * 
 * 주요 특징:
 * - 함수 이름과 상관관계 ID 포함
 * - 체인 예외 지원 (원인 예외 보존)
 * - 로깅과 모니터링에 최적화된 구조
 * 
 * 사용 예시:
 * <pre>
 * {@code
 * try {
 *     CompletableFuture<String> result = lambdaService.invoke("my-function", payload);
 *     return result.get();
 * } catch (LambdaFunctionException e) {
 *     log.error("Lambda function failed: {} (correlationId: {})", 
 *               e.getFunctionName(), e.getCorrelationId(), e);
 *     // 에러 처리 로직
 * }
 * }
 * </pre>
 */
@Getter
public class LambdaFunctionException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;

    /**
     * 실패한 Lambda 함수 이름 또는 ARN
     * 디버깅과 로깅을 위해 어떤 함수에서 오류가 발생했는지 추적
     */
    private final String functionName;
    
    /**
     * 요청 추적을 위한 상관관계 ID
     * 분산 시스템에서 요청을 추적하고 로그를 연관시키기 위해 사용
     * AWS X-Ray 추적 ID, 요청 ID 또는 사용자 정의 ID 사용 가능
     */
    private final String correlationId;
    
    /**
     * 기본 생성자 - 메시지만 포함
     * 
     * @param message 오류 메시지
     */
    public LambdaFunctionException(String message) {
        super(message);
        this.functionName = null;
        this.correlationId = null;
    }
    
    /**
     * 함수 이름을 포함한 생성자
     * 
     * @param message 오류 메시지
     * @param functionName 실패한 Lambda 함수 이름
     */
    public LambdaFunctionException(String message, String functionName) {
        super(message);
        this.functionName = functionName;
        this.correlationId = null;
    }
    
    /**
     * 함수 이름과 상관관계 ID를 포함한 생성자
     * 
     * @param message 오류 메시지
     * @param functionName 실패한 Lambda 함수 이름
     * @param correlationId 요청 추적용 상관관계 ID
     */
    public LambdaFunctionException(String message, String functionName, String correlationId) {
        super(message);
        this.functionName = functionName;
        this.correlationId = correlationId;
    }
    
    /**
     * 원인 예외를 포함한 생성자
     * 
     * @param message 오류 메시지
     * @param cause 원인 예외
     */
    public LambdaFunctionException(String message, Throwable cause) {
        super(message, cause);
        this.functionName = null;
        this.correlationId = null;
    }
    
    /**
     * 함수 이름과 원인 예외를 포함한 생성자
     * 
     * @param message 오류 메시지
     * @param functionName 실패한 Lambda 함수 이름
     * @param cause 원인 예외
     */
    public LambdaFunctionException(String message, String functionName, Throwable cause) {
        super(message, cause);
        this.functionName = functionName;
        this.correlationId = null;
    }
    
    /**
     * 모든 정보를 포함한 완전한 생성자
     * 
     * @param message 오류 메시지
     * @param functionName 실패한 Lambda 함수 이름
     * @param correlationId 요청 추적용 상관관계 ID
     * @param cause 원인 예외
     */
    public LambdaFunctionException(String message, String functionName, String correlationId, Throwable cause) {
        super(message, cause);
        this.functionName = functionName;
        this.correlationId = correlationId;
    }
    
    /**
     * 디버깅을 위한 상세 정보를 포함한 문자열 표현
     * 
     * @return 예외의 상세 정보가 포함된 문자열
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName());
        
        if (functionName != null) {
            sb.append(" [function=").append(functionName).append("]");
        }
        
        if (correlationId != null) {
            sb.append(" [correlationId=").append(correlationId).append("]");
        }
        
        String message = getMessage();
        if (message != null) {
            sb.append(": ").append(message);
        }
        
        return sb.toString();
    }
}