# AWS Kit Architecture

AWS Kit은 **헥사고날 아키텍처(Hexagonal Architecture)** 패턴을 기반으로 한 Spring Boot AWS SDK 추상화 라이브러리입니다.

## 아키텍처 개요

### 설계 원칙

1. **타입 안전성 (Type Safety)**
   - AWS SDK의 원시 타입을 커스텀 타입으로 추상화
   - 컴파일 타임 오류 검출로 런타임 안정성 보장

2. **비동기 우선 (Async-First)**
   - 모든 작업이 `CompletableFuture<T>` 반환
   - Virtual Threads를 활용한 고성능 동시성

3. **Spring Boot 네이티브 통합**
   - 자동 설정(Auto-Configuration) 제공
   - Spring Boot 생태계와 완벽 호환

4. **모듈러 설계 (Modular Design)**
   - 필요한 AWS 서비스만 선택적 사용
   - 의존성 최소화 원칙

## 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐ │
│  │   Spring    │ │  Business   │ │    User Controllers     │ │
│  │   Service   │ │   Logic     │ │    & Components         │ │
│  └─────────────┘ └─────────────┘ └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    AWS Kit Layer                           │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐ │
│  │   S3 Kit    │ │  DynamoDB   │ │   SQS/SNS/Lambda Kit   │ │
│  │             │ │    Kit      │ │                         │ │
│  │ ┌─────────┐ │ │ ┌─────────┐ │ │ ┌─────────┐ ┌─────────┐ │ │
│  │ │Type Safe│ │ │ │Type Safe│ │ │ │Type Safe│ │Type Safe│ │ │
│  │ │Interface│ │ │ │Interface│ │ │ │Interface│ │Interface│ │ │
│  │ └─────────┘ │ │ └─────────┘ │ │ └─────────┘ └─────────┘ │ │
│  └─────────────┘ └─────────────┘ └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                   AWS SDK Commons                          │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────────┐ │
│  │   Regions   │ │ Credentials │ │   Client Configuration  │ │
│  │   Config    │ │   Provider  │ │     & Properties        │ │
│  └─────────────┘ └─────────────┘ └─────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    AWS SDK v2                              │
│                  (Amazon Services)                         │
└─────────────────────────────────────────────────────────────┘
```

## 모듈 구조

### 핵심 모듈

#### aws-sdk-commons
**역할**: 공통 설정, 자격증명, 리전 관리
- `AwsProperties`: 중앙화된 AWS 설정
- `CredentialsProvider`: 안전한 자격증명 관리
- `ClientConfiguration`: 공통 클라이언트 설정

```java
@ConfigurationProperties("aws")
public class AwsProperties {
    private String region = "ap-northeast-2";
    private ClientConfig clientConfig = new ClientConfig();
    private CredentialsConfig credentials = new CredentialsConfig();
}
```

#### 서비스별 모듈

```
aws-s3-client/          # S3 파일 저장소 서비스
├── types/              # S3 특화 타입 (S3Key, S3Metadata)
├── service/            # S3Service 인터페이스
└── impl/              # DefaultS3Service 구현

aws-dynamodb-client/    # NoSQL 데이터베이스 서비스
├── types/              # DynamoDB 특화 타입 (DynamoKey, TableName)
├── service/            # DynamoDBService 인터페이스
└── impl/              # DefaultDynamoDBService 구현

aws-sqs-client/         # 메시지 큐 서비스
├── types/              # SQS 특화 타입 (SqsMessage, QueueUrl)
├── service/            # SqsService 인터페이스
└── impl/              # DefaultSqsService 구현
```

## 타입 시스템 설계

### 타입 안전성 구현

```java
// Before: AWS SDK 원시 타입 (타입 안전하지 않음)
String queueUrl = "https://sqs.region.amazonaws.com/account/queue";
String messageBody = "raw message";

// After: AWS Kit 타입 안전 추상화
public record QueueUrl(String value) {
    public QueueUrl {
        Objects.requireNonNull(value, "Queue URL cannot be null");
        if (!value.startsWith("https://sqs.")) {
            throw new IllegalArgumentException("Invalid SQS queue URL");
        }
    }
}

public record SqsMessage(
    String body,
    Map<String, String> attributes,
    Duration visibilityTimeout
) {
    // 검증 로직과 불변성 보장
}
```

### 값 객체 패턴

```java
// S3 키 타입 안전성
public record S3Key(String value) {
    public S3Key {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("S3 key cannot be null or empty");
        }
        if (value.startsWith("/")) {
            throw new IllegalArgumentException("S3 key cannot start with '/'");
        }
    }
}

// DynamoDB 키 복합 타입
public record DynamoKey(String partitionKey, String sortKey) {
    public DynamoKey(String partitionKey) {
        this(partitionKey, null);
    }
}
```

## 비동기 처리 아키텍처

### CompletableFuture 기반 설계

```java
public interface S3Service {
    // 모든 메서드가 CompletableFuture 반환
    CompletableFuture<S3Object> getObject(String bucket, S3Key key);
    CompletableFuture<String> putObject(String bucket, S3Key key, byte[] data);
    CompletableFuture<Void> deleteObject(String bucket, S3Key key);
}
```

### Virtual Threads 활용

```java
@Configuration
public class AwsClientConfiguration {

    @Bean
    public Executor awsExecutor() {
        // Java 21 Virtual Threads 활용
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

### 에러 처리 전략

```java
public CompletableFuture<S3Object> getObjectSafely(String bucket, S3Key key) {
    return s3Service.getObject(bucket, key)
        .exceptionally(throwable -> {
            if (throwable.getCause() instanceof NoSuchKeyException) {
                log.warn("Object not found: {}/{}", bucket, key.value());
                return S3Object.empty();
            }
            throw new S3Exception("Failed to retrieve object", throwable);
        });
}
```

## Spring Boot 통합 패턴

### 자동 설정 구조

```java
@Configuration
@ConditionalOnClass(S3AsyncClient.class)
@EnableConfigurationProperties(AwsProperties.class)
public class S3AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public S3AsyncClient s3AsyncClient(AwsProperties awsProperties) {
        return S3AsyncClient.builder()
            .region(Region.of(awsProperties.getRegion()))
            .credentialsProvider(awsProperties.getCredentialsProvider())
            .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Service s3Service(S3AsyncClient s3AsyncClient) {
        return new DefaultS3Service(s3AsyncClient);
    }
}
```

### 설정 속성 바인딩

```yaml
aws:
  region: ap-northeast-2
  credentials:
    use-instance-profile: true
  client-config:
    connection-timeout: PT10S
    socket-timeout: PT30S
    max-concurrency: 50
  s3:
    bucket-prefix: "myapp-"
    presigned-url-expiry: PT1H
```

## 의존성 관리 전략

### BOM (Bill of Materials) 패턴

```gradle
// 루트 프로젝트 BOM 생성
allprojects {
    group = 'com.ryuqq.aws'
    version = '1.0.2'
}

// 하위 프로젝트에서 BOM 사용
dependencies {
    implementation platform('software.amazon.awssdk:bom:2.28.11')
    implementation 'software.amazon.awssdk:s3'
}
```

### 의존성 스코프 규칙

```gradle
dependencies {
    // API 노출: 사용자가 직접 사용하는 타입
    api project(':aws-sdk-commons')

    // 구현 세부사항: 내부 구현에만 사용
    implementation 'software.amazon.awssdk:s3'

    // 선택적 의존성: 사용자 환경에서 제공
    compileOnly 'org.springframework.boot:spring-boot-starter'
}
```

## 테스트 아키텍처

### 테스트 계층 구조

```
Unit Tests              Integration Tests         Contract Tests
    │                        │                        │
    ▼                        ▼                        ▼
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Service   │         │ LocalStack  │         │    AWS      │
│    Mock     │ ────────│ Container   │ ────────│  Services   │
│   Tests     │         │   Tests     │         │   Tests     │
└─────────────┘         └─────────────┘         └─────────────┘
```

### LocalStack 통합 테스트

```java
@SpringBootTest
@TestcontainersEnabled
class S3IntegrationTest {

    @Container
    static LocalStackContainer localstack =
        new LocalStackContainer(DockerImageName.parse("localstack/localstack"))
            .withServices(S3);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint", localstack::getEndpoint);
    }
}
```

## 성능 고려사항

### 연결 풀 최적화

```yaml
aws:
  client-config:
    max-concurrency: 100      # 동시 연결 수
    connection-timeout: PT10S  # 연결 타임아웃
    socket-timeout: PT30S      # 소켓 타임아웃
    use-http2: true           # HTTP/2 활성화
```

### 메모리 관리

```java
@Configuration
public class PerformanceConfiguration {

    @Bean
    public NettyNioAsyncHttpClient httpClient() {
        return NettyNioAsyncHttpClient.builder()
            .maxConcurrency(100)
            .maxPendingConnectionAcquires(10_000)
            .build();
    }
}
```

## 보안 아키텍처

### 자격증명 계층

```
┌─────────────────────────────────────────────────────────────┐
│                Credentials Provider Chain                   │
│                                                             │
│  1. Environment Variables  →  AWS_ACCESS_KEY_ID            │
│  2. System Properties     →  aws.accessKeyId               │
│  3. Profile Files         →  ~/.aws/credentials            │
│  4. Instance Profile      →  EC2 IAM Role                  │
│  5. Container Credentials →  ECS/Fargate IAM Role          │
└─────────────────────────────────────────────────────────────┘
```

### 암호화 및 보안

```java
@Configuration
@ConditionalOnProperty("aws.encryption.enabled")
public class EncryptionConfiguration {

    @Bean
    public KmsClient kmsClient() {
        return KmsClient.builder()
            .region(Region.of(awsProperties.getRegion()))
            .build();
    }
}
```

## 모니터링 및 관찰성

### 메트릭 수집

```java
@Component
public class AwsMetrics {

    private final MeterRegistry meterRegistry;

    @EventListener
    public void handleS3Operation(S3OperationEvent event) {
        meterRegistry.counter("aws.s3.operations",
            "operation", event.getOperation(),
            "bucket", event.getBucket(),
            "status", event.getStatus())
            .increment();
    }
}
```

### 헬스 체크

```java
@Component
public class S3HealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            s3Service.listBuckets().get(5, TimeUnit.SECONDS);
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

## 확장성 고려사항

### 새로운 AWS 서비스 추가

1. **새 모듈 생성**: `aws-{service}-client`
2. **타입 정의**: 서비스 특화 값 객체 생성
3. **서비스 인터페이스**: 비동기 메서드 정의
4. **자동 설정**: Spring Boot 통합 구성
5. **테스트**: LocalStack 통합 테스트 작성

### 버전 호환성 전략

```java
@Deprecated(since = "1.1.0", forRemoval = true)
public interface LegacyS3Service extends S3Service {
    // 하위 호환성을 위한 레거시 메서드
}
```

## 아키텍처 결정 기록 (ADR)

### ADR-001: 헥사고날 아키텍처 채택
- **상태**: 승인됨
- **결정**: AWS SDK를 헥사고날 아키텍처로 추상화
- **근거**: 테스트 용이성, 의존성 역전, 도메인 중심 설계

### ADR-002: 비동기 우선 설계
- **상태**: 승인됨
- **결정**: 모든 I/O 작업을 CompletableFuture로 래핑
- **근거**: 확장성, 논블로킹 I/O, Virtual Threads 활용

### ADR-003: 타입 안전 추상화
- **상태**: 승인됨
- **결정**: AWS SDK 원시 타입을 커스텀 타입으로 래핑
- **근거**: 컴파일 타임 안전성, 개발자 경험 향상

이 아키텍처는 **안전성, 성능, 확장성**을 모두 고려하여 설계되었으며, Spring Boot 생태계와의 완벽한 통합을 제공합니다.