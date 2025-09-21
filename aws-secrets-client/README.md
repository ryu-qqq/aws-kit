# AWS Secrets Client

AWS Secrets Manager와 Systems Manager Parameter Store를 위한 Spring Boot 추상화 라이브러리입니다.

## 주요 기능

- **AWS Secrets Manager**: 암호화된 시크릿 관리 및 자동 로테이션 지원
- **Systems Manager Parameter Store**: 설정 값 및 비밀 정보 중앙 관리
- **Spring Boot 자동 설정**: 간편한 설정과 의존성 주입
- **캐싱**: Caffeine을 활용한 고성능 메모리 캐싱
- **타입 안전성**: 강타입 인터페이스로 런타임 오류 방지
- **비동기 처리**: CompletableFuture를 통한 논블로킹 처리

## 의존성 설정

### Gradle
```gradle
dependencies {
    implementation 'com.ryuqq.aws:aws-secrets-client:1.0.2'
}
```

### Maven
```xml
<dependency>
    <groupId>com.ryuqq.aws</groupId>
    <artifactId>aws-secrets-client</artifactId>
    <version>1.0.2</version>
</dependency>
```

## 설정

### application.yml
```yaml
aws:
  region: ap-northeast-2
  secrets:
    cache:
      enabled: true
      max-size: 1000
      expire-after-write: PT1H
    manager:
      enabled: true
      timeout: PT30S
  parameter-store:
    enabled: true
    prefix: "/myapp/"
    cache:
      enabled: true
      expire-after-write: PT30M
```

### Spring Boot 자동 설정
```java
@SpringBootApplication
@EnableAwsSecretsManager
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 사용법

### Secrets Manager 사용

#### 기본 사용법
```java
@Service
public class UserService {

    private final SecretsManagerService secretsService;

    public UserService(SecretsManagerService secretsService) {
        this.secretsService = secretsService;
    }

    public CompletableFuture<String> getDatabasePassword() {
        return secretsService.getSecretValue("prod/database/password");
    }

    public CompletableFuture<DatabaseConfig> getDatabaseConfig() {
        return secretsService.getSecretAsJson("prod/database/config", DatabaseConfig.class);
    }
}
```

#### JSON 시크릿 파싱
```java
public record DatabaseConfig(
    String host,
    String port,
    String username,
    String password,
    String database
) {}

// 시크릿에서 JSON을 자동으로 파싱
CompletableFuture<DatabaseConfig> config = secretsService
    .getSecretAsJson("prod/database/config", DatabaseConfig.class);
```

#### 시크릿 생성 및 업데이트
```java
// 단순 문자열 시크릿
secretsService.createSecret("dev/api-key", "secret-api-key-value")
    .thenAccept(secretArn -> log.info("Created secret: {}", secretArn));

// JSON 시크릿 생성
DatabaseConfig config = new DatabaseConfig("localhost", "5432", "user", "pass", "mydb");
secretsService.createSecretAsJson("dev/database/config", config)
    .thenAccept(secretArn -> log.info("Created JSON secret: {}", secretArn));

// 시크릿 업데이트
secretsService.updateSecret("dev/api-key", "new-secret-value");
```

### Parameter Store 사용

#### 기본 사용법
```java
@Service
public class ConfigService {

    private final ParameterStoreService parameterService;

    public ConfigService(ParameterStoreService parameterService) {
        this.parameterService = parameterService;
    }

    public CompletableFuture<String> getApplicationProperty(String key) {
        return parameterService.getParameter("/myapp/config/" + key);
    }

    public CompletableFuture<List<Parameter>> getAllAppConfig() {
        return parameterService.getParametersByPath("/myapp/config/");
    }
}
```

#### 타입 변환
```java
// 자동 타입 변환 지원
CompletableFuture<Integer> maxConnections =
    parameterService.getParameterAsType("/myapp/database/max-connections", Integer.class);

CompletableFuture<Duration> timeout =
    parameterService.getParameterAsType("/myapp/api/timeout", Duration.class);
```

#### 파라미터 생성 및 수정
```java
// 일반 파라미터
parameterService.putParameter("/myapp/config/feature-flag", "true")
    .thenRun(() -> log.info("Parameter updated"));

// 보안 파라미터 (암호화)
parameterService.putSecureParameter("/myapp/secrets/api-key", "secure-value")
    .thenRun(() -> log.info("Secure parameter created"));
```

## 고급 사용법

### 캐싱 전략

#### 수동 캐시 제어
```java
@Service
public class CachedConfigService {

    private final SecretsManagerService secretsService;

    public CompletableFuture<String> getFrequentlyUsedSecret() {
        // 캐시 강제 새로고침
        return secretsService.getSecretValue("frequent-secret", true);
    }

    public void invalidateCache() {
        secretsService.clearCache();
    }
}
```

#### 캐시 설정 커스터마이징
```yaml
aws:
  secrets:
    cache:
      enabled: true
      max-size: 2000              # 최대 캐시 엔트리 수
      expire-after-write: PT2H    # 쓰기 후 만료 시간
      expire-after-access: PT1H   # 접근 후 만료 시간
      refresh-after-write: PT30M  # 백그라운드 새로고침
```

### 에러 처리

```java
public CompletableFuture<String> getSafeSecret(String secretName) {
    return secretsService.getSecretValue(secretName)
        .exceptionally(throwable -> {
            if (throwable.getCause() instanceof ResourceNotFoundException) {
                log.warn("Secret not found: {}", secretName);
                return "default-value";
            }
            throw new RuntimeException("Failed to retrieve secret", throwable);
        });
}
```

### 모니터링 및 메트릭

#### Spring Boot Actuator 연동
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always

aws:
  secrets:
    health-check:
      enabled: true
      test-secret: "health-check-secret"
```

#### 커스텀 메트릭
```java
@Component
public class SecretsMetrics {

    private final MeterRegistry meterRegistry;

    @EventListener
    public void onSecretAccess(SecretAccessEvent event) {
        meterRegistry.counter("aws.secrets.access",
            "secret", event.getSecretName(),
            "cache_hit", String.valueOf(event.isCacheHit()))
            .increment();
    }
}
```

## 보안 고려사항

### 액세스 제어
```yaml
# IAM 정책 예시 (최소 권한 원칙)
aws:
  iam:
    policies:
      - effect: Allow
        actions:
          - secretsmanager:GetSecretValue
          - secretsmanager:DescribeSecret
        resources:
          - "arn:aws:secretsmanager:*:*:secret:prod/*"
      - effect: Allow
        actions:
          - ssm:GetParameter
          - ssm:GetParameters
          - ssm:GetParametersByPath
        resources:
          - "arn:aws:ssm:*:*:parameter/myapp/*"
```

### 로깅 보안
```yaml
# 민감 정보 로깅 방지
logging:
  level:
    com.ryuqq.aws.secrets: INFO
    software.amazon.awssdk.request: WARN  # 요청 상세 내용 숨김
```

## 성능 최적화

### 배치 조회
```java
// 여러 파라미터 한 번에 조회
List<String> parameterNames = List.of(
    "/myapp/config/database-url",
    "/myapp/config/redis-url",
    "/myapp/config/queue-url"
);

CompletableFuture<Map<String, String>> configs =
    parameterService.getMultipleParameters(parameterNames);
```

### 연결 풀 최적화
```yaml
aws:
  client-config:
    max-concurrency: 50
    connection-timeout: PT10S
    socket-timeout: PT30S
    use-http2: true
```

## 테스트

### LocalStack을 활용한 통합 테스트
```java
@SpringBootTest
@TestcontainersEnabled
class SecretsManagerIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack"))
            .withServices(SECRETSMANAGER, SSM);

    @Test
    void shouldRetrieveSecret() {
        // 테스트 로직
    }
}
```

### 단위 테스트
```java
@ExtendWith(MockitoExtension.class)
class SecretsManagerServiceTest {

    @Mock
    private SecretsManagerAsyncClient secretsManagerClient;

    @Test
    void shouldGetSecretValue() {
        // Mock 설정 및 테스트
    }
}
```

## 문제 해결

### 일반적인 오류

#### 1. 권한 부족
```
AccessDeniedException: User is not authorized to perform secretsmanager:GetSecretValue
```
**해결방법**: IAM 정책 확인 및 필요한 권한 추가

#### 2. 시크릿 미존재
```
ResourceNotFoundException: Secrets Manager can't find the specified secret
```
**해결방법**: 시크릿 이름 확인 및 리전 설정 검증

#### 3. 캐시 관련 문제
```
CacheException: Failed to serialize cache entry
```
**해결방법**: 직렬화 가능한 객체 사용 또는 캐시 비활성화

### 디버깅 설정
```yaml
logging:
  level:
    com.ryuqq.aws.secrets: DEBUG
    software.amazon.awssdk.services.secretsmanager: DEBUG
```

## 마이그레이션 가이드

### 다른 라이브러리에서 마이그레이션

#### Spring Cloud AWS에서 마이그레이션
```java
// Before (Spring Cloud AWS)
@Value("#{awsParameterStorePropertySource['/myapp/config/database-url']}")
private String databaseUrl;

// After (AWS Kit)
@Autowired
private ParameterStoreService parameterService;

public CompletableFuture<String> getDatabaseUrl() {
    return parameterService.getParameter("/myapp/config/database-url");
}
```

## 라이선스

Apache License 2.0