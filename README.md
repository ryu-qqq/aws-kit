# AWS Kit - Spring Boot를 위한 간소화된 AWS SDK

Spring Boot 애플리케이션을 위해 설계된 경량 모듈식 AWS SDK 래퍼 라이브러리입니다. 이 라이브러리는 AWS SDK의 성능과 유연성을 유지하면서 일반적인 AWS 서비스에 대한 간소화된 인터페이스를 제공합니다.

## 🎯 설계 철학

- **단순성 우선**: 필수 작업만, 과도한 엔지니어링 없음
- **직접적인 AWS SDK 접근**: 무거운 추상화가 아닌 얇은 래퍼
- **Spring Boot 네이티브**: 자동 구성 및 속성 기반 설정
- **기본적으로 비동기**: 모든 작업이 `CompletableFuture` 반환
- **모듈식 아키텍처**: 필요한 것만 사용

## 📦 모듈

| 모듈 | 설명 | 상태 |
|--------|-------------|--------|
| `aws-sdk-commons` | 핵심 구성 및 공유 컴포넌트 | ✅ 안정 |
| `aws-dynamodb-client` | 간소화된 DynamoDB 작업 | ✅ 안정 |
| `aws-s3-client` | S3 파일 작업 및 관리 | ✅ 안정 |
| `aws-sqs-client` | SQS 메시지 큐 작업 | ✅ 안정 |
| `aws-lambda-client` | Lambda 함수 호출 | ✅ 안정 |

## 🚀 빠른 시작

### JitPack을 통한 사용

#### build.gradle에 JitPack 저장소 추가
```gradle
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

#### 의존성 추가
```gradle
dependencies {
    implementation 'com.github.yourusername.awskit:aws-sdk-commons:1.0.0'
    implementation 'com.github.yourusername.awskit:aws-dynamodb-client:1.0.0'
    implementation 'com.github.yourusername.awskit:aws-s3-client:1.0.0'
    implementation 'com.github.yourusername.awskit:aws-sqs-client:1.0.0'
    implementation 'com.github.yourusername.awskit:aws-lambda-client:1.0.0'
}
```

### Spring Boot 구성

```yaml
aws:
  region: ap-northeast-2
  endpoint: ${AWS_ENDPOINT:}  # 선택사항, LocalStack용
  access-key: ${AWS_ACCESS_KEY_ID}
  secret-key: ${AWS_SECRET_ACCESS_KEY}
  
aws:
  dynamodb:
    table-prefix: ${ENVIRONMENT}-
    timeout: 30s
    max-retries: 3
```

### 사용 예제

```java
@Service
public class UserService {
    private final DynamoDbService<User> dynamoDbService;
    
    public CompletableFuture<Void> saveUser(User user) {
        return dynamoDbService.save(user, "users");
    }
    
    public CompletableFuture<User> getUser(String userId) {
        Key key = Key.builder()
            .partitionValue(userId)
            .build();
        return dynamoDbService.load(User.class, key, "users");
    }
}
```

## ⚠️ 중요: 의존성 관리

### 현재 상태
라이브러리는 현재 모든 의존성을 전이적으로 노출하는 `api` 구성을 사용합니다. 이는 리팩토링 중입니다.

### 권장 사용 패턴

**Spring Boot 애플리케이션의 경우:**
```gradle
dependencies {
    // AWS Kit 모듈
    implementation 'com.github.yourusername.awskit:aws-sdk-commons:1.0.0'
    implementation 'com.github.yourusername.awskit:aws-dynamodb-client:1.0.0'
    
    // Spring Boot 의존성 (자체 버전 관리)
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

**Spring이 아닌 애플리케이션의 경우:**
```gradle
dependencies {
    // 핵심 모듈만 사용 (Spring Boot 자동 구성이 활성화되지 않음)
    implementation('com.github.yourusername.awskit:aws-dynamodb-client:1.0.0') {
        exclude group: 'org.springframework.boot'
    }
    
    // 자체 AWS SDK 의존성 제공
    implementation 'software.amazon.awssdk:dynamodb-enhanced:2.28.11'
}
```

### 의존성 충돌 처리

버전 충돌이 발생하는 경우:

```gradle
configurations.all {
    resolutionStrategy {
        force 'org.springframework.boot:spring-boot-starter:3.3.4'
        force 'software.amazon.awssdk:bom:2.28.11'
    }
}
```

## 🔧 AWS SDK에서 마이그레이션

### 이전 (직접 AWS SDK)
```java
DynamoDbAsyncClient client = DynamoDbAsyncClient.builder()
    .region(Region.AP_NORTHEAST_2)
    .build();
    
DynamoDbEnhancedAsyncClient enhancedClient = DynamoDbEnhancedAsyncClient.builder()
    .dynamoDbClient(client)
    .build();
    
DynamoDbAsyncTable<User> table = enhancedClient.table("users", TableSchema.fromBean(User.class));
CompletableFuture<Void> future = table.putItem(user);
```

### 이후 (AWS Kit)
```java
@Autowired
private DynamoDbService<User> dynamoDbService;

CompletableFuture<Void> future = dynamoDbService.save(user, "users");
```

## 📋 모듈 호환성 매트릭스

| AWS Kit 버전 | Spring Boot | AWS SDK | Java |
|----------------|-------------|---------|------|
| 1.0.x | 3.3.x | 2.28.x | 21+ |
| 0.9.x | 3.2.x | 2.27.x | 17+ |

## 🏗️ 아키텍처 결정

### 왜 `api` 구성인가 (임시)
초기 개발 중 단순성을 위해 현재 `api` 구성을 사용합니다. 이는 v2.0에서 적절한 API/구현 분리와 함께 `implementation`으로 변경됩니다.

### 향후 개선사항 (v2.0)
1. **API와 구현 분리**
   - `awskit-api` - 인터페이스만
   - `awskit-impl` - 구현
   - `awskit-spring-boot-starter` - 자동 구성

2. **BOM (Bill of Materials)**
   ```gradle
   dependencies {
       implementation platform('com.github.yourusername:awskit-bom:2.0.0')
       implementation 'com.github.yourusername:aws-dynamodb-client'
       // 버전은 BOM에서 관리
   }
   ```

3. **선택적 의존성**
   - Spring Boot를 `optional`로
   - 서비스별 AWS SDK를 `optional`로

## 🧪 테스팅

모든 모듈은 LocalStack을 사용한 통합 테스트를 포함합니다:

```java
@SpringBootTest
@Testcontainers
class DynamoDbIntegrationTest {
    @Container
    static LocalStackContainer localstack = new LocalStackContainer()
            .withServices(Service.DYNAMODB);
    
    @Test
    void testDynamoDbOperations() {
        // 테스트 코드
    }
}
```

## 📊 코드 간소화 지표

| 모듈 | 이전 | 이후 | 감소율 |
|--------|--------|-------|-----------|
| DynamoDB | 8,000줄 이상 | ~1,500줄 | 85% |
| Commons | ~2,000줄 | ~400줄 | 70% |
| 전체 | 15,000줄 이상 | ~3,000줄 | 80% |

## 🤝 기여

이 프로젝트는 단순성의 원칙을 따릅니다. 기능을 추가하기 전에 고려하세요:
1. 이것이 80%의 사용 사례에 필수적인가?
2. 기존 AWS SDK 기능으로 달성할 수 있는가?
3. 상당한 복잡성을 추가하는가?

## 📄 라이선스

Apache License 2.0

## 🔗 관련 프로젝트

- [AWS SDK for Java v2](https://github.com/aws/aws-sdk-java-v2)
- [Spring Cloud AWS](https://spring.io/projects/spring-cloud-aws)
- [LocalStack](https://github.com/localstack/localstack)

## ⚡ 성능 고려사항

- 모든 작업은 기본적으로 비동기
- AWS SDK의 HTTP 클라이언트를 통한 연결 풀링
- 지수 백오프를 사용한 자동 재시도
- 향상된 처리량을 위한 배치 작업

## 🛡️ 보안

- AWS 자격 증명을 절대 커밋하지 마세요
- 프로덕션에서는 IAM 역할 사용
- 필요한 경우 AWS SDK 클라이언트 측 암호화 활성화
- 보안 패치를 위한 정기적인 의존성 업데이트

## 📚 문서

- [AWS SDK Commons](./aws-sdk-commons/README.md)
- [DynamoDB Client](./aws-dynamodb-client/README.md)
- [S3 Client](./aws-s3-client/README.md)
- [SQS Client](./aws-sqs-client/README.md)
- [Lambda Client](./aws-lambda-client/README.md)

---

**참고**: 이 라이브러리는 활발히 개발 중입니다. v1.0 안정 릴리스까지 마이너 버전에서 API가 변경될 수 있습니다.