package com.ryuqq.aws.lambda.service;

import com.ryuqq.aws.lambda.types.LambdaBatchInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaFunctionConfiguration;
import com.ryuqq.aws.lambda.types.LambdaInvocationRequest;
import com.ryuqq.aws.lambda.types.LambdaInvocationResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lambda 서비스 인터페이스 - 완전한 Lambda 관리 기능 제공
 * 
 * AWS Lambda 함수를 호출하고 관리하기 위한 완전한 인터페이스입니다.
 * 모든 메서드는 CompletableFuture를 반환하여 비동기 처리를 지원합니다.
 * 
 * 주요 특징:
 * - 동기/비동기 호출 방식 지원
 * - 고급 호출 옵션 (버전, 로그, 컨텍스트)
 * - 배치 호출 및 다중 처리
 * - 함수 관리 (생성, 수정, 삭제, 조회)
 * - 자동 재시도 메커니즘
 * - 동시 실행 수 제어
 * - 상관관계 ID 추적 지원
 * - Spring Boot 통합
 * 
 * 사용 예시:
 * <pre>
 * {@code
 * @Autowired
 * private LambdaService lambdaService;
 * 
 * // 기본 호출
 * CompletableFuture<String> result = lambdaService.invoke("my-function", "{\"key\":\"value\"}");
 * 
 * // 고급 호출 (버전, 로그 포함)
 * LambdaInvocationRequest request = LambdaInvocationRequest.builder()
 *     .functionName("my-function")
 *     .qualifier("PROD")
 *     .payload("{\"key\":\"value\"}")
 *     .logType(LogType.TAIL)
 *     .correlationId("req-123")
 *     .build();
 * 
 * CompletableFuture<LambdaInvocationResponse> response = 
 *     lambdaService.invokeWithResponse(request);
 * 
 * // 배치 호출
 * LambdaBatchInvocationRequest batchRequest = LambdaBatchInvocationRequest.builder()
 *     .batchId("batch-001")
 *     .request(LambdaInvocationRequest.builder()
 *         .functionName("data-processor")
 *         .payload("{\"userId\":1}")
 *         .build())
 *     .request(LambdaInvocationRequest.builder()
 *         .functionName("data-processor")
 *         .payload("{\"userId\":2}")
 *         .build())
 *     .maxConcurrency(5)
 *     .build();
 * 
 * CompletableFuture<List<LambdaInvocationResponse>> batchResults = 
 *     lambdaService.invokeBatch(batchRequest);
 * 
 * // 함수 목록 조회
 * CompletableFuture<List<LambdaFunctionConfiguration>> functions = 
 *     lambdaService.listFunctions();
 * }
 * </pre>
 */
public interface LambdaService {

    /**
     * 동기 Lambda 함수 호출
     *
     * @param functionName Lambda 함수 이름 또는 ARN
     * @param payload JSON 페이로드
     * @return 함수 실행 결과
     * 
     * 동기 방식으로 Lambda 함수를 호출합니다.
     * InvocationType.REQUEST_RESPONSE 방식을 사용하여 함수 실행 결과를 기다립니다.
     * 
     * 특징:
     * - 함수 실행이 완료될 때까지 대기
     * - 함수의 반환값을 직접 받을 수 있음
     * - 실행 시간이 15분을 초과하면 타임아웃
     * - 에러 발생 시 예외 발생
     * 
     * 언제 사용하나:
     * - 함수의 결과값이 필요한 경우
     * - 순차적 처리가 필요한 경우
     * - 실시간 응답이 필요한 API에서
     * 
     * 주의사항:
     * - 장시간 실행되는 함수에는 부적합
     * - 대량의 병렬 처리시 성능 이슈 가능
     */
    CompletableFuture<String> invoke(String functionName, String payload);

    /**
     * 비동기 Lambda 함수 호출
     *
     * @param functionName Lambda 함수 이름 또는 ARN
     * @param payload JSON 페이로드
     * @return 요청 ID
     * 
     * 비동기 방식으로 Lambda 함수를 호출합니다.
     * InvocationType.EVENT 방식을 사용하여 즉시 요청 ID를 반환합니다.
     * 
     * 특징:
     * - 함수 실행을 기다리지 않고 즉시 반환
     * - 요청 ID만 반환 (함수 결과값 없음)
     * - 백그라운드에서 함수 실행
     * - 비용 효율적 (동시 실행 수 제한 없음)
     * 
     * 언제 사용하나:
     * - 결과값이 필요하지 않은 작업
     * - 이벤트 기반 처리
     * - 대량의 병렬 처리
     * - 파일 처리, 로그 분석 등
     * 
     * 주의사항:
     * - 함수 실행 결과를 확인할 수 없음
     * - 에러 처리가 복잡함 (CloudWatch 로그 확인 필요)
     * - 실행 순서 보장 없음
     */
    CompletableFuture<String> invokeAsync(String functionName, String payload);

    /**
     * 재시도를 포함한 Lambda 함수 호출
     *
     * @param functionName Lambda 함수 이름 또는 ARN
     * @param payload JSON 페이로드
     * @param maxRetries 최대 재시도 횟수
     * @return 함수 실행 결과
     * 
     * 재시도 로직이 포함된 동기 Lambda 함수 호출입니다.
     * 일시적인 오류 발생 시 자동으로 재시도를 수행합니다.
     * 
     * 재시도 대상 오류:
     * - 5xx 서버 오류 (AWS 내부 오류)
     * - 429 조절 오류 (Throttling)
     * - 네트워크 연결 오류
     * - 타임아웃 오류
     * 
     * 재시도 비대상 오류:
     * - 4xx 클라이언트 오류 (잘못된 요청, 권한 없음 등)
     * - 함수 내부 로직 오류
     * - 잘못된 페이로드 형식
     * 
     * 언제 사용하나:
     * - 중요한 트랜잭션 처리
     * - 일시적 장애에 민감한 작업
     * - 안정성이 중요한 시스템
     * 
     * 주의사항:
     * - 총 실행 시간이 길어질 수 있음
     * - 멱등성이 보장되는 함수에만 사용
     * - 재시도 횟수를 적절히 설정 (보통 3-5회)
     */
    CompletableFuture<String> invokeWithRetry(String functionName, String payload, int maxRetries);

    // ======================================================================================
    // 고급 호출 기능 (Advanced Invocation Features)
    // ======================================================================================

    /**
     * 상세한 응답 정보를 포함한 Lambda 함수 호출
     * 
     * @param request Lambda 호출 요청 (버전, 로그 타입, 클라이언트 컨텍스트 등 포함)
     * @return 상세한 호출 응답 (페이로드, 로그, 실행 시간, 상태 코드 등)
     * 
     * 기본 invoke() 메서드와 달리 다음과 같은 고급 기능을 제공합니다:
     * 
     * 1. 함수 버전/별칭 지정:
     *    - 특정 버전 호출 (1, 2, 3...)
     *    - 별칭 호출 (PROD, STAGING, DEV)
     *    - $LATEST 호출 (기본값)
     * 
     * 2. 실행 로그 포함:
     *    - LogType.TAIL: 마지막 4KB 로그 포함
     *    - LogType.NONE: 로그 제외 (기본값)
     * 
     * 3. 실시간 로그 스트리밍:
     *    - tail=true: 실시간 로그 스트리밍
     *    - tail=false: 실행 완료 후 로그 반환
     * 
     * 4. 클라이언트 컨텍스트:
     *    - 모바일 SDK용 클라이언트 정보 전달
     *    - 사용자 환경 설정 전달
     * 
     * 5. 상관관계 ID 추적:
     *    - 분산 시스템에서 요청 추적
     *    - 로그 연관성 분석
     * 
     * 사용 시나리오:
     * - 프로덕션 환경에서 특정 버전 호출
     * - 디버깅을 위한 실행 로그 필요
     * - 성능 분석을 위한 실행 메트릭 필요
     * - 분산 추적 시스템 연동
     * - 모바일 앱에서의 Lambda 호출
     */
    CompletableFuture<LambdaInvocationResponse> invokeWithResponse(LambdaInvocationRequest request);

    /**
     * 동일한 함수를 여러 페이로드로 동시 호출
     * 
     * @param functionName Lambda 함수 이름 또는 ARN
     * @param payloads 호출할 페이로드 목록
     * @return 각 페이로드에 대한 호출 결과 목록 (순서 보장)
     * 
     * 동일한 Lambda 함수를 다양한 입력 데이터로 동시에 호출합니다.
     * 내부적으로 최적화된 동시성 제어와 에러 처리를 제공합니다.
     * 
     * 특징:
     * - 동시성 제어: LambdaProperties.maxConcurrentInvocations 설정 적용
     * - 순서 보장: payloads 순서와 동일한 순서로 결과 반환
     * - 부분 실패 허용: 일부 호출이 실패해도 다른 호출은 계속 진행
     * - 에러 정보 포함: 실패한 호출의 에러 정보도 결과에 포함
     * 
     * 사용 시나리오:
     * - 배치 데이터 처리 (사용자별, 주문별, 파일별 등)
     * - 병렬 계산 작업 (이미지 처리, 데이터 분석 등)
     * - 다중 입력 검증 또는 변환
     * - A/B 테스트를 위한 다중 시나리오 실행
     * 
     * 예시:
     * List<String> payloads = Arrays.asList(
     *     "{\"userId\":1}", "{\"userId\":2}", "{\"userId\":3}"
     * );
     * CompletableFuture<List<String>> results = 
     *     lambdaService.invokeMultiple("user-processor", payloads);
     */
    CompletableFuture<List<LambdaInvocationResponse>> invokeMultiple(String functionName, List<String> payloads);

    /**
     * 배치 Lambda 함수 호출
     * 
     * @param batchRequest 배치 호출 요청 (다중 함수, 동시성 제어, 실패 정책 등)
     * @return 배치 호출 결과 목록
     * 
     * 여러 Lambda 함수를 효율적으로 일괄 호출하는 고급 기능입니다.
     * 단일 함수의 다중 호출뿐만 아니라 서로 다른 함수들의 조합 호출도 지원합니다.
     * 
     * 주요 기능:
     * 
     * 1. 유연한 배치 구성:
     *    - 동일 함수 + 다른 페이로드: 데이터 처리 배치
     *    - 다른 함수 + 다른 페이로드: 복합 작업 배치
     *    - 혼합 구성: 일부는 동일 함수, 일부는 다른 함수
     * 
     * 2. 동시성 제어:
     *    - maxConcurrency: 동시 실행 수 제한
     *    - 시스템 리소스 보호
     *    - 다운스트림 서비스 보호
     * 
     * 3. 실패 처리 정책:
     *    - failFast=true: 첫 실패시 남은 작업 취소
     *    - failFast=false: 모든 작업 완료까지 계속
     * 
     * 4. 재시도 정책:
     *    - NONE: 재시도 없음
     *    - INDIVIDUAL: 각 함수별 재시도 설정
     *    - BATCH_LEVEL: 배치 전체 재시도 정책
     * 
     * 5. 결과 집계:
     *    - ALL: 모든 결과 반환
     *    - SUCCESS_ONLY: 성공 결과만
     *    - FAILURE_ONLY: 실패 결과만
     *    - SUMMARY: 요약 정보만
     * 
     * 사용 시나리오:
     * - ETL 파이프라인: 데이터 추출, 변환, 적재
     * - 마이크로서비스 조합 호출: 사용자 정보 + 주문 이력 + 추천 상품
     * - 배치 처리 작업: 대용량 데이터의 병렬 처리
     * - 복합 비즈니스 로직: 여러 단계의 비즈니스 프로세스
     */
    CompletableFuture<List<LambdaInvocationResponse>> invokeBatch(LambdaBatchInvocationRequest batchRequest);

    // ======================================================================================
    // 함수 관리 기능 (Function Management Features)
    // ======================================================================================

    /**
     * 모든 Lambda 함수 목록 조회
     * 
     * @return Lambda 함수 설정 목록
     * 
     * 현재 AWS 계정과 리전의 모든 Lambda 함수 목록을 조회합니다.
     * 각 함수의 기본 설정 정보가 포함됩니다.
     * 
     * 포함되는 정보:
     * - 함수 이름, ARN, 설명
     * - 런타임, 핸들러 정보
     * - 메모리 크기, 타임아웃 설정
     * - 마지막 수정 시간
     * - 코드 크기 및 SHA256 해시
     * - VPC 설정 (있는 경우)
     * 
     * 사용 시나리오:
     * - 시스템 인벤토리 관리
     * - 함수 배포 상태 확인
     * - 모니터링 및 관리 도구
     * - 자동화 스크립트에서 함수 발견
     * - 비용 분석을 위한 함수 목록 수집
     * 
     * 주의사항:
     * - 많은 함수가 있을 경우 응답 시간이 길어질 수 있음
     * - IAM 권한 필요: lambda:ListFunctions
     * - 리전별로 조회됨 (다른 리전 함수는 포함되지 않음)
     */
    CompletableFuture<List<LambdaFunctionConfiguration>> listFunctions();

    /**
     * 특정 Lambda 함수의 설정 정보 조회
     * 
     * @param functionName 함수 이름 또는 ARN
     * @return Lambda 함수 설정 정보
     * 
     * 지정한 Lambda 함수의 상세 설정 정보를 조회합니다.
     * listFunctions()보다 더 상세한 정보를 제공합니다.
     * 
     * 포함되는 상세 정보:
     * - 환경 변수 목록
     * - VPC 설정 상세 (서브넷, 보안 그룹)
     * - 데드 레터 큐 설정
     * - KMS 암호화 키 정보
     * - X-Ray 추적 설정
     * - 레이어 정보
     * - 태그 정보
     * - 예약된 동시 실행 수
     * 
     * 사용 시나리오:
     * - 함수 설정 감사 및 검증
     * - 설정 백업 및 복원
     * - 함수간 설정 비교
     * - 문제 진단 및 디버깅
     * - 보안 검사 (VPC, 권한, 암호화)
     * 
     * 에러 케이스:
     * - 함수가 존재하지 않는 경우: ResourceNotFoundException
     * - 권한이 없는 경우: AccessDeniedException
     */
    CompletableFuture<LambdaFunctionConfiguration> getFunctionConfiguration(String functionName);

    /**
     * Lambda 함수 코드 업데이트
     * 
     * @param functionName 함수 이름 또는 ARN
     * @param zipFileBytes 새로운 함수 코드 (ZIP 파일 바이트)
     * @return 업데이트된 함수 설정 정보
     * 
     * 기존 Lambda 함수의 코드를 새로운 코드로 업데이트합니다.
     * 함수 설정은 변경하지 않고 코드만 교체합니다.
     * 
     * 코드 업데이트 과정:
     * 1. 새 코드 패키지 검증
     * 2. 코드 SHA256 해시 계산
     * 3. 함수 코드 교체
     * 4. 함수 상태 업데이트
     * 
     * 주의사항:
     * - 함수 실행 중일 때도 업데이트 가능 (새 요청부터 새 코드 사용)
     * - 코드 크기 제한: 50MB (직접 업로드), 250MB (S3 사용시)
     * - 업데이트 중에는 함수 상태가 "Pending"이 될 수 있음
     * - 원본 코드 백업 권장 (롤백용)
     * 
     * 권한 요구사항:
     * - lambda:UpdateFunctionCode
     * - 필요시 S3 접근 권한
     * 
     * 사용 시나리오:
     * - CI/CD 파이프라인에서 자동 배포
     * - 핫픽스 적용
     * - 코드 최적화 후 업데이트
     * - 라이브러리 의존성 업데이트
     */
    CompletableFuture<LambdaFunctionConfiguration> updateFunctionCode(String functionName, byte[] zipFileBytes);

    /**
     * 새로운 Lambda 함수 생성
     * 
     * @param functionName 생성할 함수 이름
     * @param runtime 함수 런타임 (예: "java21", "python3.12")
     * @param role 함수 실행 역할 ARN
     * @param handler 함수 핸들러 (예: "com.example.Handler::handleRequest")
     * @param zipFileBytes 함수 코드 (ZIP 파일 바이트)
     * @return 생성된 함수 설정 정보
     * 
     * 새로운 Lambda 함수를 생성합니다.
     * 기본 설정으로 함수를 생성하며, 이후 updateFunctionConfiguration()으로 세부 설정 변경 가능합니다.
     * 
     * 기본 설정 값:
     * - 메모리: 128MB
     * - 타임아웃: 3초
     * - 환경변수: 없음
     * - VPC: 없음
     * - 데드레터큐: 없음
     * 
     * 생성 과정:
     * 1. 함수 이름 중복 확인
     * 2. IAM 역할 존재 확인
     * 3. 코드 업로드 및 검증
     * 4. 함수 생성 및 초기화
     * 5. 준비 상태 확인
     * 
     * 권한 요구사항:
     * - lambda:CreateFunction
     * - iam:PassRole (실행 역할에 대한)
     * - 필요시 VPC 관련 권한
     * 
     * 사용 시나리오:
     * - 새로운 마이크로서비스 배포
     * - 인프라스트럭처 as 코드 (IaC)
     * - 템플릿 기반 함수 생성
     * - 자동화된 환경 구성
     * 
     * 에러 케이스:
     * - 함수 이름 중복: ResourceConflictException
     * - 잘못된 역할 ARN: InvalidParameterValueException
     * - 코드 크기 초과: CodeStorageExceededException
     */
    CompletableFuture<LambdaFunctionConfiguration> createFunction(
            String functionName, String runtime, String role, String handler, byte[] zipFileBytes);

    /**
     * Lambda 함수 삭제
     * 
     * @param functionName 삭제할 함수 이름 또는 ARN
     * @return 삭제 완료 여부 (true: 성공, false: 실패)
     * 
     * 지정한 Lambda 함수를 완전히 삭제합니다.
     * 함수 코드, 설정, 버전, 별칭이 모두 삭제됩니다.
     * 
     * 삭제 과정:
     * 1. 함수 존재 확인
     * 2. 실행 중인 인스턴스 확인
     * 3. 종속성 확인 (트리거, 별칭 등)
     * 4. 함수 및 관련 리소스 삭제
     * 
     * 주의사항:
     * - 삭제는 되돌릴 수 없음 (영구 삭제)
     * - 실행 중인 함수 인스턴스는 완료될 때까지 계속 실행
     * - CloudWatch 로그는 별도로 삭제해야 함
     * - 이벤트 소스 매핑은 자동 삭제됨
     * 
     * 삭제 전 확인사항:
     * - 다른 서비스에서 이 함수를 참조하고 있는지 확인
     * - 중요한 데이터나 로직이 포함되어 있는지 확인
     * - 백업이나 버전 관리가 필요한지 확인
     * 
     * 권한 요구사항:
     * - lambda:DeleteFunction
     * 
     * 사용 시나리오:
     * - 불필요한 함수 정리
     * - 리팩토링 후 구 버전 제거
     * - 개발/테스트 환경 정리
     * - 비용 절감을 위한 미사용 함수 제거
     * 
     * 반환값:
     * - true: 삭제 성공
     * - false: 삭제 실패 (함수 없음, 권한 없음 등)
     */
    CompletableFuture<Boolean> deleteFunction(String functionName);
}
