# AWS Kit - Enterprise-Grade AWS SDK Abstraction for Spring Boot

[![JitPack](https://jitpack.io/v/com.github.ryuqq/awskit.svg)](https://jitpack.io/#com.github.ryuqq/awskit)
[![Java Version](https://img.shields.io/badge/Java-21+-blue.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![AWS SDK](https://img.shields.io/badge/AWS%20SDK-2.28.11-orange.svg)](https://aws.amazon.com/sdk-for-java/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Test Coverage](https://img.shields.io/badge/Coverage-80%25+-green.svg)](#testing)

**Professional AWS SDK wrapper library designed for Spring Boot applications. Provides simplified interfaces while maintaining the full power and flexibility of AWS SDK v2, with comprehensive type abstraction and 80%+ test coverage.**

## 🌟 Key Features

- **🎯 Type Abstraction**: Custom types (DynamoKey, SqsMessage, etc.) - no direct AWS SDK dependency required
- **🚀 Async-First**: All operations return `CompletableFuture` for non-blocking performance
- **🏗️ Modular Architecture**: Include only the AWS services you need
- **⚙️ Spring Boot Native**: Auto-configuration with property-based setup
- **🔧 Enterprise Ready**: Comprehensive error handling, retries, and monitoring
- **📊 Production Tested**: 80%+ test coverage with LocalStack integration
- **🔄 Type-Safe**: Strong typing with adapter pattern for AWS SDK conversion

## 📦 Available Modules

| Module | Description | Key Features | Status |
|--------|-------------|--------------|--------|
| `aws-sdk-commons` | Core configuration and shared components | Region, credentials, retry policies | ✅ Stable |
| `aws-dynamodb-client` | DynamoDB operations with type abstraction | DynamoKey, DynamoQuery, transactions | ✅ Stable |
| `aws-s3-client` | S3 file operations and management | Upload, download, presigned URLs | ✅ Stable |
| `aws-sqs-client` | SQS messaging with custom types | SqsMessage, batch operations | ✅ Stable |
| `aws-lambda-client` | Lambda function invocation | Async invocation, error handling | ✅ Stable |

## 🚀 Quick Start

### 1. Add JitPack Repository

**Gradle:**
```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

**Maven:**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

### 2. Add Dependencies

**Gradle:**
```gradle
dependencies {
    // Core module (required)
    implementation 'com.github.ryuqq.awskit:aws-sdk-commons:1.0.0'
    
    // Service modules (add as needed)
    implementation 'com.github.ryuqq.awskit:aws-dynamodb-client:1.0.0'
    implementation 'com.github.ryuqq.awskit:aws-s3-client:1.0.0'
    implementation 'com.github.ryuqq.awskit:aws-sqs-client:1.0.0'
    implementation 'com.github.ryuqq.awskit:aws-lambda-client:1.0.0'
    
    // Spring Boot (user-managed version)
    implementation 'org.springframework.boot:spring-boot-starter'
}
```

**Maven:**
```xml
<dependencies>
    <!-- Core module -->
    <dependency>
        <groupId>com.github.ryuqq.awskit</groupId>
        <artifactId>aws-sdk-commons</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Service modules -->
    <dependency>
        <groupId>com.github.ryuqq.awskit</groupId>
        <artifactId>aws-dynamodb-client</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

### 3. Configuration

**application.yml:**
```yaml
aws:
  region: ap-northeast-2
  credentials:
    access-key-id: ${AWS_ACCESS_KEY_ID}
    secret-access-key: ${AWS_SECRET_ACCESS_KEY}
    use-instance-profile: true
  
  # Service-specific configurations
  dynamodb:
    table-prefix: ${ENVIRONMENT}-
    connection-config:
      max-connections: 50
      connection-timeout: PT10S
    retry-config:
      max-retries: 3
      enable-adaptive-retry: true
  
  s3:
    client-config:
      transfer-acceleration: true
      multipart-threshold: 16MB
    
  sqs:
    default-queue-name: ${APP_NAME}-queue
    visibility-timeout: PT30S
    message-retention: P14D
```

## 💻 Usage Examples

### DynamoDB Operations

```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final DynamoDbService<User> dynamoDbService;
    
    public CompletableFuture<Void> saveUser(User user) {
        return dynamoDbService.save(user, "users");
    }
    
    public CompletableFuture<User> getUser(String userId) {
        DynamoKey key = DynamoKey.of("userId", userId);
        return dynamoDbService.load(User.class, key, "users");
    }
    
    public CompletableFuture<List<User>> queryUsersByStatus(String status) {
        DynamoQuery query = DynamoQuery.builder()
            .indexName("status-index")
            .keyCondition("status = :status")
            .attributeValue(":status", status)
            .build();
            
        return dynamoDbService.query(User.class, query, "users");
    }
    
    public CompletableFuture<Void> transferCredits(String fromUserId, String toUserId, int amount) {
        DynamoTransaction transaction = DynamoTransaction.builder()
            .addUpdate("users", 
                DynamoKey.of("userId", fromUserId),
                "SET credits = credits - :amount",
                Map.of(":amount", amount))
            .addUpdate("users",
                DynamoKey.of("userId", toUserId), 
                "SET credits = credits + :amount",
                Map.of(":amount", amount))
            .build();
            
        return dynamoDbService.executeTransaction(transaction);
    }
}
```

### S3 File Operations

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    private final S3Service s3Service;
    
    public CompletableFuture<String> uploadFile(String bucketName, String key, Path filePath) {
        return s3Service.uploadFile(bucketName, key, filePath)
            .thenApply(response -> response.getETag());
    }
    
    public CompletableFuture<byte[]> downloadFile(String bucketName, String key) {
        return s3Service.downloadAsBytes(bucketName, key);
    }
    
    public CompletableFuture<String> generatePresignedUrl(String bucketName, String key, Duration expiration) {
        return s3Service.generatePresignedGetUrl(bucketName, key, expiration)
            .thenApply(URL::toString);
    }
}
```

### SQS Messaging

```java
@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final SqsService sqsService;
    
    public CompletableFuture<Void> sendNotification(String queueName, Object message) {
        SqsMessage sqsMessage = SqsMessage.builder()
            .body(JsonUtils.toJson(message))
            .attribute("messageType", message.getClass().getSimpleName())
            .attribute("timestamp", Instant.now().toString())
            .build();
            
        return sqsService.sendMessage(queueName, sqsMessage);
    }
    
    public CompletableFuture<List<SqsMessage>> receiveMessages(String queueName) {
        return sqsService.receiveMessages(queueName, 10);
    }
}
```

### Lambda Invocation

```java
@Service
@RequiredArgsConstructor
public class ProcessingService {
    
    private final LambdaService lambdaService;
    
    public CompletableFuture<String> processAsync(ProcessingRequest request) {
        return lambdaService.invokeAsync("data-processor", request, String.class);
    }
    
    public CompletableFuture<ProcessingResult> processSync(ProcessingRequest request) {
        return lambdaService.invokeSync("data-processor", request, ProcessingResult.class);
    }
}
```

## 🏗️ Architecture & Design

### Type Abstraction System

AWS Kit provides clean abstractions over AWS SDK types:

```java
// Instead of AWS SDK types
Key awsKey = Key.builder()
    .partitionValue(AttributeValue.fromS(userId))
    .sortValue(AttributeValue.fromN(timestamp))
    .build();

// Use AWS Kit types  
DynamoKey key = DynamoKey.of("userId", userId)
    .withSortKey("timestamp", timestamp);
```

### Dependency Management

- **AWS SDK**: Encapsulated as `implementation` - not exposed to your application
- **Spring Boot**: Provided as `compileOnly` - use your preferred version
- **Clean Dependencies**: Only commons module exposed as `api` dependency

### Error Handling & Retries

```java
// Automatic retry with exponential backoff
aws:
  dynamodb:
    retry-config:
      max-retries: 3
      base-delay: PT0.1S
      max-backoff-time: PT30S
      enable-adaptive-retry: true
      retryable-error-codes:
        - "ProvisionedThroughputExceededException"
        - "InternalServerError"
```

## 🧪 Testing

### Test Coverage

- **Unit Tests**: 80%+ coverage across all modules
- **Integration Tests**: LocalStack-based testing for all AWS services
- **Type Adapters**: Comprehensive testing of AWS SDK type conversion

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific module tests
./gradlew :aws-dynamodb-client:test

# Run integration tests
./gradlew integrationTest

# Generate coverage report
./gradlew jacocoTestReport
```

### Example Integration Test

```java
@SpringBootTest
@Testcontainers
class DynamoDbIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(Service.DYNAMODB);

    @Test
    void shouldSaveAndLoadUser() {
        // Given
        User user = new User("user123", "John Doe", "john@example.com");
        
        // When
        CompletableFuture<Void> saveFuture = dynamoDbService.save(user, "users");
        saveFuture.join();
        
        DynamoKey key = DynamoKey.of("userId", "user123");
        CompletableFuture<User> loadFuture = dynamoDbService.load(User.class, key, "users");
        
        // Then
        User loadedUser = loadFuture.join();
        assertThat(loadedUser).isEqualTo(user);
    }
}
```

## 📊 Performance & Monitoring

### Built-in Metrics

```yaml
aws:
  dynamodb:
    monitoring-config:
      enable-cloud-watch-metrics: true
      cloud-watch-namespace: "DynamoDB/ApplicationMetrics"
      enable-performance-monitoring: true
      slow-query-threshold: PT1S
```

### Connection Pooling

```yaml
aws:
  dynamodb:
    connection-config:
      max-connections: 50
      connection-timeout: PT10S
      connection-acquisition-timeout: PT10S
      tcp-keep-alive: true
```

## 🔧 Advanced Configuration

### Environment-Specific Profiles

```yaml
---
spring:
  config:
    activate:
      on-profile: production
      
aws:
  dynamodb:
    table-config:
      billing-mode: "PAY_PER_REQUEST"
      enable-deletion-protection: true
    retry-config:
      max-retries: 5
      enable-adaptive-retry: true

---
spring:
  config:
    activate:
      on-profile: development
      
aws:
  endpoint: http://localhost:4566  # LocalStack
  dynamodb:
    table-config:
      billing-mode: "PROVISIONED"
      read-capacity-units: 5
      write-capacity-units: 5
```

### Custom Type Adapters

```java
@Component
public class CustomTypeAdapter implements DynamoTypeAdapter<CustomType> {
    
    @Override
    public AttributeValue toAttributeValue(CustomType value) {
        return AttributeValue.fromS(value.serialize());
    }
    
    @Override
    public CustomType fromAttributeValue(AttributeValue attributeValue) {
        return CustomType.deserialize(attributeValue.s());
    }
}
```

## 🔄 Migration from AWS SDK

### Before (Direct AWS SDK)

```java
// Complex setup
DynamoDbAsyncClient client = DynamoDbAsyncClient.builder()
    .region(Region.AP_NORTHEAST_2)
    .credentialsProvider(DefaultCredentialsProvider.create())
    .build();
    
DynamoDbEnhancedAsyncClient enhancedClient = DynamoDbEnhancedAsyncClient.builder()
    .dynamoDbClient(client)
    .build();
    
DynamoDbAsyncTable<User> table = enhancedClient.table("users", TableSchema.fromBean(User.class));

// Complex operations
Key key = Key.builder()
    .partitionValue(AttributeValue.fromS(userId))
    .build();
    
CompletableFuture<User> future = table.getItem(GetItemEnhancedRequest.builder()
    .key(key)
    .consistentRead(false)
    .build());
```

### After (AWS Kit)

```java
// Simple autowiring
@Autowired
private DynamoDbService<User> dynamoDbService;

// Simple operations
DynamoKey key = DynamoKey.of("userId", userId);
CompletableFuture<User> future = dynamoDbService.load(User.class, key, "users");
```

## 🔗 Module Documentation

- [**AWS SDK Commons**](./aws-sdk-commons) - Core configuration and shared components
- [**DynamoDB Client**](./aws-dynamodb-client) - Type-safe DynamoDB operations
- [**S3 Client**](./aws-s3-client) - File operations and presigned URLs
- [**SQS Client**](./aws-sqs-client) - Message queue operations
- [**Lambda Client**](./aws-lambda-client) - Function invocation and error handling

## 📈 Version Compatibility

| AWS Kit Version | Spring Boot | AWS SDK | Java | Status |
|-----------------|-------------|---------|------|--------|
| 1.0.x | 3.3.x | 2.28.x | 21+ | ✅ Current |
| 0.9.x | 3.2.x | 2.27.x | 17+ | 🔄 Maintenance |

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

```bash
# Clone repository
git clone https://github.com/ryuqq/awskit.git
cd awskit

# Build all modules
./gradlew build

# Run tests with LocalStack
./gradlew integrationTest

# Publish to local Maven
./gradlew publishToMavenLocal
```

### Code Quality

- **Code Style**: Google Java Style Guide
- **Test Coverage**: Minimum 80% for new code
- **Documentation**: Comprehensive JavaDoc for public APIs
- **Type Safety**: Strong typing with adapter patterns

## 📄 License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## 🆘 Support

- **Issues**: [GitHub Issues](https://github.com/ryuqq/awskit/issues)
- **Documentation**: [Wiki](https://github.com/ryuqq/awskit/wiki)
- **Examples**: [Examples Repository](./examples)

## 🌏 한국어 요약

Spring Boot 애플리케이션을 위한 엔터프라이즈급 AWS SDK 추상화 라이브러리입니다.

### 주요 특징
- **타입 추상화**: AWS SDK에 직접 의존하지 않는 커스텀 타입 시스템
- **비동기 우선**: 모든 작업이 `CompletableFuture` 반환
- **모듈식 설계**: 필요한 AWS 서비스만 선택적 사용
- **높은 테스트 커버리지**: 80%+ 테스트 커버리지와 LocalStack 통합 테스트
- **Spring Boot 네이티브**: 자동 구성 및 속성 기반 설정

### 사용법
```gradle
implementation 'com.github.ryuqq.awskit:aws-dynamodb-client:1.0.0'
```

자세한 한국어 문서는 각 모듈의 README를 참조하세요.

---

**Built with ❤️ for the Spring Boot and AWS community**