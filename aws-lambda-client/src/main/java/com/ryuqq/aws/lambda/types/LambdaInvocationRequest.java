package com.ryuqq.aws.lambda.types;

import software.amazon.awssdk.services.lambda.model.LogType;

/**
 * Lambda 함수 호출 요청을 위한 타입 추상화 클래스
 * 
 * AWS Lambda 호출시 필요한 모든 매개변수를 캡슐화하여 
 * 복잡한 AWS SDK 타입 대신 간단한 인터페이스를 제공합니다.
 * 
 * 주요 기능:
 * - 함수 버전/별칭 지원
 * - 클라이언트 컨텍스트 설정
 * - 로그 타입 설정 (실행 로그 포함 여부)
 * - 실시간 로그 스트리밍 지원
 * - 상관관계 ID 추적 지원
 * 
 * 사용 예시:
 * <pre>
 * {@code
 * // 기본 호출
 * LambdaInvocationRequest request = LambdaInvocationRequest.builder()
 *     .functionName("my-function")
 *     .payload("{\"key\":\"value\"}")
 *     .build();
 * 
 * // 고급 호출 (버전, 로그, 컨텍스트 포함)
 * LambdaInvocationRequest advancedRequest = LambdaInvocationRequest.builder()
 *     .functionName("my-function")
 *     .qualifier("LATEST")
 *     .payload("{\"key\":\"value\"}")
 *     .clientContext("{\"client\":{\"appTitle\":\"MyApp\"}}")
 *     .logType(LogType.TAIL)
 *     .tail(true)
 *     .correlationId("req-123456")
 *     .build();
 * }
 * </pre>
 */
public record LambdaInvocationRequest(
    
    /**
     * Lambda 함수 이름, ARN, 또는 부분 ARN
     * 
     * 지원되는 형식:
     * - 함수 이름: my-function
     * - 함수 ARN: arn:aws:lambda:us-east-1:123456789012:function:my-function
     * - 부분 ARN: 123456789012:function:my-function
     * 
     * 필수 필드입니다.
     */
    String functionName,
    
    /**
     * 함수의 특정 버전 또는 별칭을 지정
     * 
     * 사용 가능한 값:
     * - $LATEST: 최신 버전 (기본값)
     * - 버전 번호: 1, 2, 3... (특정 버전)
     * - 별칭: PROD, STAGING, DEV (배포 단계별 별칭)
     * 
     * 생략시 $LATEST 사용
     * 
     * 사용 시나리오:
     * - 프로덕션: "PROD" 별칭 사용
     * - 스테이징: "STAGING" 별칭 사용
     * - 테스트: 특정 버전 번호 사용
     */
    String qualifier,
    
    /**
     * Lambda 함수에 전달할 JSON 페이로드
     * 
     * JSON 문자열 형태로 전달되며, Lambda 함수의 event 매개변수로 전달됩니다.
     * null 또는 빈 문자열인 경우 빈 객체 {}가 전달됩니다.
     * 
     * 페이로드 크기 제한:
     * - 동기 호출: 6MB
     * - 비동기 호출: 256KB
     * 
     * 예시:
     * - 간단한 데이터: "{\"name\":\"John\",\"age\":30}"
     * - 복잡한 구조: "{\"user\":{\"id\":123},\"action\":\"update\"}"
     */
    String payload,
    
    /**
     * 모바일 SDK를 위한 클라이언트 컨텍스트 (Base64 인코딩)
     * 
     * 모바일 애플리케이션에서 Lambda를 호출할 때 클라이언트 정보를 전달하기 위해 사용합니다.
     * AWS Mobile SDK에서 자동으로 설정되며, 일반적으로 서버 애플리케이션에서는 사용하지 않습니다.
     * 
     * 포함되는 정보:
     * - 클라이언트 애플리케이션 정보
     * - 사용자 환경 설정
     * - 커스텀 속성
     * 
     * JSON 형태로 작성한 후 Base64로 인코딩하여 전달:
     * "{\"client\":{\"appTitle\":\"MyMobileApp\",\"appVersionCode\":\"1.0\"}}"
     */
    String clientContext,
    
    /**
     * 응답에 포함할 로그 타입 설정
     * 
     * LogType.NONE (기본값):
     * - 로그를 응답에 포함하지 않음
     * - 성능상 가장 빠름
     * - 프로덕션 환경에서 권장
     * 
     * LogType.TAIL:
     * - 함수 실행 로그의 마지막 4KB를 응답에 포함
     * - Base64로 인코딩되어 전달
     * - 디버깅과 모니터링에 유용
     * - 개발/테스트 환경에서 권장
     * 
     * 로그 내용:
     * - console.log, print 등의 출력
     * - Lambda 런타임 로그
     * - 에러 스택 트레이스
     */
    LogType logType,
    
    /**
     * 실시간 로그 스트리밍 활성화 여부
     * 
     * true로 설정시:
     * - 함수 실행 중 실시간으로 로그 스트리밍
     * - 장시간 실행되는 함수의 진행 상황 모니터링 가능
     * - 응답 시간이 약간 증가할 수 있음
     * 
     * false (기본값):
     * - 함수 완료 후 전체 로그 반환
     * - 빠른 응답 시간
     * - 일반적인 사용 케이스
     * 
     * 주의사항:
     * - logType이 LogType.TAIL일 때만 의미 있음
     * - 비동기 호출에서는 효과 없음
     */
    boolean tail,
    
    /**
     * 요청 추적을 위한 상관관계 ID
     * 
     * 분산 시스템에서 요청을 추적하고 로그를 연관시키기 위해 사용합니다.
     * 
     * 사용 패턴:
     * - HTTP 요청 ID 전파
     * - 트랜잭션 ID 추적  
     * - 배치 처리 작업 ID
     * - 사용자 세션 ID
     * 
     * 권장 형식:
     * - UUID: "550e8400-e29b-41d4-a716-446655440000"
     * - 타임스탬프 기반: "req-20231201-123456-001"
     * - 계층적: "batch-2023-order-processing-001"
     * 
     * 로깅 예시:
     * log.info("Processing Lambda request [correlationId={}] for function [{}]", 
     *          request.correlationId(), request.functionName());
     */
    String correlationId
) {

    /**
     * Builder class with default values and validation
     */
    public static final class Builder {
        private String functionName;
        private String qualifier;
        private String payload;
        private String clientContext;
        private LogType logType = LogType.NONE;
        private boolean tail = false;
        private String correlationId;

        private Builder() {}

        public Builder functionName(String functionName) {
            this.functionName = functionName;
            return this;
        }

        public Builder qualifier(String qualifier) {
            this.qualifier = qualifier;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public Builder clientContext(String clientContext) {
            this.clientContext = clientContext;
            return this;
        }

        public Builder logType(LogType logType) {
            this.logType = logType != null ? logType : LogType.NONE;
            return this;
        }

        public Builder tail(boolean tail) {
            this.tail = tail;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public LambdaInvocationRequest build() {
            if (functionName == null || functionName.trim().isEmpty()) {
                throw new IllegalArgumentException("Function name is required");
            }
            return new LambdaInvocationRequest(functionName, qualifier, payload, clientContext,
                                               logType, tail, correlationId);
        }
    }

    /**
     * Creates a new builder instance
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a simple request with just function name and payload
     * @param functionName the Lambda function name
     * @param payload the JSON payload
     * @return LambdaInvocationRequest instance
     */
    public static LambdaInvocationRequest of(String functionName, String payload) {
        return builder()
            .functionName(functionName)
            .payload(payload)
            .build();
    }
}