package com.ryuqq.aws.lambda.types;

import lombok.Builder;
import lombok.Data;

/**
 * Lambda 함수 호출 응답을 위한 타입 추상화 클래스
 * 
 * AWS Lambda 호출 결과를 캡슐화하여 복잡한 AWS SDK 응답 타입 대신
 * 간단하고 사용하기 쉬운 인터페이스를 제공합니다.
 * 
 * 주요 기능:
 * - 함수 실행 결과 페이로드
 * - 실행 로그 정보
 * - 상태 코드와 에러 정보
 * - 실행 메타데이터 (요청 ID, 실행 시간 등)
 * - 요청 추적을 위한 상관관계 ID
 * 
 * 사용 예시:
 * <pre>
 * {@code
 * CompletableFuture<LambdaInvocationResponse> future = 
 *     lambdaService.invokeWithResponse("my-function", payload);
 * 
 * LambdaInvocationResponse response = future.get();
 * 
 * if (response.isSuccess()) {
 *     String result = response.getPayload();
 *     log.info("Function result: {}", result);
 * } else {
 *     log.error("Function failed: {} (status: {})", 
 *               response.getErrorMessage(), response.getStatusCode());
 * }
 * 
 * // 실행 로그 확인 (LogType.TAIL로 요청한 경우)
 * if (response.hasLogs()) {
 *     log.debug("Function logs: {}", response.getLogResult());
 * }
 * }
 * </pre>
 */
@Data
@Builder
public class LambdaInvocationResponse {
    
    /**
     * Lambda 함수의 실행 결과 페이로드
     * 
     * 함수가 반환한 데이터를 JSON 문자열로 표현합니다.
     * 함수 실행이 성공한 경우에만 의미있는 값을 가집니다.
     * 
     * 일반적인 형태:
     * - 성공 응답: "{\"result\":\"success\",\"data\":{...}}"
     * - 에러 응답: "{\"errorType\":\"Exception\",\"errorMessage\":\"...\",\"trace\":[...]}"
     * - 단순 값: "\"Hello World\"", "123", "true"
     * - null 반환: "null"
     */
    private final String payload;
    
    /**
     * HTTP 응답 상태 코드
     * 
     * Lambda 호출의 성공/실패 여부를 나타내는 HTTP 상태 코드입니다.
     * AWS Lambda API의 응답 코드를 그대로 반영합니다.
     * 
     * 주요 상태 코드:
     * - 200: 성공 (함수 정상 실행)
     * - 202: 비동기 호출 수락됨
     * - 400: 잘못된 요청 (페이로드 형식 오류 등)
     * - 403: 권한 없음
     * - 404: 함수 없음
     * - 413: 페이로드 크기 초과
     * - 429: 요청 한도 초과 (Throttling)
     * - 500: 내부 서버 오류
     * - 502: 함수 실행 오류
     * - 503: 서비스 사용 불가
     */
    private final int statusCode;
    
    /**
     * 함수 실행 중 발생한 오류 타입
     * 
     * Lambda 함수 내부에서 예외가 발생한 경우 오류 타입을 나타냅니다.
     * 정상 실행된 경우 null입니다.
     * 
     * 일반적인 오류 타입:
     * - "Unhandled": 처리되지 않은 예외
     * - "Timeout": 함수 실행 시간 초과
     * - "MemoryLimitExceeded": 메모리 한도 초과
     * - 사용자 정의: 함수에서 던진 사용자 정의 예외 이름
     * 
     * 주의: 이 필드는 함수 레벨 오류에만 설정되며,
     * AWS 시스템 레벨 오류(권한, 네트워크 등)는 statusCode로 구분
     */
    private final String functionError;
    
    /**
     * 함수 실행 로그 (Base64 인코딩)
     * 
     * LogType.TAIL로 요청한 경우 함수 실행 로그의 마지막 4KB가 포함됩니다.
     * Base64로 인코딩된 상태로 전달되므로 사용시 디코딩이 필요합니다.
     * 
     * 포함되는 로그:
     * - console.log, print 등의 표준 출력
     * - Lambda 런타임 초기화 로그
     * - 함수 실행 시작/종료 로그
     * - 에러 발생시 스택 트레이스
     * 
     * 디코딩 방법:
     * String decodedLog = new String(Base64.getDecoder().decode(logResult));
     * 
     * null인 경우:
     * - LogType.NONE으로 요청
     * - 비동기 호출
     * - 함수 실행 전 시스템 오류 발생
     */
    private final String logResult;
    
    /**
     * AWS 요청 ID
     * 
     * AWS에서 생성한 고유한 요청 식별자입니다.
     * AWS 지원팀과의 문제 해결, CloudWatch 로그 조회, X-Ray 추적 등에 사용됩니다.
     * 
     * 형태: UUID 형식의 문자열
     * 예시: "8a2c9d1e-4f5b-6c7d-8e9f-0a1b2c3d4e5f"
     * 
     * 용도:
     * - AWS 지원 케이스 생성시 참조
     * - CloudWatch 로그에서 해당 요청 로그 검색
     * - X-Ray에서 분산 추적 연결
     * - 성능 분석 및 디버깅
     */
    private final String requestId;
    
    /**
     * 함수 실행 시간 (밀리초)
     * 
     * Lambda 함수의 실제 실행 시간을 밀리초 단위로 나타냅니다.
     * 과금과 성능 분석에 중요한 메트릭입니다.
     * 
     * 측정 범위:
     * - 함수 코드 실행 시작부터 종료까지
     * - 런타임 초기화 시간 포함
     * - 네트워크 지연시간 제외
     * 
     * 활용:
     * - 성능 모니터링 및 최적화
     * - 비용 계산 (100ms 단위로 과금)
     * - SLA 준수 여부 확인
     * - 타임아웃 설정 최적화
     * 
     * 주의사항:
     * - 함수 실행이 실패해도 실행된 시간은 기록됨
     * - 콜드 스타트시 초기화 시간이 포함되어 더 오래 걸림
     */
    private final Long executionTimeMs;
    
    /**
     * 요청 추적을 위한 상관관계 ID
     * 
     * 원본 요청에서 전달된 상관관계 ID가 그대로 반환됩니다.
     * 분산 시스템에서 요청-응답을 매핑하고 로그를 연관시키는데 사용됩니다.
     * 
     * 사용 패턴:
     * - 로그 집계시 동일한 요청의 로그를 그룹핑
     * - 분산 추적 시스템에서 스팬 연결
     * - 에러 발생시 원본 요청 추적
     * - 성능 분석시 요청별 메트릭 수집
     */
    private final String correlationId;
    
    /**
     * 함수 실행이 성공했는지 확인
     * 
     * @return 성공시 true, 실패시 false
     */
    public boolean isSuccess() {
        return statusCode == 200 && functionError == null;
    }
    
    /**
     * 함수 실행 로그가 포함되어 있는지 확인
     * 
     * @return 로그가 있으면 true, 없으면 false
     */
    public boolean hasLogs() {
        return logResult != null && !logResult.trim().isEmpty();
    }
    
    /**
     * 함수 레벨 에러가 발생했는지 확인
     * 
     * @return 함수 에러가 있으면 true, 없으면 false
     */
    public boolean hasFunctionError() {
        return functionError != null;
    }
    
    /**
     * 실행 로그를 디코딩하여 반환
     * 
     * Base64로 인코딩된 로그를 원본 텍스트로 디코딩합니다.
     * 로그가 없는 경우 빈 문자열을 반환합니다.
     * 
     * @return 디코딩된 로그 텍스트
     */
    public String getDecodedLogResult() {
        if (!hasLogs()) {
            return "";
        }
        
        try {
            return new String(java.util.Base64.getDecoder().decode(logResult));
        } catch (Exception e) {
            return "Log decoding failed: " + e.getMessage();
        }
    }
    
    /**
     * 에러 메시지 추출
     * 
     * 함수 에러가 있는 경우 페이로드에서 에러 메시지를 추출하거나,
     * HTTP 상태 코드 기반으로 일반적인 에러 메시지를 반환합니다.
     * 
     * @return 에러 메시지 또는 null (성공시)
     */
    public String getErrorMessage() {
        if (isSuccess()) {
            return null;
        }
        
        if (hasFunctionError()) {
            // 함수 에러인 경우 페이로드에서 에러 메시지 추출 시도
            if (payload != null && payload.contains("errorMessage")) {
                try {
                    // 간단한 JSON 파싱으로 에러 메시지 추출
                    int startIndex = payload.indexOf("\"errorMessage\":\"") + 16;
                    int endIndex = payload.indexOf("\"", startIndex);
                    if (startIndex > 15 && endIndex > startIndex) {
                        return payload.substring(startIndex, endIndex);
                    }
                } catch (Exception ignored) {
                    // 파싱 실패시 전체 페이로드 반환
                }
                return payload;
            }
            return "Function execution error: " + functionError;
        }
        
        // HTTP 상태 코드 기반 에러 메시지
        switch (statusCode) {
            case 400: return "Bad Request - Invalid payload or parameters";
            case 403: return "Forbidden - Insufficient permissions";
            case 404: return "Not Found - Function does not exist";
            case 413: return "Payload Too Large - Request size exceeds limit";
            case 429: return "Too Many Requests - Rate limit exceeded";
            case 500: return "Internal Server Error - AWS service error";
            case 502: return "Bad Gateway - Function runtime error";
            case 503: return "Service Unavailable - AWS service temporarily unavailable";
            default: return "HTTP " + statusCode + " error";
        }
    }
}