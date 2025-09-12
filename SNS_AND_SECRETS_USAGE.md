# AWS SNS & Secrets Manager Usage Guide

## Overview

This guide covers the usage of two new awskit modules:
- **aws-sns-client**: For Amazon Simple Notification Service (SNS) operations
- **aws-secrets-client**: For AWS Secrets Manager and Systems Manager Parameter Store

## 🚀 Quick Start

### Gradle Dependencies

```gradle
dependencies {
    // SNS Client
    implementation 'com.github.ryuqq.awskit:aws-sns-client:2.0.0'
    
    // Secrets Manager & Parameter Store
    implementation 'com.github.ryuqq.awskit:aws-secrets-client:2.0.0'
}
```

### Spring Boot Configuration

```yaml
aws:
  region: ap-northeast-2
  
  # SNS Configuration
  sns:
    enabled: true
    default-topic-arn: arn:aws:sns:ap-northeast-2:123456789012:my-topic
    connection-config:
      max-connections: 50
      connection-timeout: PT10S
    retry-config:
      max-retries: 3
      enable-adaptive-retry: true
    sms-config:
      sender-id: MyApp
      sms-type: TRANSACTIONAL
      max-price: 0.50
  
  # Secrets Configuration  
  secrets:
    enabled: true
    cache:
      enabled: true
      ttl: PT5M  # 5 minutes
      maximum-size: 1000
    parameter-store:
      path-prefix: /myapp
      recursive-by-default: true
      with-decryption-by-default: true
```

## 📢 SNS Client Usage

### Basic Message Publishing

```java
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final SnsService snsService;
    
    // Simple text message
    public CompletableFuture<SnsPublishResult> sendNotification(String message) {
        SnsMessage snsMessage = SnsMessage.of(message);
        return snsService.publish("arn:aws:sns:region:account:topic", snsMessage);
    }
    
    // Message with subject
    public CompletableFuture<SnsPublishResult> sendAlert(String subject, String body) {
        SnsMessage snsMessage = SnsMessage.of(subject, body);
        return snsService.publish("arn:aws:sns:region:account:alerts", snsMessage);
    }
    
    // Message with attributes
    public CompletableFuture<SnsPublishResult> sendOrderUpdate(Order order) {
        SnsMessage message = SnsMessage.builder()
            .subject("Order Update")
            .body(toJson(order))
            .withAttribute("orderId", order.getId())
            .withAttribute("customerId", order.getCustomerId())
            .withAttribute("amount", String.valueOf(order.getAmount()))
            .build();
            
        return snsService.publish("order-events", message);
    }
}
```

### SMS Messaging

```java
@Service
@RequiredArgsConstructor
public class SmsService {
    
    private final SnsService snsService;
    
    public CompletableFuture<SnsPublishResult> sendOtp(String phoneNumber, String otp) {
        String message = String.format("Your OTP is: %s. Valid for 5 minutes.", otp);
        return snsService.publishSms(phoneNumber, message);
    }
}
```

### Multi-Protocol Messages

```java
// Different message for different protocols (email, SMS, etc.)
public CompletableFuture<SnsPublishResult> sendMultiProtocol() {
    Map<String, String> protocolMessages = Map.of(
        "default", "Default message",
        "email", "Detailed email message with HTML content",
        "sms", "Short SMS message",
        "https", "{\"key\":\"value\"}"  // JSON for HTTP endpoints
    );
    
    SnsMessage message = SnsMessage.jsonMessage(protocolMessages);
    return snsService.publish(topicArn, message);
}
```

### Batch Publishing (FIFO Topics)

```java
public CompletableFuture<List<SnsPublishResult>> sendBatch(List<Order> orders) {
    List<SnsMessage> messages = orders.stream()
        .map(order -> SnsMessage.builder()
            .body(toJson(order))
            .messageGroupId("orders")  // For FIFO topics
            .messageDeduplicationId(order.getId())
            .build())
        .collect(Collectors.toList());
        
    return snsService.publishBatch("orders.fifo", messages);
}
```

### Topic Management

```java
@Service
@RequiredArgsConstructor
public class TopicManagementService {
    
    private final SnsService snsService;
    
    // Create topic
    public CompletableFuture<SnsTopic> createTopic(String topicName) {
        return snsService.createTopic(topicName);
    }
    
    // Subscribe to topic
    public CompletableFuture<SnsSubscription> subscribeEmail(String topicArn, String email) {
        return snsService.subscribe(topicArn, "email", email);
    }
    
    public CompletableFuture<SnsSubscription> subscribeSqs(String topicArn, String queueArn) {
        return snsService.subscribe(topicArn, "sqs", queueArn);
    }
    
    // List subscriptions
    public CompletableFuture<List<SnsSubscription>> getSubscriptions(String topicArn) {
        return snsService.listSubscriptions(topicArn);
    }
}
```

## 🔐 Secrets Manager Usage

### Basic Secret Operations

```java
@Service
@RequiredArgsConstructor
public class SecretService {
    
    private final SecretsService secretsService;
    
    // Get secret as string
    public CompletableFuture<String> getDatabasePassword() {
        return secretsService.getSecret("rds/myapp/password");
    }
    
    // Get secret as JSON map
    public CompletableFuture<Map<String, Object>> getDatabaseConfig() {
        return secretsService.getSecretAsMap("rds/myapp/config");
    }
    
    // Get secret as specific type
    public CompletableFuture<DatabaseConfig> getTypedConfig() {
        return secretsService.getSecret("rds/myapp/config", DatabaseConfig.class);
    }
    
    @Data
    public static class DatabaseConfig {
        private String host;
        private int port;
        private String username;
        private String password;
        private String database;
    }
}
```

### Creating and Updating Secrets

```java
@Service
@RequiredArgsConstructor
public class SecretManagementService {
    
    private final SecretsService secretsService;
    
    // Create string secret
    public CompletableFuture<String> createApiKey(String keyName, String apiKey) {
        return secretsService.createSecret(keyName, apiKey);
    }
    
    // Create JSON secret from object
    public CompletableFuture<String> createDatabaseConfig(DatabaseConfig config) {
        return secretsService.createSecret("db/config", config);
    }
    
    // Update existing secret
    public CompletableFuture<String> rotateApiKey(String keyName, String newApiKey) {
        return secretsService.updateSecret(keyName, newApiKey);
    }
    
    // Delete secret
    public CompletableFuture<Void> deleteSecret(String secretName) {
        return secretsService.deleteSecret(secretName, false); // with 30-day recovery
    }
}
```

### Secret Rotation

```java
@Component
@RequiredArgsConstructor
public class RotationHandler {
    
    private final SecretsService secretsService;
    
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void rotateSecrets() {
        secretsService.rotateSecret("api/key")
            .thenAccept(versionId -> 
                log.info("Rotated secret to version: {}", versionId))
            .exceptionally(throwable -> {
                log.error("Failed to rotate secret", throwable);
                return null;
            });
    }
}
```

## 🗂️ Parameter Store Usage

### Basic Parameter Operations

```java
@Service
@RequiredArgsConstructor
public class ConfigService {
    
    private final ParameterStoreService parameterStore;
    
    // Get single parameter
    public CompletableFuture<String> getFeatureFlag(String flagName) {
        return parameterStore.getParameter("/myapp/features/" + flagName);
    }
    
    // Get secure string with decryption
    public CompletableFuture<String> getEncryptedConfig() {
        return parameterStore.getParameter("/myapp/secure/api-key", true);
    }
    
    // Get multiple parameters
    public CompletableFuture<Map<String, String>> getAppConfig() {
        List<String> params = List.of(
            "/myapp/db/host",
            "/myapp/db/port",
            "/myapp/cache/url"
        );
        return parameterStore.getParameters(params, true);
    }
    
    // Get parameters by path
    public CompletableFuture<Map<String, String>> getAllFeatureFlags() {
        return parameterStore.getParametersByPath("/myapp/features", true, false);
    }
}
```

### Managing Parameters

```java
@Service
@RequiredArgsConstructor
public class ParameterManagementService {
    
    private final ParameterStoreService parameterStore;
    
    // Create or update parameter
    public CompletableFuture<Long> setConfig(String key, String value) {
        return parameterStore.putParameter(
            "/myapp/" + key, 
            value, 
            ParameterType.STRING,
            true  // overwrite if exists
        );
    }
    
    // Store secure string
    public CompletableFuture<Long> storeSecret(String key, String secret) {
        return parameterStore.putSecureParameter("/myapp/secure/" + key, secret);
    }
    
    // Delete parameter
    public CompletableFuture<Void> removeConfig(String key) {
        return parameterStore.deleteParameter("/myapp/" + key);
    }
    
    // Add tags
    public CompletableFuture<Void> tagParameter(String name, Map<String, String> tags) {
        return parameterStore.addTagsToParameter(name, tags);
    }
}
```

## 💾 Cache Management

Both Secrets Manager and Parameter Store support caching:

```java
@Service
@RequiredArgsConstructor
public class CacheManagementService {
    
    private final SecretsService secretsService;
    private final ParameterStoreService parameterStore;
    private final SecretsCacheManager cacheManager;
    
    // Invalidate specific cache entry
    public void refreshSecret(String secretName) {
        secretsService.invalidateCache(secretName);
    }
    
    public void refreshParameter(String paramName) {
        parameterStore.invalidateCache(paramName);
    }
    
    // Clear all cache
    public void clearAllCache() {
        secretsService.invalidateAllCache();
    }
    
    // Get cache statistics
    public void logCacheStats() {
        SecretsCacheManager.CacheStats stats = cacheManager.getStats();
        log.info("Cache stats - Hit rate: {}, Size: {}, Evictions: {}", 
                stats.hitRate(), stats.getSize(), stats.getEvictionCount());
    }
}
```

## 🧪 Testing with LocalStack

```java
@SpringBootTest
@Testcontainers
class IntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer()
            .withServices(Service.SNS, Service.SECRETSMANAGER, Service.SSM);
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("aws.sns.endpoint", () -> localstack.getEndpointOverride(Service.SNS));
        registry.add("aws.secrets.secrets-manager-endpoint", 
                    () -> localstack.getEndpointOverride(Service.SECRETSMANAGER));
        registry.add("aws.secrets.ssm-endpoint", 
                    () -> localstack.getEndpointOverride(Service.SSM));
    }
    
    @Test
    void testSnsPublish() {
        // Test SNS operations
    }
    
    @Test
    void testSecretsManager() {
        // Test Secrets Manager operations
    }
}
```

## 🎯 Best Practices

### SNS Best Practices
1. **Use message attributes** for filtering and routing
2. **Implement retry logic** for failed publishes
3. **Use FIFO topics** for ordered message delivery
4. **Set up DLQ** for failed message processing
5. **Monitor CloudWatch metrics** for delivery success rates

### Secrets Management Best Practices
1. **Enable caching** to reduce API calls and latency
2. **Use versioning** for secret rotation
3. **Implement least privilege** IAM policies
4. **Rotate secrets regularly** using AWS Lambda
5. **Use Parameter Store** for non-sensitive configuration
6. **Use Secrets Manager** for sensitive credentials
7. **Tag resources** for cost tracking and organization

## 🔧 Virtual Thread Support

Both modules support Java 21 Virtual Threads:

```java
@Configuration
public class VirtualThreadConfig {
    
    @Bean
    @ConditionalOnJava(JavaVersion.TWENTY_ONE)
    public ExecutorService virtualThreadExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
```

## 📊 Monitoring & Metrics

```java
@Component
@RequiredArgsConstructor
public class MetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    @EventListener
    public void onSnsPublish(SnsPublishEvent event) {
        meterRegistry.counter("sns.messages.published",
            "topic", event.getTopicArn(),
            "status", event.isSuccess() ? "success" : "failure"
        ).increment();
    }
    
    @EventListener
    public void onSecretAccess(SecretAccessEvent event) {
        meterRegistry.counter("secrets.accessed",
            "name", event.getSecretName(),
            "cached", String.valueOf(event.isFromCache())
        ).increment();
    }
}
```

## 🚨 Error Handling

```java
@ControllerAdvice
public class AwsExceptionHandler {
    
    @ExceptionHandler(SnsService.SnsPublishException.class)
    public ResponseEntity<String> handleSnsError(SnsPublishException e) {
        log.error("SNS publish failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("Notification service temporarily unavailable");
    }
    
    @ExceptionHandler(SecretsService.SecretsException.class)
    public ResponseEntity<String> handleSecretsError(SecretsException e) {
        log.error("Secrets access failed", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Configuration error");
    }
}
```

## Summary

The new SNS and Secrets Manager modules provide:
- **Type-safe abstractions** over AWS SDK
- **Async-first design** with CompletableFuture
- **Built-in caching** for performance
- **Spring Boot integration** with auto-configuration
- **Virtual Thread support** for Java 21+
- **Comprehensive error handling**
- **Production-ready defaults**

Both modules follow the same patterns as other awskit modules, making them easy to integrate into existing projects.