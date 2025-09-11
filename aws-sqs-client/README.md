# AWS SQS Client

비동기 작업, 배치 처리, 포괄적인 오류 처리를 제공하는 간편한 Amazon Simple Queue Service (SQS) 통합을 위한 Spring Boot 자동 구성 모듈입니다.

## 기능

- **비동기 작업**: CompletableFuture를 사용한 논블로킹 메시지 작업
- **배치 처리**: 단일 작업으로 최대 10개의 메시지 전송 및 삭제
- **롱 폴링**: 효율적인 메시지 검색을 위한 구성 가능한 롱 폴링
- **자동 구성**: 합리적인 기본값을 가진 Spring Boot 자동 구성
- **오류 처리**: 상세한 로깅을 포함한 포괄적인 예외 처리
- **메시지 관리**: 완전한 생명주기 관리 (전송, 수신, 삭제)
- **큐 관리**: 큐 생성 및 URL 확인
- **가시성 타임아웃**: 구성 가능한 메시지 가시성 설정

## 빠른 시작

### 1. 의존성 추가

```gradle
dependencies {
    implementation 'com.ryuqq:aws-sqs-client:1.0.0'
}
```

### 2. 구성

```yaml
# application.yml
aws:
  region: us-east-1
  credentials:
    access-key: your-access-key
    secret-key: your-secret-key
  sqs:
    long-polling-wait-seconds: 20      # 0-20 seconds
    max-batch-size: 10                 # 1-10 messages
    visibility-timeout: 30             # seconds
```

### 3. 기본 사용법

```java
@Service
@RequiredArgsConstructor
public class MessageService {
    
    private final SqsService sqsService;
    
    public void sendMessage(String queueUrl, String message) {
        sqsService.sendMessage(queueUrl, message)
            .thenAccept(messageId -> 
                log.info("Message sent with ID: {}", messageId))
            .exceptionally(throwable -> {
                log.error("Failed to send message", throwable);
                return null;
            });
    }
    
    public void processMessages(String queueUrl) {
        sqsService.receiveMessages(queueUrl, 10)
            .thenAccept(messages -> {
                for (Message message : messages) {
                    // Process message
                    processMessage(message);
                    
                    // Delete after processing
                    sqsService.deleteMessage(queueUrl, message.receiptHandle());
                }
            });
    }
}
```

## 사용 예제

### 메시지 전송

#### 단일 메시지
```java
CompletableFuture<String> messageId = sqsService.sendMessage(
    "https://sqs.us-east-1.amazonaws.com/123456789012/my-queue",
    "Hello, SQS!"
);
```

#### 배치 메시지
```java
List<String> messages = Arrays.asList(
    "Message 1",
    "Message 2", 
    "Message 3"
);

CompletableFuture<List<String>> messageIds = sqsService.sendMessageBatch(
    queueUrl, 
    messages
);
```

### 메시지 수신

#### 기본 수신
```java
CompletableFuture<List<Message>> messages = sqsService.receiveMessages(
    queueUrl, 
    5  // max messages
);

messages.thenAccept(messageList -> {
    messageList.forEach(message -> {
        System.out.println("Message ID: " + message.messageId());
        System.out.println("Body: " + message.body());
        System.out.println("Receipt Handle: " + message.receiptHandle());
    });
});
```

#### 메시지 처리 패턴
```java
@Component
public class MessageProcessor {
    
    private final SqsService sqsService;
    
    @Scheduled(fixedDelay = 5000)  // 5초마다 폴링
    public void pollMessages() {
        String queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/my-queue";
        
        sqsService.receiveMessages(queueUrl, 10)
            .thenCompose(messages -> {
                List<CompletableFuture<Void>> processedMessages = messages.stream()
                    .map(this::processMessage)
                    .collect(Collectors.toList());
                
                return CompletableFuture.allOf(
                    processedMessages.toArray(new CompletableFuture[0])
                );
            })
            .exceptionally(throwable -> {
                log.error("Error processing messages", throwable);
                return null;
            });
    }
    
    private CompletableFuture<Void> processMessage(Message message) {
        return CompletableFuture.runAsync(() -> {
            try {
                // 메시지 처리 로직
                handleMessage(message.body());
                
                // 성공적인 처리 후 메시지 삭제
                sqsService.deleteMessage(queueUrl, message.receiptHandle()).join();
                
            } catch (Exception e) {
                log.error("Failed to process message: {}", message.messageId(), e);
                // 메시지는 가시성 타임아웃 후 큐로 다시 돌아갑니다
            }
        });
    }
}
```

### 메시지 삭제

#### 단일 메시지 삭제
```java
sqsService.deleteMessage(queueUrl, receiptHandle)
    .thenRun(() -> log.info("Message deleted successfully"));
```

#### 배치 삭제
```java
List<String> receiptHandles = Arrays.asList(
    "receipt-handle-1",
    "receipt-handle-2",
    "receipt-handle-3"
);

sqsService.deleteMessageBatch(queueUrl, receiptHandles)
    .thenRun(() -> log.info("Batch delete completed"));
```

### 큐 관리

#### 큐 생성
```java
Map<String, String> attributes = Map.of(
    "VisibilityTimeout", "30",
    "MessageRetentionPeriod", "1209600",  // 14 days
    "ReceiveMessageWaitTimeSeconds", "20"
);

CompletableFuture<String> queueUrl = sqsService.createQueue("my-new-queue", attributes);
```

#### 큐 URL 가져오기
```java
CompletableFuture<String> queueUrl = sqsService.getQueueUrl("existing-queue");
```

## 구성 속성

| 속성 | 기본값 | 설명 |
|----------|---------|-------------|
| `aws.sqs.long-polling-wait-seconds` | 20 | 롱 폴링 대기 시간 (0-20초) |
| `aws.sqs.max-batch-size` | 10 | 작업을 위한 최대 배치 크기 (1-10) |
| `aws.sqs.visibility-timeout` | 30 | 메시지 가시성 타임아웃 (초) |

### Complete Configuration Example

```yaml
aws:
  region: us-east-1
  credentials:
    access-key: ${AWS_ACCESS_KEY_ID}
    secret-key: ${AWS_SECRET_ACCESS_KEY}
  
  sqs:
    # Long polling reduces empty responses and costs
    long-polling-wait-seconds: 20
    
    # Maximum messages per batch operation
    max-batch-size: 10
    
    # Time before message becomes visible again if not deleted
    visibility-timeout: 30

# Optional: Enable SQS client logging
logging:
  level:
    com.ryuqq.aws.sqs: DEBUG
    software.amazon.awssdk.services.sqs: DEBUG
```

## Error Handling

### Exception Handling Pattern
```java
@Service
public class RobustMessageService {
    
    private final SqsService sqsService;
    
    public void sendMessageWithRetry(String queueUrl, String message) {
        sqsService.sendMessage(queueUrl, message)
            .handle((messageId, throwable) -> {
                if (throwable != null) {
                    log.error("Send failed, implementing retry logic", throwable);
                    
                    // Implement retry logic
                    return retryWithBackoff(queueUrl, message, 3);
                } else {
                    log.info("Message sent successfully: {}", messageId);
                    return CompletableFuture.completedFuture(messageId);
                }
            });
    }
    
    private CompletableFuture<String> retryWithBackoff(String queueUrl, String message, int attempts) {
        if (attempts <= 0) {
            return CompletableFuture.failedFuture(
                new RuntimeException("Max retry attempts exceeded")
            );
        }
        
        return CompletableFuture.delayedExecutor(
            Duration.ofSeconds(2).toMillis(), 
            TimeUnit.MILLISECONDS
        ).execute(() -> {
            sqsService.sendMessage(queueUrl, message)
                .exceptionally(throwable -> {
                    return retryWithBackoff(queueUrl, message, attempts - 1).join();
                });
        });
    }
}
```

## Dead Letter Queue (DLQ) Support

Configure DLQ for failed message handling:

```yaml
# Queue attributes for DLQ configuration
queue-attributes:
  # Main queue with DLQ
  my-queue:
    VisibilityTimeout: "30"
    MessageRetentionPeriod: "1209600"  # 14 days
    RedrivePolicy: |
      {
        "deadLetterTargetArn": "arn:aws:sqs:us-east-1:123456789012:my-queue-dlq",
        "maxReceiveCount": 3
      }
  
  # Dead letter queue
  my-queue-dlq:
    MessageRetentionPeriod: "1209600"  # 14 days
```

```java
@Component
public class DlqMessageProcessor {
    
    @EventListener
    @Async
    public void handleFailedMessage(MessageProcessingFailedEvent event) {
        String dlqUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/my-queue-dlq";
        
        // Process DLQ messages for analysis
        sqsService.receiveMessages(dlqUrl, 10)
            .thenAccept(messages -> {
                messages.forEach(this::analyzeFailedMessage);
            });
    }
    
    private void analyzeFailedMessage(Message message) {
        log.warn("Failed message analysis: ID={}, Body={}", 
            message.messageId(), message.body());
        
        // Implement failure analysis logic
        // Decide whether to retry, discard, or escalate
    }
}
```

## Best Practices

### 1. Message Processing
- Always delete messages after successful processing
- Implement idempotent message processing
- Use appropriate visibility timeout based on processing time
- Handle partial batch failures gracefully

```java
@Service
public class IdempotentMessageProcessor {
    
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();
    
    public CompletableFuture<Void> processMessage(Message message) {
        String messageId = message.messageId();
        
        if (processedMessageIds.contains(messageId)) {
            log.info("Message already processed: {}", messageId);
            return sqsService.deleteMessage(queueUrl, message.receiptHandle());
        }
        
        return CompletableFuture.runAsync(() -> {
            try {
                // Process message
                doProcessMessage(message.body());
                
                // Mark as processed
                processedMessageIds.add(messageId);
                
                // Delete from queue
                sqsService.deleteMessage(queueUrl, message.receiptHandle()).join();
                
            } catch (Exception e) {
                log.error("Processing failed for message: {}", messageId, e);
                throw new RuntimeException(e);
            }
        });
    }
}
```

### 2. Batch Operations
- Use batch operations for better throughput
- Handle partial batch failures
- Stay within batch size limits (max 10)

```java
public CompletableFuture<Void> processBatchEfficiently(List<Message> messages) {
    List<CompletableFuture<String>> processedMessages = new ArrayList<>();
    List<String> receiptHandles = new ArrayList<>();
    
    for (Message message : messages) {
        CompletableFuture<String> processed = processMessageAsync(message)
            .thenApply(result -> {
                receiptHandles.add(message.receiptHandle());
                return result;
            });
        processedMessages.add(processed);
    }
    
    return CompletableFuture.allOf(
        processedMessages.toArray(new CompletableFuture[0])
    ).thenCompose(v -> {
        // Batch delete all successfully processed messages
        return sqsService.deleteMessageBatch(queueUrl, receiptHandles);
    });
}
```

### 3. Resource Management
- Use connection pooling for high-throughput applications
- Monitor queue depths and processing rates
- Implement circuit breaker pattern for resilience

### 4. Security
- Use IAM roles instead of hardcoded credentials
- Implement least privilege access
- Enable SQS server-side encryption

```yaml
aws:
  credentials:
    # Prefer IAM roles over access keys
    use-instance-profile: true
  sqs:
    # Enable server-side encryption
    default-queue-attributes:
      KmsMasterKeyId: "arn:aws:kms:us-east-1:123456789012:key/12345678-1234-1234-1234-123456789012"
      SqsManagedSseEnabled: "true"
```

## 테스트

모듈은 LocalStack 통합을 통한 포괄적인 테스트 커버리지를 포함합니다:

```java
@TestConfiguration
@EnableConfigurationProperties(SqsProperties.class)
public class SqsTestConfiguration {
    
    @Bean
    @Primary
    public SqsAsyncClient testSqsClient() {
        return SqsAsyncClient.builder()
            .endpointOverride(URI.create("http://localhost:4566"))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .build();
    }
}
```

## 마이그레이션 가이드

### AWS SDK v1.x에서
- `AmazonSQS`를 `SqsService`로 교체
- async/CompletableFuture 패턴으로 업데이트
- 배치 작업을 새로운 배치 메소드로 마이그레이션

### Spring Cloud AWS에서
- `@SqsListener`를 폴링 메커니즘으로 교체
- 구성 속성 형식 업데이트
- 비동기 메시지 처리로 마이그레이션

## 문제 해결

### 일반적인 문제

1. **메시지 수신 안됨**: 가시성 타임아웃 및 롱 폴링 설정 확인
2. **배치 크기 초과**: 배치 작업이 10개 메시지 제한 내에 있는지 확인
3. **인증 오류**: AWS 자격 증명 및 IAM 권한 확인
4. **연결 타임아웃**: 네트워크 연결 및 엔드포인트 구성 확인

### 모니터링

문제 해결을 위한 상세 로깅 활성화:

```yaml
logging:
  level:
    com.ryuqq.aws.sqs: DEBUG
    software.amazon.awssdk: DEBUG
    org.apache.http: DEBUG
```

## 의존성

- Spring Boot 3.x+
- AWS SDK for Java 2.x
- Java 17+
- aws-sdk-commons (내부 의존성)

## 라이선스

이 모듈은 awskit 프로젝트의 일부입니다. 라이선스 정보는 메인 프로젝트를 참조하세요.