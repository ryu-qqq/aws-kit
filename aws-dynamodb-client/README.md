# AWS DynamoDB Client

Amazon DynamoDB 작업을 위한 간소화된 Spring Boot 스타터로 비동기 지원과 필수 작업에 중점을 둡니다.

## 개요

`aws-dynamodb-client` 모듈은 가장 일반적으로 사용되는 8가지 작업에 중점을 두면서 `CompletableFuture`를 통한 완전한 비동기 지원을 유지하는 간소화된 DynamoDB 클라이언트를 제공합니다. 이 모듈은 복잡한 구성을 제거하고 DynamoDB 작업을 위한 깨끗하고 프로덕션 준비가 된 인터페이스를 제공합니다.

### 설계 철학

- **단순성 우선**: 불필요한 복잡성 없이 필수 작업에 집중
- **기본적으로 비동기**: 논블로킹 실행을 위해 모든 작업이 `CompletableFuture` 반환
- **Spring Boot 통합**: 합리적인 기본값으로 자동 구성
- **프로덕션 준비**: 내장된 재시도 로직, 연결 풀링 및 모니터링 지원

## 주요 기능

### 필수 작업 (8가지 핵심 메소드)

1. **save** - 테이블에 단일 항목 저장
2. **load** - 키로 항목 로드
3. **delete** - 키로 항목 삭제
4. **query** - 조건으로 항목 쿼리
5. **scan** - 전체 테이블 스캔
6. **batchSave** - 여러 항목 효율적으로 저장
7. **batchLoad** - 키로 여러 항목 로드
8. **transactWrite** - 트랜잭션 작업 실행

### 기술적 특징

- ✅ **비동기 작업**: 논블로킹 `CompletableFuture` 지원
- ✅ **자동 구성**: Spring Boot 자동 구성
- ✅ **연결 풀링**: 최적화된 연결 관리
- ✅ **재시도 로직**: 내장된 지수 백오프
- ✅ **LocalStack 지원**: 컨테이너화된 DynamoDB로 쉬운 테스팅
- ✅ **향상된 클라이언트**: AWS SDK Enhanced DynamoDB 클라이언트 사용
- ✅ **빈 매핑**: POJO에서 DynamoDB로 자동 매핑

## 빠른 시작

### 1. 의존성 추가

```gradle
dependencies {
    implementation 'com.github.yourusername.awskit:aws-dynamodb-client:1.0.0'
}
```

### 2. 속성 구성

```yaml
aws:
  dynamodb:
    region: ap-northeast-2
    table-prefix: "dev-"         # 모든 테이블명 앞에 추가될 접두사
    table-suffix: "-v1"          # 모든 테이블명 뒤에 추가될 접미사
    timeout: PT30S                # API 호출 타임아웃
    max-retries: 3                # 재시도 횟수
```

### 3. 엔티티 정의

```java
@DynamoDbBean
public class User {
    private String userId;
    private String profileType;
    private String name;
    private String email;

    @DynamoDbPartitionKey
    public String getUserId() { return userId; }
    
    @DynamoDbSortKey  
    public String getProfileType() { return profileType; }
    
    // 생성자, getter, setter...
}
```

### 4. 서비스 사용

```java
@Service
public class UserService {
    
    private final DynamoDbService<User> dynamoDbService;
    
    public UserService(DynamoDbService<User> dynamoDbService) {
        this.dynamoDbService = dynamoDbService;
    }
    
    public CompletableFuture<Void> saveUser(User user) {
        return dynamoDbService.save(user, "users");
    }
    
    public CompletableFuture<User> getUser(String userId, String profileType) {
        Key key = Key.builder()
            .partitionValue(userId)
            .sortValue(profileType)
            .build();
        return dynamoDbService.load(User.class, key, "users");
    }
}
```

## 사용 예제

### 기본 작업

#### 항목 저장
```java
User user = new User("user123", "profile", "홍길동", "hong@example.com");
CompletableFuture<Void> result = dynamoDbService.save(user, "users");
result.join(); // 완료 대기
```

#### 항목 로드
```java
Key key = Key.builder()
    .partitionValue("user123")
    .sortValue("profile")
    .build();
    
CompletableFuture<User> result = dynamoDbService.load(User.class, key, "users");
User user = result.join();
```

#### 항목 삭제
```java
Key key = Key.builder()
    .partitionValue("user123")
    .sortValue("profile")
    .build();
    
CompletableFuture<Void> result = dynamoDbService.delete(key, "users", User.class);
result.join();
```

### 쿼리 작업

#### 파티션 키로 쿼리
```java
QueryConditional condition = QueryConditional.keyEqualTo(
    Key.builder().partitionValue("user123").build()
);

CompletableFuture<List<User>> result = dynamoDbService.query(User.class, condition, "users");
List<User> users = result.join();
```

#### 테이블 스캔
```java
CompletableFuture<List<User>> result = dynamoDbService.scan(User.class, "users");
List<User> allUsers = result.join();
```

### 배치 작업

#### 배치 저장
```java
List<User> users = Arrays.asList(
    new User("user1", "profile", "김철수", "kim@example.com"),
    new User("user2", "profile", "이영희", "lee@example.com"),
    new User("user3", "profile", "박민수", "park@example.com")
);

CompletableFuture<Void> result = dynamoDbService.batchSave(users, "users");
result.join();
```

#### 배치 로드
```java
List<Key> keys = Arrays.asList(
    Key.builder().partitionValue("user1").sortValue("profile").build(),
    Key.builder().partitionValue("user2").sortValue("profile").build(),
    Key.builder().partitionValue("user3").sortValue("profile").build()
);

CompletableFuture<List<User>> result = dynamoDbService.batchLoad(User.class, keys, "users");
List<User> users = result.join();
```

### 비동기 구성

```java
public CompletableFuture<UserProfile> getUserWithProfile(String userId) {
    Key userKey = Key.builder().partitionValue(userId).sortValue("user").build();
    Key profileKey = Key.builder().partitionValue(userId).sortValue("profile").build();
    
    CompletableFuture<User> userFuture = dynamoDbService.load(User.class, userKey, "users");
    CompletableFuture<Profile> profileFuture = dynamoDbService.load(Profile.class, profileKey, "users");
    
    return userFuture.thenCombine(profileFuture, (user, profile) -> 
        new UserProfile(user, profile)
    );
}
```

## 구성

### 핵심 속성

```yaml
aws:
  dynamodb:
    # 리전 구성
    region: ap-northeast-2                # AWS 리전
    endpoint: http://localhost:4566       # 커스텀 엔드포인트 (LocalStack용)
    
    # 테이블 네이밍 (NEW!)
    table-prefix: "prod-"                 # 모든 테이블 이름의 접두사
    table-suffix: "-v1"                   # 모든 테이블 이름의 접미사
    
    # 클라이언트 구성 (NEW!)
    timeout: PT30S                        # API 호출 타임아웃 (HTTP 연결 포함)
    max-retries: 3                        # 최대 재시도 횟수
```

### 테이블명 변환 기능

이제 모든 DynamoDB 작업에서 자동으로 테이블명 변환이 적용됩니다:

```java
// 설정: prefix="dev-", suffix="-v1"
dynamoDbService.save(user, "users");    // 실제 테이블명: "dev-users-v1"
dynamoDbService.load(User.class, key, "products"); // 실제 테이블명: "dev-products-v1"
```

**환경별 테이블 분리**:
```yaml
# development
aws.dynamodb.table-prefix: "dev-"

# staging  
aws.dynamodb.table-prefix: "staging-"

# production
aws.dynamodb.table-prefix: "prod-"
```

**버전 관리**:
```yaml
# 현재 버전
aws.dynamodb.table-suffix: "-v2"

# 레거시 테이블과 분리
aws.dynamodb.table-suffix: "-legacy"
```

### 타임아웃 및 재시도 설정 (NEW!)

이제 모든 필드가 실제 클라이언트 설정에 적용됩니다:

```yaml
aws:
  dynamodb:
    # 타임아웃 설정 - 모든 HTTP 연결에 적용
    timeout: PT45S                        # 연결, 읽기, 쓰기 타임아웃
    
    # 재시도 설정 - DynamoDB 클라이언트에 적용
    max-retries: 5                        # 실패시 최대 5번 재시도
```

**타임아웃 설정 상세**:
- `connectionTimeout`: 연결 설정 타임아웃
- `connectionAcquisitionTimeout`: 연결 획득 타임아웃  
- `readTimeout`: 데이터 읽기 타임아웃
- `writeTimeout`: 데이터 쓰기 타임아웃

모든 타임아웃이 동일한 값으로 설정되어 일관된 동작을 보장합니다.

### 고급 구성

프로덕션 환경 최적화:

```yaml
aws:
  dynamodb:
    # 기본 설정
    region: ap-northeast-2
    table-prefix: "prod-"
    timeout: PT60S        # 프로덕션용 긴 타임아웃
    max-retries: 5        # 프로덕션용 많은 재시도
```

### 환경별 프로파일

- `application-dynamodb-dev.yml` - 개발 설정
- `application-dynamodb-prod.yml` - 프로덕션 최적화
- `application-dynamodb.yml` - 포괄적인 구성 참조

## LocalStack으로 테스팅

### 테스트 의존성 설정

```gradle
testImplementation 'org.testcontainers:testcontainers'
testImplementation 'org.testcontainers:junit-jupiter'
testImplementation 'org.testcontainers:localstack'
```

### 통합 테스트 예제

```java
@Testcontainers
class UserServiceIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(
        DockerImageName.parse("localstack/localstack:2.0")
    ).withServices(LocalStackContainer.Service.DYNAMODB);
    
    private DynamoDbService<User> dynamoDbService;
    
    @BeforeEach
    void setUp() {
        DynamoDbAsyncClient client = DynamoDbAsyncClient.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test")))
            .endpointOverride(localstack.getEndpoint())
            .build();
            
        DynamoDbEnhancedAsyncClient enhancedClient = 
            DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(client)
                .build();
                
        dynamoDbService = new DefaultDynamoDbService<>(enhancedClient);
        
        // 테스트 테이블 생성
        createTestTable();
    }
    
    @Test
    void shouldSaveAndLoadUser() {
        // Given
        User user = new User("test123", "profile", "테스트 사용자", "test@example.com");
        
        // When
        dynamoDbService.save(user, "test-users").join();
        
        Key key = Key.builder()
            .partitionValue("test123")
            .sortValue("profile")
            .build();
        User loaded = dynamoDbService.load(User.class, key, "test-users").join();
        
        // Then
        assertThat(loaded).isNotNull();
        assertThat(loaded.getName()).isEqualTo("테스트 사용자");
    }
}
```

### LocalStack 구성

```yaml
# application-test.yml
aws:
  dynamodb:
    endpoint: http://localhost:4566
    region: us-east-1
```

## Spring Boot 통합

### 자동 구성 (업데이트됨!)

모듈은 `AwsDynamoDbAutoConfiguration`을 통해 자동 구성을 제공합니다:

- **SdkAsyncHttpClient** - timeout 설정이 적용된 HTTP 클라이언트 (NEW!)
- **DynamoDbAsyncClient** - 리전, 엔드포인트, timeout, maxRetries가 모두 적용 (업데이트됨!)
- **DynamoDbEnhancedAsyncClient** - 객체 매핑을 위한 향상된 클라이언트
- **TableNameResolver** - prefix/suffix 변환을 담당하는 유틸리티 (NEW!) 
- **DynamoDbService** - 테이블명 변환이 적용된 서비스 구현 (업데이트됨!)

### 커스텀 구성

이제 모든 프로퍼티가 자동으로 적용되지만, 필요시 커스터마이징 가능합니다:

```java
@Configuration
public class CustomDynamoDbConfig {
    
    // 커스텀 테이블명 변환 로직
    @Bean
    @Primary
    public TableNameResolver customTableNameResolver() {
        return new TableNameResolver("custom-", "-special");
    }
    
    // 커스텀 HTTP 클라이언트 (고급 성능 튜닝)
    @Bean
    @Primary
    public SdkAsyncHttpClient customHttpClient(DynamoDbProperties properties) {
        return NettyNioAsyncHttpClient.builder()
            .connectionTimeout(properties.getTimeout())
            .maxConcurrency(100)  // 추가 설정
            .build();
    }
}
```

## 마이그레이션 가이드

### 복잡한 DynamoDB 구현에서 마이그레이션

복잡한 DynamoDB 설정에서 마이그레이션하는 경우:

#### 이전 (복잡한 구현)
```java
// 여러 클라이언트, 복잡한 구성, 리포지토리 패턴
@Repository
public class UserRepository {
    private final DynamoDbTemplate template;
    private final DynamoDbOperations operations;
    private final AsyncDynamoDbOperations asyncOps;
    
    // 50줄 이상의 구성
    // 여러 쿼리 메서드
    // 복잡한 오류 처리
}
```

#### 이후 (간소화된 구현)
```java
@Service
public class UserService {
    private final DynamoDbService<User> dynamoDbService;
    
    public CompletableFuture<User> findUser(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return dynamoDbService.load(User.class, key, "users");
    }
}
```

### 마이그레이션 단계

1. **의존성 교체**: 복잡한 DynamoDB 의존성 제거, `aws-dynamodb-client` 추가
2. **구성 간소화**: 복잡한 구성을 간단한 속성으로 교체
3. **서비스 레이어 업데이트**: 리포지토리 패턴을 직접 서비스 주입으로 교체
4. **작업 변환**: 기존 작업을 8가지 핵심 메서드로 매핑
5. **테스트 업데이트**: LocalStack 통합 테스트 사용

### 마이그레이션 후 이점

- ⚡ **복잡성 감소**: 구성 코드 80% 감소
- 🚀 **향상된 성능**: 비동기 우선 접근 방식
- 🧪 **쉬운 테스팅**: 내장된 LocalStack 지원
- 📦 **작은 풋프린트**: 더 적은 의존성
- 🔧 **유지보수성**: 더 간단한 코드베이스

### 설정 검증

설정이 올바르게 적용되었는지 확인하는 방법:

```java
@Component
public class DynamoDbConfigurationValidator {
    
    @Autowired
    private DynamoDbProperties properties;
    
    @Autowired
    private TableNameResolver tableNameResolver;
    
    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        log.info("DynamoDB 설정:");
        log.info("- Region: {}", properties.getRegion());
        log.info("- Timeout: {}", properties.getTimeout());
        log.info("- Max Retries: {}", properties.getMaxRetries());
        log.info("- Table Name Resolver: {}", tableNameResolver);
        
        // 테이블명 변환 예시
        String resolvedName = tableNameResolver.resolve("users");
        log.info("- 'users' 테이블 → '{}'", resolvedName);
    }
}
```

## 모범 사례

### 오류 처리

```java
public CompletableFuture<User> getUserSafely(String userId) {
    Key key = Key.builder().partitionValue(userId).build();
    
    return dynamoDbService.load(User.class, key, "users")
        .handle((user, throwable) -> {
            if (throwable != null) {
                log.error("사용자 로드 실패: {}", userId, throwable);
                return null; // 또는 기본 사용자
            }
            return user;
        });
}
```

### 배치 작업

```java
// 여러 항목에 대해 배치 작업 선호
public CompletableFuture<Void> saveUsers(List<User> users) {
    // 효율적: 단일 배치 작업
    return dynamoDbService.batchSave(users, "users");
    
    // 피하기: 여러 단일 작업
    // return CompletableFuture.allOf(
    //     users.stream()
    //         .map(user -> dynamoDbService.save(user, "users"))
    //         .toArray(CompletableFuture[]::new)
    // );
}
```

### 비동기 구성

```java
public CompletableFuture<UserSummary> getUserSummary(String userId) {
    return getUserBasicInfo(userId)
        .thenCompose(user -> 
            getUserPreferences(userId)
                .thenApply(prefs -> new UserSummary(user, prefs))
        );
}
```

## 성능 고려사항

### 연결 풀링
- 기본값: 최대 50개 연결
- 애플리케이션 부하에 따라 조정
- 연결 사용률 모니터링

### 배치 크기
- 쓰기 배치: 25개 항목 (DynamoDB 제한)
- 읽기 배치: 100개 항목
- 더 나은 처리량을 위해 배치 사용

### Query vs Scan
- **Query**: 알려진 파티션 키에 사용 (효율적)
- **Scan**: 가급적 사용 자제, 큰 테이블의 경우 페이지네이션 고려

### 비동기 모범 사례
- 요청 스레드에서 `CompletableFuture.join()` 블로킹 금지
- 종속 작업 체이닝에 `thenCompose()` 사용
- 병렬 독립 작업에 `thenCombine()` 사용

## 문제 해결

### 일반적인 문제

1. **테이블을 찾을 수 없음**
   ```
   해결책: 테이블 이름과 리전 구성 확인
   ```

2. **액세스 거부**
   ```
   해결책: DynamoDB 작업에 대한 IAM 권한 확인
   ```

3. **타임아웃 오류**
   ```yaml
   aws:
     dynamodb:
       timeout: PT60S     # 모든 HTTP 연결 타임아웃 증가
       max-retries: 5     # 재시도 횟수 증가
   ```

5. **테이블명 변환 문제**
   ```java
   // TableNameResolver 상태 확인
   @Autowired
   private TableNameResolver resolver;
   
   log.info("Resolver: {}", resolver);
   log.info("'users' resolves to: {}", resolver.resolve("users"));
   ```

4. **LocalStack 연결 문제**
   ```yaml
   aws:
     dynamodb:
       endpoint: http://localhost:4566
   ```

### 디버깅

디버그 로깅 활성화:

```yaml
logging:
  level:
    com.ryuqq.aws.dynamodb: DEBUG
    software.amazon.awssdk.services.dynamodb: DEBUG
```

## API 참조

### DynamoDbService 인터페이스

### TableNameResolver API

| 메서드 | 설명 | 매개변수 | 반환값 |
|--------|-------------|------------|--------|
| `resolve` | 테이블명 변환 | `tableName` | `String` |
| `getTablePrefix` | 현재 prefix 조회 | 없음 | `String` |
| `getTableSuffix` | 현재 suffix 조회 | 없음 | `String` |
| `hasNoTransformation` | 변환 여부 확인 | 없음 | `boolean` |

### DynamoDbService API

| 메서드 | 설명 | 매개변수 | 반환값 |
|--------|-------------|------------|---------|
| `save` | 단일 항목 저장 | `item`, `tableName` | `CompletableFuture<Void>` |
| `load` | 키로 항목 로드 | `itemClass`, `key`, `tableName` | `CompletableFuture<T>` |
| `delete` | 키로 항목 삭제 | `key`, `tableName`, `itemClass` | `CompletableFuture<Void>` |
| `query` | 조건으로 쿼리 | `itemClass`, `queryConditional`, `tableName` | `CompletableFuture<List<T>>` |
| `scan` | 전체 테이블 스캔 | `itemClass`, `tableName` | `CompletableFuture<List<T>>` |
| `batchSave` | 배치 항목 저장 | `items`, `tableName` | `CompletableFuture<Void>` |
| `batchLoad` | 키로 배치 로드 | `itemClass`, `keys`, `tableName` | `CompletableFuture<List<T>>` |
| `transactWrite` | 트랜잭션 쓰기 | `transactItems` | `CompletableFuture<Void>` |
| `getTableNameResolver` | 현재 resolver 조회 | 없음 | `TableNameResolver` |

### 구성 속성

사용 가능한 모든 구성 옵션은 `DynamoDbProperties` 클래스를 참조하세요.

## 라이선스

이 모듈은 AWS Kit 프로젝트의 일부이며 동일한 라이선스 조건을 따릅니다.