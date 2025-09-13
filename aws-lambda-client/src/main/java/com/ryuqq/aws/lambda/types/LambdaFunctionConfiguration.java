package com.ryuqq.aws.lambda.types;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Lambda 함수 설정 정보를 나타내는 타입 추상화 클래스
 * 
 * AWS Lambda 함수의 구성 정보를 캡슐화하여 복잡한 AWS SDK 타입 대신
 * 간단하고 사용하기 쉬운 인터페이스를 제공합니다.
 * 
 * 주요 용도:
 * - 함수 목록 조회 결과
 * - 함수 설정 정보 확인
 * - 함수 생성/수정 결과
 * - 배포 상태 모니터링
 * 
 * 사용 예시:
 * <pre>
 * {@code
 * CompletableFuture<LambdaFunctionConfiguration> future = 
 *     lambdaService.getFunctionConfiguration("my-function");
 * 
 * LambdaFunctionConfiguration config = future.get();
 * 
 * log.info("Function: {} (Runtime: {})", config.getFunctionName(), config.getRuntime());
 * log.info("Memory: {}MB, Timeout: {}s", config.getMemorySize(), config.getTimeout());
 * log.info("Last Modified: {}", config.getLastModified());
 * }
 * </pre>
 */
public record LambdaFunctionConfiguration(
    
    /**
     * 함수 이름
     * 
     * Lambda 함수의 이름입니다.
     * 생성시 지정한 함수 이름으로, 동일한 AWS 계정의 동일한 리전에서 고유해야 합니다.
     * 
     * 명명 규칙:
     * - 길이: 1-64자
     * - 허용 문자: a-z, A-Z, 0-9, 하이픈(-), 언더스코어(_)
     * - 시작: 문자로 시작해야 함
     * 
     * 예시: "user-service", "order_processor", "ImageResizer"
     */
    String functionName,
    
    /**
     * 함수 ARN (Amazon Resource Name)
     * 
     * 함수를 고유하게 식별하는 AWS 리소스 식별자입니다.
     * 다른 AWS 서비스에서 이 함수를 참조할 때 사용합니다.
     * 
     * ARN 형식:
     * arn:aws:lambda:{region}:{account-id}:function:{function-name}
     * 
     * 예시:
     * "arn:aws:lambda:us-east-1:123456789012:function:user-service"
     */
    String functionArn,
    
    /**
     * 함수 런타임 환경
     * 
     * Lambda 함수가 실행되는 런타임 환경을 나타냅니다.
     * AWS에서 지원하는 언어와 버전을 표시합니다.
     * 
     * 주요 런타임:
     * - "java21": Java 21 (Amazon Corretto)
     * - "java17": Java 17 (Amazon Corretto)
     * - "java11": Java 11 (Amazon Corretto)
     * - "java8.al2": Java 8 (Amazon Linux 2)
     * - "python3.12": Python 3.12
     * - "nodejs20.x": Node.js 20.x
     * - "dotnet8": .NET 8
     * - "go1.x": Go 1.x
     * 
     * 런타임 선택 고려사항:
     * - 성능: 최신 버전이 일반적으로 더 빠름
     * - 호환성: 기존 코드와의 호환성 확인 필요
     * - 수명: AWS에서 지원 중단 예정인 런타임 피하기
     */
    String runtime,
    
    /**
     * 함수 실행 역할 ARN
     * 
     * Lambda 함수가 실행될 때 사용하는 IAM 역할의 ARN입니다.
     * 이 역할을 통해 함수가 다른 AWS 서비스에 접근할 수 있는 권한이 결정됩니다.
     * 
     * 필요한 기본 권한:
     * - CloudWatch Logs에 로그 작성 권한
     * - VPC 설정시 ENI 관리 권한
     * 
     * 추가 권한 예시:
     * - S3 버킷 읽기/쓰기
     * - DynamoDB 테이블 액세스
     * - SQS 메시지 처리
     * - SNS 메시지 발행
     * 
     * ARN 예시:
     * "arn:aws:iam::123456789012:role/lambda-execution-role"
     */
    String role,
    
    /**
     * 함수 핸들러 메서드
     * 
     * Lambda 함수의 진입점을 지정하는 문자열입니다.
     * 런타임별로 형식이 다릅니다.
     * 
     * 런타임별 핸들러 형식:
     * - Java: "com.example.Handler::handleRequest"
     * - Python: "lambda_function.lambda_handler"  
     * - Node.js: "index.handler"
     * - C#: "Assembly::Namespace.Class::Method"
     * - Go: "main"
     * 
     * Java 예시 상세:
     * - 클래스: com.example.Handler
     * - 메서드: handleRequest
     * - 시그니처: public String handleRequest(Map<String,Object> event, Context context)
     */
    String handler,
    
    /**
     * 코드 크기 (바이트)
     * 
     * 압축된 배포 패키지의 크기를 바이트 단위로 나타냅니다.
     * 
     * 크기 제한:
     * - 직접 업로드: 50MB (압축)
     * - S3를 통한 업로드: 250MB (압축해제시)
     * - 레이어 포함 총 크기: 250MB (압축해제시)
     * 
     * 최적화 방법:
     * - 불필요한 의존성 제거
     * - 코드 분할 및 레이어 사용
     * - 압축률이 좋은 라이브러리 선택
     */
    Long codeSize,
    
    /**
     * 함수 설명
     * 
     * 함수의 목적과 기능을 설명하는 텍스트입니다.
     * 팀 협업과 유지보수를 위해 명확하고 구체적으로 작성하는 것이 좋습니다.
     * 
     * 길이 제한: 최대 256자
     * 
     * 좋은 설명 예시:
     * - "사용자 주문 처리 및 재고 업데이트"
     * - "이미지 리사이징 및 S3 업로드"
     * - "API 게이트웨이 인증 토큰 검증"
     * 
     * 피해야 할 예시:
     * - "함수" (너무 일반적)
     * - "테스트" (목적 불명확)
     * - "" (빈 설명)
     */
    String description,
    
    /**
     * 함수 타임아웃 (초)
     * 
     * Lambda 함수의 최대 실행 시간을 초 단위로 설정합니다.
     * 이 시간을 초과하면 함수 실행이 강제 종료됩니다.
     * 
     * 설정 범위: 1초 ~ 900초 (15분)
     * 기본값: 3초
     * 
     * 타임아웃 설정 가이드:
     * - API 응답: 5-30초
     * - 데이터 처리: 300-900초
     * - 파일 업로드: 300-600초
     * - 배치 작업: 900초
     * 
     * 주의사항:
     * - 너무 짧으면 정상 작업도 실패
     * - 너무 길면 장애시 비용 낭비
     * - API Gateway는 29초 제한
     */
    Integer timeout,
    
    /**
     * 메모리 크기 (MB)
     * 
     * Lambda 함수에 할당되는 메모리 크기입니다.
     * 메모리 크기에 비례하여 CPU 성능도 결정됩니다.
     * 
     * 설정 범위: 128MB ~ 10,240MB (10GB)
     * 증가 단위: 1MB 단위로 설정 가능
     * 기본값: 128MB
     * 
     * 메모리-CPU 관계:
     * - 128MB: 0.083 vCPU
     * - 1,024MB: 0.67 vCPU  
     * - 1,792MB: 1 vCPU
     * - 3,008MB: 1.77 vCPU
     * - 10,240MB: 6 vCPU
     * 
     * 선택 가이드:
     * - 단순 API: 128-512MB
     * - 데이터 처리: 512-3008MB
     * - 머신러닝: 3008-10240MB
     * - 이미지/비디오 처리: 1792-10240MB
     */
    Integer memorySize,
    
    /**
     * 마지막 수정 시각
     * 
     * 함수 코드나 설정이 마지막으로 수정된 시각입니다.
     * ISO 8601 형식의 UTC 타임스탬프로 제공됩니다.
     * 
     * 용도:
     * - 배포 이력 추적
     * - 변경 감지 및 모니터링
     * - 롤백 시점 결정
     * - 동기화 상태 확인
     * 
     * 예시: 2023-12-01T10:30:45.123Z
     */
    Instant lastModified,
    
    /**
     * 코드 SHA256 해시
     * 
     * 배포된 코드의 SHA256 해시값입니다.
     * 코드 무결성 검증과 변경 감지에 사용됩니다.
     * 
     * 용도:
     * - 배포 검증 (의도한 코드가 배포되었는지 확인)
     * - 코드 변경 감지 (해시 비교로 변경 여부 확인)
     * - 캐시 무효화 (코드 변경시 캐시 갱신)
     * - 보안 감사 (코드 변조 여부 확인)
     * 
     * 형태: 64자리 16진수 문자열
     * 예시: "a1b2c3d4e5f6789012345678901234567890123456789012345678901234567890"
     */
    String codeSha256,
    
    /**
     * 함수 버전
     * 
     * 현재 함수 설정의 버전 번호입니다.
     * 
     * 버전 유형:
     * - $LATEST: 최신 버전 (편집 가능)
     * - 숫자: 게시된 불변 버전 (1, 2, 3...)
     * 
     * 버전 관리 전략:
     * - 개발: $LATEST 사용
     * - 스테이징: 특정 버전 사용
     * - 프로덕션: 별칭(PROD)으로 특정 버전 참조
     * 
     * 버전별 특징:
     * - $LATEST: 편집 가능, 코드/설정 변경 가능
     * - 숫자 버전: 불변, 코드/설정 변경 불가, 독립적 호출 가능
     */
    String version,
    
    /**
     * VPC 설정 정보
     * 
     * Lambda 함수가 VPC 내에서 실행될 때의 네트워크 설정입니다.
     * VPC 설정이 없으면 null입니다.
     * 
     * 포함 정보:
     * - VPC ID
     * - 서브넷 ID 목록
     * - 보안 그룹 ID 목록
     * 
     * VPC 사용 시나리오:
     * - RDS 데이터베이스 접근
     * - 내부 API 서버 호출
     * - 사설 네트워크 리소스 접근
     * - 엄격한 네트워크 보안 요구사항
     * 
     * 주의사항:
     * - 콜드 스타트 시간 증가 (ENI 생성)
     * - NAT 게이트웨이 필요 (인터넷 접근시)
     * - ENI 관리 권한 필요
     */
    VpcConfig vpcConfig,
    
    /**
     * 환경 변수
     * 
     * Lambda 함수 실행 시 사용할 수 있는 환경 변수 목록입니다.
     * 
     * 용도:
     * - 설정 값 전달 (데이터베이스 URL, API 키 등)
     * - 환경별 다른 값 설정 (개발/스테이징/프로덕션)
     * - 민감하지 않은 설정 정보
     * 
     * 제한 사항:
     * - 총 크기: 4KB
     * - 개수: 제한 없음 (크기 제한 내에서)
     * 
     * 보안 고려사항:
     * - 민감한 정보는 AWS Secrets Manager 또는 Parameter Store 사용
     * - API 키, 패스워드 등은 환경변수에 직접 저장 금지
     * 
     * 예시:
     * - "LOG_LEVEL": "INFO"
     * - "DB_HOST": "my-db.region.rds.amazonaws.com"
     * - "STAGE": "production"
     */
    Map<String, String> environment,
    
    /**
     * 데드 레터 큐 설정
     * 
     * 비동기 호출 실패시 메시지를 보낼 SQS 또는 SNS의 ARN입니다.
     * 
     * 용도:
     * - 실패한 이벤트 보존 및 분석
     * - 재처리 로직 구현
     * - 모니터링 및 알림
     * - 데이터 손실 방지
     * 
     * 지원 대상:
     * - Amazon SQS 큐
     * - Amazon SNS 토픽
     * 
     * 실패 조건:
     * - 함수 실행 오류
     * - 타임아웃
     * - 메모리 부족
     * - 최대 재시도 횟수 초과
     * 
     * 예시 ARN:
     * "arn:aws:sqs:us-east-1:123456789012:my-dlq"
     */
    String deadLetterConfig,
    
    /**
     * KMS 키 ARN
     * 
     * 환경변수 암호화에 사용되는 AWS KMS 키의 ARN입니다.
     * 
     * 암호화 범위:
     * - 환경 변수
     * - 데드 레터 큐 설정
     * - 기타 민감한 설정 정보
     * 
     * KMS 키 유형:
     * - AWS 관리형 키: aws/lambda (기본)
     * - 고객 관리형 키: 사용자 정의 키
     * 
     * 보안 이점:
     * - 저장 중 암호화 (encryption at rest)
     * - 키 순환 지원
     * - 세밀한 액세스 제어
     * - 감사 로깅
     * 
     * 예시 ARN:
     * "arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012"
     */
    String kmsKeyArn,
    
    /**
     * 추적 설정
     * 
     * AWS X-Ray를 통한 분산 추적 설정입니다.
     * 
     * 설정 값:
     * - "PassThrough": 상위 서비스의 추적 상태를 따름 (기본)
     * - "Active": 모든 요청을 추적
     * 
     * X-Ray 추적의 이점:
     * - 요청 흐름 시각화
     * - 성능 병목 지점 식별
     * - 에러 발생 위치 추적
     * - 서비스 간 의존성 분석
     * 
     * 비용 고려사항:
     * - 추적된 요청당 과금
     * - 높은 트래픽 시스템에서는 샘플링 필요
     * - 개발/테스트 환경에서는 비활성화 고려
     */
    String tracingConfig,
    
    /**
     * 마스터 ARN (예약된 동시성)
     * 
     * 다른 AWS 계정에서 이 함수를 호출할 때의 마스터 계정 ARN입니다.
     * 크로스 계정 Lambda 호출시 사용됩니다.
     * 
     * 사용 시나리오:
     * - 멀티 테넌트 아키텍처
     * - 계정 분리 전략
     * - 보안 격리 요구사항
     * - 서비스 간 경계 설정
     * 
     * 보안 고려사항:
     * - 최소 권한 원칙 적용
     * - 리소스 기반 정책 설정 필요
     * - 감사 로깅 활성화
     */
    String masterArn,
    
    /**
     * 예약된 동시 실행 수
     * 
     * 이 함수를 위해 예약된 동시 실행 슬롯의 수입니다.
     * 
     * 동시 실행 제어:
     * - null: 제한 없음 (계정 레벨 한도 내에서)
     * - 0: 함수 비활성화 (호출 차단)
     * - 양수: 해당 수만큼 동시 실행 보장 및 제한
     * 
     * 사용 사례:
     * - 중요한 함수의 성능 보장
     * - 리소스 사용량 제한
     * - 비용 제어
     * - 다운스트림 시스템 보호
     * 
     * 주의사항:
     * - 계정 전체 한도에서 차감됨
     * - 다른 함수의 가용성에 영향
     * - 비용 최적화 필요
     */
    Integer reservedConcurrencyExecutions,
    
    /**
     * 태그 정보
     * 
     * Lambda 함수에 연결된 태그 목록입니다.
     * 
     * 태그 활용:
     * - 비용 추적 및 배분
     * - 리소스 그룹핑
     * - 액세스 제어
     * - 자동화 스크립트 필터링
     * - 규정 준수 추적
     * 
     * 일반적인 태그:
     * - Environment: dev/staging/prod
     * - Team: backend/frontend/data
     * - Project: user-service/order-system
     * - CostCenter: engineering/marketing
     * - Owner: john.doe@company.com
     * 
     * 태그 제한:
     * - 키/값 길이: 각각 최대 128자/256자
     * - 개수: 최대 50개
     * - 대소문자 구분 안함
     */
    Map<String, String> tags,
    
    /**
     * 레이어 정보
     * 
     * 함수에서 사용하는 Lambda 레이어 목록입니다.
     * 
     * 레이어 용도:
     * - 공통 라이브러리 공유
     * - 런타임 의존성 분리
     * - 배포 패키지 크기 최적화
     * - 코드 재사용성 향상
     * 
     * 레이어 구성:
     * - 최대 5개 레이어 사용 가능
     * - 순서대로 적용됨
     * - 버전별로 불변
     * 
     * 예시:
     * - AWS 제공 레이어: AWS SDK, 런타임 등
     * - 사용자 정의 레이어: 회사 공통 라이브러리
     * - 서드파티 레이어: 오픈소스 라이브러리
     */
    List<LayerInfo> layers
) {
    
    // Getter methods for backward compatibility with traditional JavaBean pattern
    public String getFunctionName() { return functionName; }
    public String getFunctionArn() { return functionArn; }
    public String getRuntime() { return runtime; }
    public String getRole() { return role; }
    public String getHandler() { return handler; }
    public Long getCodeSize() { return codeSize; }
    public String getDescription() { return description; }
    public Integer getTimeout() { return timeout; }
    public Integer getMemorySize() { return memorySize; }
    public Instant getLastModified() { return lastModified; }
    public String getCodeSha256() { return codeSha256; }
    public String getVersion() { return version; }
    public VpcConfig getVpcConfig() { return vpcConfig; }
    public Map<String, String> getEnvironment() { return environment; }
    public String getDeadLetterConfig() { return deadLetterConfig; }
    public String getKmsKeyArn() { return kmsKeyArn; }
    public String getTracingConfig() { return tracingConfig; }
    public String getMasterArn() { return masterArn; }
    public Integer getReservedConcurrencyExecutions() { return reservedConcurrencyExecutions; }
    public Map<String, String> getTags() { return tags; }
    public List<LayerInfo> getLayers() { return layers; }
    
    /**
     * VPC 설정 정보를 나타내는 중첩 레코드
     */
    public record VpcConfig(
        /**
         * VPC ID
         */
        String vpcId,

        /**
         * 서브넷 ID 목록
         */
        List<String> subnetIds,

        /**
         * 보안 그룹 ID 목록
         */
        List<String> securityGroupIds
    ) {
        /**
         * Builder for VpcConfig
         */
        public static final class Builder {
            private String vpcId;
            private List<String> subnetIds;
            private List<String> securityGroupIds;

            private Builder() {}

            public Builder vpcId(String vpcId) {
                this.vpcId = vpcId;
                return this;
            }

            public Builder subnetIds(List<String> subnetIds) {
                this.subnetIds = subnetIds != null ? List.copyOf(subnetIds) : null;
                return this;
            }

            public Builder securityGroupIds(List<String> securityGroupIds) {
                this.securityGroupIds = securityGroupIds != null ? List.copyOf(securityGroupIds) : null;
                return this;
            }

            public VpcConfig build() {
                return new VpcConfig(vpcId, subnetIds, securityGroupIds);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }
    
    /**
     * 레이어 정보를 나타내는 중첩 레코드
     */
    public record LayerInfo(
        /**
         * 레이어 ARN
         */
        String arn,

        /**
         * 레이어 코드 크기 (바이트)
         */
        Long codeSize,

        /**
         * 레이어 버전
         */
        Long version
    ) {
        /**
         * Builder for LayerInfo
         */
        public static final class Builder {
            private String arn;
            private Long codeSize;
            private Long version;

            private Builder() {}

            public Builder arn(String arn) {
                this.arn = arn;
                return this;
            }

            public Builder codeSize(Long codeSize) {
                this.codeSize = codeSize;
                return this;
            }

            public Builder version(Long version) {
                this.version = version;
                return this;
            }

            public LayerInfo build() {
                return new LayerInfo(arn, codeSize, version);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    /**
     * Builder class for LambdaFunctionConfiguration
     */
    public static final class Builder {
        private String functionName;
        private String functionArn;
        private String runtime;
        private String role;
        private String handler;
        private Long codeSize;
        private String description;
        private Integer timeout;
        private Integer memorySize;
        private Instant lastModified;
        private String codeSha256;
        private String version;
        private VpcConfig vpcConfig;
        private Map<String, String> environment;
        private String deadLetterConfig;
        private String kmsKeyArn;
        private String tracingConfig;
        private String masterArn;
        private Integer reservedConcurrencyExecutions;
        private Map<String, String> tags;
        private List<LayerInfo> layers;

        private Builder() {}

        public Builder functionName(String functionName) {
            this.functionName = functionName;
            return this;
        }

        public Builder functionArn(String functionArn) {
            this.functionArn = functionArn;
            return this;
        }

        public Builder runtime(String runtime) {
            this.runtime = runtime;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder handler(String handler) {
            this.handler = handler;
            return this;
        }

        public Builder codeSize(Long codeSize) {
            this.codeSize = codeSize;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder timeout(Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder memorySize(Integer memorySize) {
            this.memorySize = memorySize;
            return this;
        }

        public Builder lastModified(Instant lastModified) {
            this.lastModified = lastModified;
            return this;
        }

        public Builder codeSha256(String codeSha256) {
            this.codeSha256 = codeSha256;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder vpcConfig(VpcConfig vpcConfig) {
            this.vpcConfig = vpcConfig;
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            this.environment = environment != null ? Map.copyOf(environment) : null;
            return this;
        }

        public Builder deadLetterConfig(String deadLetterConfig) {
            this.deadLetterConfig = deadLetterConfig;
            return this;
        }

        public Builder kmsKeyArn(String kmsKeyArn) {
            this.kmsKeyArn = kmsKeyArn;
            return this;
        }

        public Builder tracingConfig(String tracingConfig) {
            this.tracingConfig = tracingConfig;
            return this;
        }

        public Builder masterArn(String masterArn) {
            this.masterArn = masterArn;
            return this;
        }

        public Builder reservedConcurrencyExecutions(Integer reservedConcurrencyExecutions) {
            this.reservedConcurrencyExecutions = reservedConcurrencyExecutions;
            return this;
        }

        public Builder tags(Map<String, String> tags) {
            this.tags = tags != null ? Map.copyOf(tags) : null;
            return this;
        }

        public Builder layers(List<LayerInfo> layers) {
            this.layers = layers != null ? List.copyOf(layers) : null;
            return this;
        }

        public LambdaFunctionConfiguration build() {
            return new LambdaFunctionConfiguration(
                functionName, functionArn, runtime, role, handler, codeSize, description,
                timeout, memorySize, lastModified, codeSha256, version, vpcConfig,
                environment, deadLetterConfig, kmsKeyArn, tracingConfig, masterArn,
                reservedConcurrencyExecutions, tags, layers
            );
        }
    }

    /**
     * Creates a new builder instance
     * @return new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}