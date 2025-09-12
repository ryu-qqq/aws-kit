# AWS Kit SQS Consumer

Spring Boot 어노테이션 기반 SQS 메시지 소비 모듈입니다.

## 특징

- **@SqsListener 어노테이션**: 선언적 SQS 메시지 소비
- **타입 추상화**: AWS SDK 의존성 숨김, 사용자 친화적 API
- **비동기 처리**: CompletableFuture 기반 논블로킹 처리
- **배치 처리**: 다중 메시지 배치 처리 지원
- **자동 재시도**: 실패 시 자동 재시도 메커니즘
- **Dead Letter Queue**: 실패한 메시지 DLQ 처리
- **Spring Boot 통합**: 자동 설정 및 라이프사이클 관리

## 의존성

```gradle
dependencies {
    implementation 'com.github.ryuqq.awskit:aws-sqs-consumer:1.0.0'
}
```

## 사용법

### 1. 기본 메시지 리스너

```java
@Service
public class OrderService {
    
    @SqsListener(queueName = "order-events")
    public void handleOrderEvent(SqsMessage message) {
        String orderData = message.getBody();
        // 주문 이벤트 처리
        processOrder(orderData);
    }
}
```

### 2. 배치 메시지 처리

```java
@Service
public class BatchOrderService {
    
    @SqsListener(
        queueName = "batch-orders", 
        batchMode = true,
        batchSize = 10,
        maxConcurrentMessages = 5
    )
    public void handleBatchOrders(List<SqsMessage> messages) {
        List<Order> orders = messages.stream()
            .map(msg -> parseOrder(msg.getBody()))
            .toList();
            
        processBatchOrders(orders);
    }
}
```

### 3. 고급 설정 예제

```java
@Service
public class PaymentService {
    
    @SqsListener(
        queueName = "${app.payment.queue.name}",
        id = "payment-processor",
        maxConcurrentMessages = 3,
        pollTimeoutSeconds = 20,
        messageVisibilitySeconds = 60,
        maxRetryAttempts = 5,
        retryDelayMillis = 2000,
        autoDelete = true,
        enableDeadLetterQueue = true,
        deadLetterQueueName = "${app.payment.dlq.name}"
    )
    public void processPayment(SqsMessage message) {
        PaymentRequest request = parsePaymentRequest(message.getBody());
        
        try {
            processPaymentTransaction(request);
        } catch (PaymentException e) {
            // 재시도 로직이 자동으로 처리됨
            throw e;
        }
    }
}
```

### 4. 큐 URL 직접 사용

```java
@Service
public class NotificationService {
    
    @SqsListener(queueUrl = "https://sqs.us-east-1.amazonaws.com/123456789012/notifications")
    public void sendNotification(SqsMessage message) {
        NotificationData data = parseNotification(message.getBody());
        sendPushNotification(data);
    }
}
```

## 설정 옵션

### application.yml 설정

```yaml
aws:
  sqs:
    consumer:
      enabled: true
      default-max-concurrent-messages: 10
      default-poll-timeout-seconds: 20
      default-message-visibility-seconds: 30
      default-max-messages-per-poll: 10
      default-batch-size: 10
      default-max-retry-attempts: 3
      default-retry-delay-millis: 1000
      default-auto-delete: true
      
      # 스레드 풀 설정
      thread-pool-size: 20
      thread-pool-core-size: 10
      thread-pool-max-size: 50
      thread-pool-queue-capacity: 100
      thread-name-prefix: "sqs-consumer-"
      
      # 모니터링 설정
      enable-metrics: true
      health-check-interval-millis: 30000
      shutdown-timeout-millis: 30000
```

## @SqsListener 어노테이션 파라미터

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `queueName` | String | "" | SQS 큐 이름 |
| `queueUrl` | String | "" | SQS 큐 URL (queueName과 배타적) |
| `id` | String | "" | 컨테이너 고유 ID |
| `maxConcurrentMessages` | int | 10 | 동시 처리 메시지 수 |
| `pollTimeoutSeconds` | int | 20 | 롱 폴링 타임아웃 (0-20초) |
| `messageVisibilitySeconds` | int | 30 | 메시지 가시성 타임아웃 |
| `maxMessagesPerPoll` | int | 10 | 폴링당 최대 메시지 수 (1-10) |
| `batchMode` | boolean | false | 배치 처리 모드 활성화 |
| `batchSize` | int | 10 | 배치 크기 |
| `autoDelete` | boolean | true | 성공 처리 후 자동 삭제 |
| `maxRetryAttempts` | int | 3 | 최대 재시도 횟수 |
| `retryDelayMillis` | long | 1000 | 재시도 간격 (밀리초) |
| `enableDeadLetterQueue` | boolean | false | DLQ 처리 활성화 |
| `deadLetterQueueName` | String | "" | DLQ 큐 이름 |

## Spring Cloud AWS와의 차이점

| 기능 | AWS Kit SQS Consumer | Spring Cloud AWS |
|------|---------------------|-------------------|
| **타입 추상화** | ✅ 완전한 AWS SDK 숨김 | ❌ AWS SDK 타입 노출 |
| **의존성** | 경량 (AWS Kit만) | 무거움 (Spring Cloud 전체) |
| **설정** | 간단한 어노테이션 | 복잡한 설정 |
| **커스터마이징** | 높음 | 제한적 |
| **성능** | 최적화됨 | 일반적 |

## 컨테이너 관리 및 모니터링

```java
@RestController
public class SqsMonitoringController {
    
    @Autowired
    private SqsListenerContainerRegistry containerRegistry;
    
    @GetMapping("/sqs/stats")
    public SqsListenerContainerRegistry.RegistryStats getStats() {
        return containerRegistry.getStats();
    }
    
    @GetMapping("/sqs/containers")
    public Collection<SqsListenerContainer> getContainers() {
        return containerRegistry.getAllContainers();
    }
    
    @PostMapping("/sqs/containers/{containerId}/start")
    public void startContainer(@PathVariable String containerId) {
        SqsListenerContainer container = containerRegistry.getContainer(containerId);
        if (container != null && !container.isRunning()) {
            container.start();
        }
    }
    
    @PostMapping("/sqs/containers/{containerId}/stop")
    public void stopContainer(@PathVariable String containerId) {
        SqsListenerContainer container = containerRegistry.getContainer(containerId);
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }
}
```

## 베스트 프랙티스

### 1. 메시지 처리 멱등성
```java
@SqsListener(queueName = "user-events")
public void handleUserEvent(SqsMessage message) {
    String messageId = message.getMessageId();
    
    // 중복 처리 방지
    if (processedMessageIds.contains(messageId)) {
        return;
    }
    
    try {
        processUserEvent(message.getBody());
        processedMessageIds.add(messageId);
    } catch (Exception e) {
        // 에러 처리 및 재시도
        throw e;
    }
}
```

### 2. 구조화된 메시지 처리
```java
@SqsListener(queueName = "structured-events")
public void handleStructuredEvent(SqsMessage message) {
    try {
        EventData event = objectMapper.readValue(message.getBody(), EventData.class);
        
        String eventType = message.getMessageAttribute("eventType")
            .getStringValue();
            
        switch (eventType) {
            case "ORDER_CREATED" -> handleOrderCreated(event);
            case "ORDER_UPDATED" -> handleOrderUpdated(event);
            case "ORDER_CANCELLED" -> handleOrderCancelled(event);
            default -> throw new UnsupportedOperationException("Unknown event type: " + eventType);
        }
    } catch (Exception e) {
        log.error("Failed to process structured event: {}", e.getMessage(), e);
        throw e;
    }
}
```

### 3. 배치 처리 최적화
```java
@SqsListener(
    queueName = "bulk-data-processing",
    batchMode = true,
    batchSize = 10,
    maxConcurrentMessages = 3 // 메모리 사용량 고려
)
public void processBulkData(List<SqsMessage> messages) {
    List<DataRecord> records = messages.parallelStream()
        .map(this::parseDataRecord)
        .collect(Collectors.toList());
    
    // 배치 데이터베이스 삽입으로 성능 최적화
    dataRepository.saveAllInBatch(records);
}
```

## 주의사항

1. **메시지 처리 시간**: visibility timeout 내에 처리 완료 필요
2. **재시도 전략**: 무한 재시도 방지를 위한 적절한 maxRetryAttempts 설정
3. **DLQ 모니터링**: 실패한 메시지들의 DLQ 정기 점검 필요
4. **동시성 제어**: maxConcurrentMessages로 리소스 사용량 조절
5. **배치 처리**: 메모리 사용량 고려하여 적절한 배치 크기 설정