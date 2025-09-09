# AWS SDK v2 표준 모듈

Spring Boot 3.3 + Java 21 환경에서 사용할 수 있는 팀용 AWS SDK v2 기반 표준 모듈입니다.

## 🏗️ 프로젝트 구조

```
awskit/
├── aws-sdk-commons/          # AWS SDK 공통 설정 및 유틸리티
├── aws-sqs-client/          # Amazon SQS 클라이언트
├── aws-dynamodb-client/     # Amazon DynamoDB 클라이언트  
├── aws-s3-client/           # Amazon S3 클라이언트
├── aws-lambda-client/       # AWS Lambda 클라이언트
└── example-app/             # 사용 예제 애플리케이션
```

## 🚀 주요 특징

- **Spring Boot 3.3** + **Java 21** 최신 스택
- **AWS SDK v2** 비동기 클라이언트 활용
- **멀티 모듈 구조**로 필요한 서비스만 선택적 사용
- **환경별 설정** 지원 (dev, staging, prod)
- **표준화된 에러 핸들링** 및 **구조화된 로깅**
- **메트릭 수집** 및 **모니터링** 지원
- **LocalStack** 연동으로 로컬 개발 환경 지원

## 📦 모듈 소개

### aws-sdk-commons
- AWS SDK 공통 설정 및 자동 구성
- 환경별 프로필 관리 (dev/staging/prod)
- 표준화된 예외 처리 및 로깅
- 메트릭 수집 및 성능 모니터링
- 재시도 정책 및 서킷 브레이커

### aws-sqs-client
- SQS 메시지 송수신 (단일/배치)
- FIFO 큐 지원
- Long Polling 지원
- Dead Letter Queue 처리
- 메시지 직렬화/역직렬화

### aws-dynamodb-client
- DynamoDB Enhanced Client 활용
- 기본 CRUD 작업 지원
- 쿼리 및 스캔 기능
- 배치 처리 및 트랜잭션
- 조건부 업데이트

### aws-s3-client
- S3 객체 업로드/다운로드
- 멀티파트 업로드
- Pre-signed URL 생성
- 메타데이터 관리

### aws-lambda-client
- Lambda 함수 동기/비동기 호출
- 페이로드 직렬화
- 에러 처리 및 재시도

## 🔧 사용 방법

### 1. 의존성 추가

```gradle
dependencies {
    // 필요한 모듈만 선택적으로 추가
    implementation project(':aws-sdk-commons')    // 필수
    implementation project(':aws-sqs-client')     // SQS 사용시
    implementation project(':aws-dynamodb-client') // DynamoDB 사용시
    implementation project(':aws-s3-client')      // S3 사용시
    implementation project(':aws-lambda-client')  // Lambda 사용시
}
```

### 2. 설정 파일 구성

```yaml
# application.yml
aws:
  region: us-west-2
  credentials:
    profile-name: default
    use-instance-profile: true
  client-config:
    connection-timeout: PT10S
    socket-timeout: PT30S
    max-concurrency: 50
  retry-policy:
    max-retries: 3
    base-delay: PT0.1S
```

### 3. 클라이언트 사용 예제

#### SQS 사용
```java
@Autowired
private SqsClient sqsClient;

public void sendMessage() {
    sqsClient.sendMessage("queue-url", "message body")
        .thenAccept(messageId -> 
            log.info("Message sent: {}", messageId));
}
```

#### DynamoDB 사용  
```java
@Autowired
private DynamoDbClient dynamoDbClient;

public void saveItem() {
    MyItem item = new MyItem("pk", "sk", "data");
    dynamoDbClient.putItem("table-name", item, MyItem.class)
        .thenRun(() -> log.info("Item saved"));
}
```

#### S3 사용
```java
@Autowired
private S3Client s3Client;

public void uploadFile() {
    byte[] content = "file content".getBytes();
    s3Client.uploadObject("bucket-name", "key", content)
        .thenAccept(etag -> log.info("File uploaded: {}", etag));
}
```

#### Lambda 사용
```java
@Autowired
private LambdaClient lambdaClient;

public void invokeFunction() {
    lambdaClient.invokeFunction("function-name", "{\"key\":\"value\"}")
        .thenAccept(response -> log.info("Response: {}", response));
}
```

## 🌱 개발 환경 설정

### LocalStack 사용
```bash
# LocalStack 시작
docker run --rm -it -p 4566:4566 localstack/localstack

# 개발 프로필로 애플리케이션 실행
./gradlew :example-app:bootRun --args='--spring.profiles.active=dev'
```

### 환경별 프로필
- **dev**: LocalStack 연동, 상세 로깅
- **staging**: 스테이징 환경 설정
- **prod**: 운영 환경 최적화 설정

## 🧪 예제 애플리케이션

`example-app` 모듈에서 각 클라이언트의 사용법을 확인할 수 있습니다.

```bash
# 예제 앱 실행
./gradlew :example-app:bootRun

# Swagger UI 접속
http://localhost:8080/swagger-ui.html

# API 테스트
curl -X POST "http://localhost:8080/api/aws/sqs/send" \
  -H "Content-Type: application/json" \
  -d "Hello SQS"
```

## 📊 모니터링

### Actuator Endpoints
- **Health**: `/actuator/health`
- **Metrics**: `/actuator/metrics`
- **Prometheus**: `/actuator/prometheus`

### 사용 가능한 메트릭
- `aws.api.call.duration`: API 호출 지연시간
- `aws.api.call.success`: API 호출 성공 수
- `aws.api.call.error`: API 호출 에러 수
- `aws.api.call.retry`: API 호출 재시도 수

## 🔒 보안 고려사항

- AWS 자격증명은 환경변수 또는 IAM Role 사용 권장
- 민감한 정보는 로깅에서 자동 마스킹
- 모든 API 호출에 대한 상세 감사 로깅

## 🛠️ 빌드 및 테스트

```bash
# 전체 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :aws-sqs-client:build

# 테스트 실행
./gradlew test

# 통합 테스트 (LocalStack 필요)
./gradlew integrationTest
```

## 📝 라이센스

MIT License

## 🤝 기여 가이드

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## 📞 지원

이슈나 질문이 있으시면 GitHub Issues를 통해 문의해 주세요.