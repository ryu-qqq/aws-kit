# AWS SNS Client

Amazon Simple Notification Service (SNS)를 위한 Spring Boot 추상화 라이브러리입니다.

## 주요 기능

- **메시지 발행**: 토픽으로 메시지 발송 및 직접 전송
- **구독 관리**: 토픽 구독 및 구독 해제 자동화
- **필터링**: 메시지 속성 기반 구독 필터링
- **Spring Boot 자동 설정**: 간편한 설정과 의존성 주입
- **타입 안전성**: 강타입 인터페이스로 런타임 오류 방지
- **비동기 처리**: CompletableFuture를 통한 논블로킹 처리
- **배치 처리**: 여러 메시지 동시 발송

## 의존성 설정

### Gradle
```gradle
dependencies {
    implementation 'com.ryuqq.aws:aws-sns-client:1.0.2'
}
```

### Maven
```xml
<dependency>
    <groupId>com.ryuqq.aws</groupId>
    <artifactId>aws-sns-client</artifactId>
    <version>1.0.2</version>
</dependency>
```

## 설정

### application.yml
```yaml
aws:
  region: ap-northeast-2
  sns:
    enabled: true
    default-topic-arn: "arn:aws:sns:ap-northeast-2:123456789012:my-topic"
    timeout: PT30S
    retry:
      max-attempts: 3
      base-delay: PT1S
    delivery:
      message-retention-period: P14D
      max-receive-count: 5
    fifo:
      content-based-deduplication: true
      message-group-id: "default-group"
```

### Spring Boot 자동 설정
```java
@SpringBootApplication
@EnableAwsSns
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 사용법

### 기본 메시지 발행

#### 단순 메시지 발송
```java
@Service
public class NotificationService {

    private final SnsService snsService;

    public NotificationService(SnsService snsService) {
        this.snsService = snsService;
    }

    public CompletableFuture<String> sendNotification(String message) {
        return snsService.publishMessage("arn:aws:sns:ap-northeast-2:123456789012:notifications", message);
    }

    public CompletableFuture<String> sendWithSubject(String subject, String message) {
        return snsService.publishMessage(
            "arn:aws:sns:ap-northeast-2:123456789012:notifications",
            message,
            subject
        );
    }
}
```

#### 메시지 속성 포함
```java
public CompletableFuture<String> sendNotificationWithAttributes(String message) {
    Map<String, MessageAttributeValue> attributes = Map.of(
        "priority", MessageAttributeValue.builder()
            .dataType("String")
            .stringValue("high")
            .build(),
        "source", MessageAttributeValue.builder()
            .dataType("String")
            .stringValue("user-service")
            .build()
    );

    return snsService.publishMessage(
        "arn:aws:sns:ap-northeast-2:123456789012:notifications",
        message,
        "System Alert",
        attributes
    );
}
```

### 타입 안전 메시지 발행

```java
public record OrderNotification(
    String orderId,
    String customerId,
    BigDecimal amount,
    String status
) {}

@Service
public class OrderNotificationService {

    private final SnsService snsService;

    public CompletableFuture<String> notifyOrderCreated(OrderNotification order) {
        return snsService.publishJsonMessage(
            "arn:aws:sns:ap-northeast-2:123456789012:order-events",
            order,
            "New Order Created"
        );
    }
}
```

### 토픽 관리

#### 토픽 생성
```java
public CompletableFuture<String> createNotificationTopic() {
    return snsService.createTopic("user-notifications")
        .thenApply(response -> {
            log.info("Created topic: {}", response.topicArn());
            return response.topicArn();
        });
}

// FIFO 토픽 생성
public CompletableFuture<String> createFifoTopic() {
    Map<String, String> attributes = Map.of(
        "FifoTopic", "true",
        "ContentBasedDeduplication", "true"
    );

    return snsService.createTopicWithAttributes("order-events.fifo", attributes);
}
```

#### 토픽 설정 관리
```java
public CompletableFuture<Void> configureTopicRetention(String topicArn) {
    Map<String, String> attributes = Map.of(
        "MessageRetentionPeriod", "1209600", // 14일 (초 단위)
        "DisplayName", "사용자 알림 토픽"
    );

    return snsService.setTopicAttributes(topicArn, attributes);
}
```

### 구독 관리

#### 이메일 구독
```java
public CompletableFuture<String> subscribeEmail(String topicArn, String email) {
    return snsService.subscribe(topicArn, "email", email);
}

// SMS 구독
public CompletableFuture<String> subscribeSms(String topicArn, String phoneNumber) {
    return snsService.subscribe(topicArn, "sms", phoneNumber);
}

// SQS 구독
public CompletableFuture<String> subscribeSqs(String topicArn, String queueArn) {
    return snsService.subscribe(topicArn, "sqs", queueArn);
}
```

#### 필터 정책 구독
```java
public CompletableFuture<String> subscribeWithFilter(String topicArn, String queueArn) {
    Map<String, Object> filterPolicy = Map.of(
        "priority", List.of("high", "critical"),
        "source", "user-service"
    );

    return snsService.subscribeWithFilterPolicy(topicArn, "sqs", queueArn, filterPolicy);
}
```

### 배치 메시지 처리

```java
public CompletableFuture<List<String>> sendBulkNotifications(List<String> messages) {
    String topicArn = "arn:aws:sns:ap-northeast-2:123456789012:notifications";

    List<CompletableFuture<String>> futures = messages.stream()
        .map(message -> snsService.publishMessage(topicArn, message))
        .toList();

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
        .thenApply(v -> futures.stream()
            .map(CompletableFuture::join)
            .toList());
}
```

## 고급 사용법

### FIFO 토픽 처리

```java
@Service
public class FifoNotificationService {

    private final SnsService snsService;

    public CompletableFuture<String> sendOrderedMessage(
            String message,
            String messageGroupId,
            String deduplicationId) {

        return snsService.publishFifoMessage(
            "arn:aws:sns:ap-northeast-2:123456789012:order-events.fifo",
            message,
            messageGroupId,
            deduplicationId
        );
    }

    // 자동 중복 제거 ID 생성
    public CompletableFuture<String> sendOrderEvent(OrderEvent event) {
        String messageGroupId = "order-" + event.customerId();
        String deduplicationId = event.orderId() + "-" + event.timestamp();

        return snsService.publishJsonFifoMessage(
            "arn:aws:sns:ap-northeast-2:123456789012:order-events.fifo",
            event,
            messageGroupId,
            deduplicationId
        );
    }
}
```

### 에러 처리 및 재시도

```java
public CompletableFuture<String> sendReliableNotification(String message) {
    return snsService.publishMessage(topicArn, message)
        .exceptionally(throwable -> {
            if (throwable.getCause() instanceof ThrottledException) {
                log.warn("Rate limited, retrying after delay");
                return retryAfterDelay(message, Duration.ofSeconds(2));
            }
            throw new NotificationException("Failed to send notification", throwable);
        });
}

private CompletableFuture<String> retryAfterDelay(String message, Duration delay) {
    return CompletableFuture.delayedExecutor(delay.toMillis(), TimeUnit.MILLISECONDS)
        .execute(() -> snsService.publishMessage(topicArn, message));
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
  sns:
    health-check:
      enabled: true
      test-topic-arn: "arn:aws:sns:ap-northeast-2:123456789012:health-check"
```

#### 커스텀 메트릭
```java
@Component
public class SnsMetrics {

    private final MeterRegistry meterRegistry;

    @EventListener
    public void onMessagePublished(MessagePublishedEvent event) {
        meterRegistry.counter("aws.sns.messages.published",
            "topic", event.getTopicArn(),
            "status", event.isSuccess() ? "success" : "failure")
            .increment();

        meterRegistry.timer("aws.sns.publish.duration",
            "topic", event.getTopicArn())
            .record(event.getDuration(), TimeUnit.MILLISECONDS);
    }
}
```

### 메시지 템플릿

```java
@Component
public class NotificationTemplateService {

    private final SnsService snsService;

    public CompletableFuture<String> sendWelcomeNotification(String email, String userName) {
        String message = buildWelcomeMessage(userName);
        Map<String, MessageAttributeValue> attributes = Map.of(
            "template", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("welcome")
                .build(),
            "user_type", MessageAttributeValue.builder()
                .dataType("String")
                .stringValue("new_user")
                .build()
        );

        return snsService.publishMessage(
            "arn:aws:sns:ap-northeast-2:123456789012:user-notifications",
            message,
            "환영합니다!",
            attributes
        );
    }

    private String buildWelcomeMessage(String userName) {
        return String.format("""
            안녕하세요 %s님,

            저희 서비스에 가입해주셔서 감사합니다.
            다양한 기능을 체험해보세요!

            문의사항이 있으시면 언제든 연락주세요.
            """, userName);
    }
}
```

## 보안 고려사항

### 액세스 제어
```yaml
# IAM 정책 예시
aws:
  iam:
    policies:
      - effect: Allow
        actions:
          - sns:Publish
          - sns:Subscribe
          - sns:Unsubscribe
        resources:
          - "arn:aws:sns:*:*:notifications-*"
      - effect: Allow
        actions:
          - sns:CreateTopic
          - sns:DeleteTopic
          - sns:SetTopicAttributes
        resources:
          - "arn:aws:sns:*:*:*"
        condition:
          StringEquals:
            "aws:RequestedRegion": "ap-northeast-2"
```

### 메시지 암호화
```yaml
aws:
  sns:
    encryption:
      enabled: true
      kms-key-id: "arn:aws:kms:ap-northeast-2:123456789012:key/12345678-1234-1234-1234-123456789012"
      message-body-encryption: true
```

## 성능 최적화

### 연결 풀 설정
```yaml
aws:
  client-config:
    max-concurrency: 100
    connection-timeout: PT10S
    socket-timeout: PT30S
    use-http2: true

  sns:
    batch:
      max-batch-size: 10
      flush-interval: PT1S
      enable-batching: true
```

### 비동기 처리 최적화
```java
@Configuration
public class SnsConfiguration {

    @Bean
    public Executor snsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("sns-");
        executor.initialize();
        return executor;
    }
}
```

## 테스트

### LocalStack을 활용한 통합 테스트
```java
@SpringBootTest
@TestcontainersEnabled
class SnsIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack"))
            .withServices(SNS, SQS);

    @Test
    void shouldPublishAndReceiveMessage() {
        // 테스트 로직
    }
}
```

### 모의 테스트
```java
@ExtendWith(MockitoExtension.class)
class SnsServiceTest {

    @Mock
    private SnsAsyncClient snsClient;

    @Test
    void shouldPublishMessage() {
        // Mock 설정 및 테스트
    }
}
```

## 문제 해결

### 일반적인 오류

#### 1. 권한 부족
```
AuthorizationErrorException: User is not authorized to perform sns:Publish
```
**해결방법**: IAM 정책 확인 및 필요한 권한 추가

#### 2. 토픽 미존재
```
NotFoundException: Topic does not exist
```
**해결방법**: 토픽 ARN 확인 및 리전 설정 검증

#### 3. 메시지 크기 초과
```
InvalidParameterException: Invalid parameter: Message too long
```
**해결방법**: 메시지 크기를 256KB 이하로 제한 또는 S3 연동 고려

## 마이그레이션 가이드

### AWS SDK v1에서 마이그레이션

```java
// Before (AWS SDK v1)
AmazonSNS snsClient = AmazonSNSClientBuilder.defaultClient();
PublishRequest request = new PublishRequest(topicArn, message);
PublishResult result = snsClient.publish(request);

// After (AWS Kit)
CompletableFuture<String> messageId = snsService.publishMessage(topicArn, message);
```

## 라이선스

Apache License 2.0