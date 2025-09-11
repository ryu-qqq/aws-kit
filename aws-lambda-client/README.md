# AWS Lambda Client

자동 구성, 재시도 메커니즘, 오류 처리를 제공하는 AWS Lambda 함수 호출을 위한 간편한 Spring Boot 라이브러리입니다.

## 개요

AWS Lambda Client는 Spring Boot 애플리케이션에서 AWS Lambda 함수를 호출하기 위한 간소화된 인터페이스를 제공합니다. 내장된 타임아웃 처리, 지수 백오프 재시도 전략, 포괄적인 오류 관리와 함께 동기 및 비동기 호출 패턴을 모두 지원합니다.

## 주요 기능

- **간편한 Lambda 호출**: Lambda 함수 호출을 위한 사용하기 쉬운 서비스 인터페이스
- **동기 및 비동기 실행**: REQUEST_RESPONSE 및 EVENT 호출 유형 모두 지원
- **자동 재시도 로직**: 탄력적인 호출을 위한 지터가 포함된 내장 지수 백오프
- **Spring Boot 자동 구성**: 합리적인 기본값을 가진 제로 구성 설정
- **타임아웃 관리**: 함수 호출을 위한 구성 가능한 타임아웃 설정
- **오류 처리**: 상세한 로깅을 포함한 포괄적인 예외 처리
- **동시 실행 제어**: 동시 호출을 위한 구성 가능한 제한

## 의존성

이 모듈은 다음에 의존합니다:
- `aws-sdk-commons` (이 프로젝트에서)
- AWS SDK for Java v2 (Lambda)
- Spring Boot Starter

## 설치

`build.gradle`에 의존성을 추가하세요:

```gradle
implementation project(':aws-lambda-client')
```

또는 독립 의존성으로 사용하는 경우:

```gradle
implementation 'com.ryuqq:aws-lambda-client:1.0.0'
```

## 구성

### 애플리케이션 속성

`application.yml`에서 Lambda 클라이언트를 구성하세요:

```yaml
aws:
  lambda:
    timeout: PT15M              # Function invocation timeout (default: 15 minutes)
    max-retries: 3              # Maximum retry attempts (default: 3)
    max-concurrent-invocations: 10  # Maximum concurrent invocations (default: 10)
```

### 자동 구성

라이브러리가 자동으로 구성하는 것들:
- `LambdaAsyncClient` - AWS SDK Lambda 클라이언트
- `LambdaService` - 고수준 서비스 인터페이스

이러한 컴포넌트들은 다음을 포함하여 `aws-sdk-commons` 모듈로부터 AWS 구성을 상속받습니다:
- 리전 구성
- 자격 증명 제공자
- HTTP 클라이언트 설정
- 재시도 정책

## 사용 예제

### 기본 Lambda 호출

```java
@Service
public class MyService {
    
    private final LambdaService lambdaService;
    
    public MyService(LambdaService lambdaService) {
        this.lambdaService = lambdaService;
    }
    
    public String processData(String data) {
        String payload = "{\"input\":\"" + data + "\"}";
        
        return lambdaService.invoke("my-lambda-function", payload)
                .join(); // 완료까지 대기
    }
}
```

### 비동기 호출

```java
@Service
public class AsyncProcessor {
    
    private final LambdaService lambdaService;
    
    public String triggerAsync(String data) {
        String payload = "{\"data\":\"" + data + "\"}";
        
        // 즉시 요청 ID 반환
        return lambdaService.invokeAsync("background-processor", payload)
                .join();
    }
}
```

### 사용자 정의 재시도를 통한 호출

```java
@Service
public class ReliableProcessor {
    
    private final LambdaService lambdaService;
    
    public String processWithRetry(String data) {
        String payload = "{\"input\":\"" + data + "\"}";
        
        // 사용자 정의 재시도 횟수 (기본값 재정의)
        return lambdaService.invokeWithRetry("critical-function", payload, 5)
                .join();
    }
}
```

### 콜백을 사용한 비동기 처리

```java
@Service
public class CallbackProcessor {
    
    private final LambdaService lambdaService;
    
    public void processAsync(String data) {
        String payload = "{\"input\":\"" + data + "\"}";
        
        lambdaService.invoke("data-processor", payload)
                .thenAccept(result -> {
                    log.info("Processing completed: {}", result);
                })
                .exceptionally(throwable -> {
                    log.error("Processing failed", throwable);
                    return null;
                });
    }
}
```

### 오류 처리

```java
@Service
public class SafeProcessor {
    
    private final LambdaService lambdaService;
    
    public Optional<String> safeLambdaCall(String functionName, String payload) {
        try {
            CompletableFuture<String> future = lambdaService.invoke(functionName, payload);
            String result = future.get(30, TimeUnit.SECONDS);
            return Optional.of(result);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof AwsServiceException) {
                AwsServiceException awsException = (AwsServiceException) e.getCause();
                log.error("AWS error: {} (status: {})", 
                         awsException.getMessage(), awsException.statusCode());
            }
            return Optional.empty();
        } catch (TimeoutException e) {
            log.error("Lambda invocation timed out for function: {}", functionName);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }
}
```

## Spring Boot 통합

### 자동 구성

모듈이 자동으로 등록하는 빈들:

```java
@Configuration
public class LambdaConfiguration {
    
    @Bean
    public LambdaAsyncClient lambdaAsyncClient() {
        // aws-sdk-commons 설정으로 구성됨
    }
    
    @Bean
    public LambdaService lambdaService() {
        // 사용 준비가 완료된 서비스 구현
    }
}
```

### 사용자 정의 구성

필요한 경우 기본 구성 재정의:

```java
@Configuration
public class CustomLambdaConfig {
    
    @Bean
    @Primary
    public LambdaService customLambdaService(LambdaAsyncClient client) {
        LambdaProperties customProperties = new LambdaProperties();
        customProperties.setTimeout(Duration.ofMinutes(30));
        customProperties.setMaxRetries(5);
        
        return new DefaultLambdaService(client, customProperties);
    }
}
```

## 오류 처리 및 재시도 전략

### 자동 재시도 로직

서비스가 자동으로 재시도하는 경우:
- **HTTP 5xx 오류** (서버 오류)
- **HTTP 429** (속도 제한)
- **HTTP 408** (요청 타임아웃)
- **TimeoutException** (클라이언트 측 타임아웃)

재시도하지 않는 오류:
- **HTTP 4xx 오류** (408, 429 제외)
- Lambda에서 반환된 함수 오류
- 잘못된 함수 이름 또는 페이로드

### 지수 백오프

재시도 지연은 지수 백오프 패턴을 따릅니다:
- 기본 지연: 100ms
- 공식: `100ms * 2^attempt`
- 최대 지연: 30초
- 지터: ±10% (동시 요청 방지)

```java
// 재시도 지연 예시:
// 시도 1: ~100ms (지터 포함 90-110ms)
// 시도 2: ~200ms (지터 포함 180-220ms)
// 시도 3: ~400ms (지터 포함 360-440ms)
// 시도 4: ~800ms (지터 포함 720-880ms)
```

### 예외 유형

```java
// 함수 실행 오류
RuntimeException: "Lambda function error: {errorDetails}"

// AWS 서비스 오류  
AwsServiceException: 원본 AWS SDK 예외

// 타임아웃 오류
TimeoutException: 호출이 구성된 타임아웃을 초과할 때

// 완료 오류
CompletionException: 기본 비동기 실행 오류를 래핑
```

## 모범 사례

### 1. 함수 명명

Lambda 함수에 대해 일관된 명명 패턴 사용:

```java
// 좋음: 환경별 명명
String functionName = environment + "-" + serviceName + "-" + operation;
// 예시: "prod-user-service-validate"

// 좋음: 계정 간 액세스를 위한 ARN
String functionArn = "arn:aws:lambda:us-east-1:123456789012:function:my-function";
```

### 2. 페이로드 관리

페이로드를 일관되게 구조화:

```java
// 좋음: 메타데이터가 포함된 구조화된 JSON
String payload = """
    {
        "data": %s,
        "metadata": {
            "requestId": "%s",
            "timestamp": "%s",
            "version": "1.0"
        }
    }
    """.formatted(dataJson, requestId, timestamp);
```

### 3. 타임아웃 구성

함수 특성에 따라 타임아웃 구성:

```yaml
aws:
  lambda:
    timeout: PT5M   # 빠른 함수용 (5분)
    # timeout: PT15M  # 장시간 실행 함수용 (15분)
```

### 4. 비동기 vs 동기 호출

적절한 호출 유형 선택:

```java
// 발사 후 잊어버리기 작업에는 비동기 사용
lambdaService.invokeAsync("notification-sender", payload);

// 결과가 필요한 작업에는 동기 사용
String result = lambdaService.invoke("data-validator", payload).join();
```

### 5. 리소스 관리

동시 호출 모니터링:

```yaml
aws:
  lambda:
    max-concurrent-invocations: 10  # Lambda 동시성 제한에 따라 조정
```

### 6. 오류 복구

우아한 오류 처리 구현:

```java
public Optional<String> processWithFallback(String data) {
    try {
        return Optional.of(lambdaService.invoke("primary-processor", data).join());
    } catch (Exception e) {
        log.warn("Primary processor failed, using fallback", e);
        return fallbackProcessor.process(data);
    }
}
```

### 7. 모니터링 및 로깅

서비스는 DEBUG 레벨에서 상세한 로깅을 제공합니다:

```yaml
logging:
  level:
    com.ryuqq.aws.lambda: DEBUG  # 상세한 Lambda 호출 로그 활성화
```

### 8. 테스트

제공된 테스트 유틸리티 사용:

```java
@SpringBootTest
class LambdaIntegrationTest {
    
    @Autowired
    private LambdaService lambdaService;
    
    @Test
    void testLambdaInvocation() {
        // LocalStack 또는 AWS와의 통합 테스트
    }
}
```

## 성능 고려사항

- **연결 풀링**: `LambdaAsyncClient` 인스턴스 재사용
- **타임아웃 튜닝**: 사용 사례에 적절한 타임아웃 설정
- **재시도 전략**: 재시도 구성으로 신뢰성 vs 지연시간 균형
- **동시 제한**: 동시 호출 제한 모니터링 및 조정
- **페이로드 크기**: 동기 호출의 경우 페이로드를 6MB 미만으로 유지

## 보안 고려사항

- **IAM 권한**: 적절한 `lambda:InvokeFunction` 권한 확보
- **VPC 구성**: 함수가 프라이빗 리소스에 액세스하는 경우 VPC 설정 구성
- **암호화**: 민감한 데이터 처리 시 페이로드 암호화를 위해 AWS KMS 사용
- **계정 간 액세스**: 계정 간 호출을 위해 리소스 기반 정책 사용

## 문제 해결

### 일반적인 문제

1. **함수를 찾을 수 없음 (404)**
   - 함수 이름/ARN 확인
   - 리전 구성 확인
   - 대상 리전에 함수가 존재하는지 확인

2. **액세스 거부됨 (403)**
   - IAM 권한 확인
   - 리소스 기반 정책 확인
   - 자격 증명이 올바르게 구성되었는지 확인

3. **속도 제한 (429)**
   - 동시 호출 감소
   - 지수 백오프 구현 (자동)
   - Lambda 동시성 제한 확인

4. **타임아웃 오류**
   - 타임아웃 구성 증가
   - Lambda 함수 성능 최적화
   - 장시간 실행 작업에 대해 비동기 호출 고려

### 디버그 로깅

상세한 정보를 위한 디버그 로깅 활성화:

```yaml
logging:
  level:
    com.ryuqq.aws.lambda: DEBUG
    software.amazon.awssdk.services.lambda: DEBUG
```

이것은 다음을 로그합니다:
- 함수 호출 세부사항
- 요청/응답 페이로드
- 재시도 시도 및 백오프 지연
- 오류 세부사항 및 스택 트레이스