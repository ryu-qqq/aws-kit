# AWS S3 Client

AWS SDK v2를 기반으로 구축된 비동기 지원을 제공하는 간편하고 효율적인 Amazon S3 작업을 위한 Spring Boot starter입니다.

## 기능

- **비동기 작업**: 모든 작업이 논블로킹 실행을 위해 `CompletableFuture`를 반환
- **파일 및 바이트 배열 지원**: 파일 또는 바이트 배열의 업로드 및 다운로드
- **대용량 파일 처리**: S3 Transfer Manager를 사용한 대용량 파일의 자동 멀티파트 업로드
- **Presigned URL**: 안전한 파일 액세스를 위한 시간 제한 URL 생성
- **Spring Boot 통합**: 사용자 정의 가능한 속성을 가진 자동 구성
- **오류 처리**: `AwsOperationTemplate`을 통한 통합 오류 처리 및 재시도 메커니즘
- **테스트 지원**: LocalStack을 활용한 내장 통합 테스트

## 빠른 시작

### 1. 의존성 추가

```gradle
dependencies {
    implementation 'com.ryuqq:aws-s3-client:1.0.0-SNAPSHOT'
}
```

### 2. 구성

`application.yml`에서 AWS 자격 증명 및 S3 설정을 구성하세요:

```yaml
aws:
  region: us-east-1
  credentials:
    access-key: your-access-key
    secret-key: your-secret-key
  s3:
    region: us-east-1
    multipart-threshold: 5242880  # 5MB in bytes
    presigned-url-expiry: PT1H    # 1 hour
```

### 3. 사용법

Spring 컴포넌트에서 `S3Service`를 주입하여 사용하세요:

```java
@Service
@RequiredArgsConstructor
public class FileService {
    
    private final S3Service s3Service;
    
    public CompletableFuture<String> uploadFile(String bucketName, String key, Path filePath) {
        return s3Service.uploadFile(bucketName, key, filePath);
    }
}
```

## 핵심 작업

### 파일 업로드

```java
// 파일 시스템에서 파일 업로드
CompletableFuture<String> etag = s3Service.uploadFile("my-bucket", "documents/file.pdf", Paths.get("/path/to/file.pdf"));

// 콘텐츠 타입과 함께 바이트 배열 업로드
byte[] data = "Hello, S3!".getBytes();
CompletableFuture<String> etag = s3Service.uploadBytes("my-bucket", "texts/hello.txt", data, "text/plain");

// 대용량 파일 업로드 (자동 멀티파트 업로드)
CompletableFuture<String> etag = s3Service.uploadLargeFile("my-bucket", "videos/large-video.mp4", Paths.get("/path/to/large-file.mp4"));
```

### 파일 다운로드

```java
// 바이트 배열로 다운로드
CompletableFuture<byte[]> data = s3Service.downloadFile("my-bucket", "documents/file.pdf");

// 특정 파일 경로로 다운로드
CompletableFuture<Void> result = s3Service.downloadToFile("my-bucket", "documents/file.pdf", Paths.get("/local/path/file.pdf"));
```

### 파일 관리

```java
// 접두사가 있는 객체 나열
CompletableFuture<List<String>> objects = s3Service.listObjects("my-bucket", "documents/");

// 객체 삭제
CompletableFuture<Void> result = s3Service.deleteObject("my-bucket", "documents/old-file.pdf");
```

### Presigned URL

```java
// 다운로드를 위한 presigned URL 생성 (1시간 만료)
CompletableFuture<String> url = s3Service.generatePresignedUrl("my-bucket", "documents/file.pdf", Duration.ofHours(1));

// URL 사용
url.thenAccept(presignedUrl -> {
    System.out.println("Download URL: " + presignedUrl);
    // 클라이언트와 직접 다운로드를 위해 이 URL을 공유
});
```

## 구성 속성

| 속성 | 기본값 | 설명 |
|----------|---------|-------------|
| `aws.s3.region` | `us-east-1` | S3 작업을 위한 AWS 리전 |
| `aws.s3.multipart-threshold` | `5242880` (5MB) | 멀티파트 업로드를 위한 파일 크기 임계값 |
| `aws.s3.presigned-url-expiry` | `PT1H` (1시간) | presigned URL의 기본 만료 시간 |

## 멀티파트 업로드 지원

서비스는 S3 Transfer Manager를 통해 대용량 파일에 대해 자동으로 멀티파트 업로드를 사용합니다:

- **자동 감지**: 구성된 임계값보다 큰 파일은 멀티파트 업로드를 사용
- **병렬 업로드**: 더 나은 성능을 위해 여러 부분을 동시에 업로드
- **재개 지원**: 실패한 업로드는 재개 가능 (Transfer Manager가 관리)
- **메모리 효율성**: 전체 파일을 메모리에 로드하지 않고 파일 내용을 스트리밍

```java
// 이것은 5MB보다 큰 파일에 대해 자동으로 멀티파트 업로드를 사용합니다
CompletableFuture<String> result = s3Service.uploadLargeFile("my-bucket", "large-files/video.mp4", filePath);
```

## 오류 처리

모든 작업은 다음을 제공하는 `AwsOperationTemplate`으로 래핑됩니다:

- **자동 재시도**: 일시적 실패에 대한 구성 가능한 재시도 로직
- **회로 차단기**: 연쇄 실패에 대한 보호
- **메트릭**: 작업 타이밍 및 성공/실패 메트릭
- **로깅**: 디버깅 및 모니터링을 위한 구조화된 로깅

```java
// 오류는 CompletableFuture를 통해 전파됩니다
s3Service.uploadFile("my-bucket", "key", filePath)
    .thenAccept(etag -> log.info("Upload successful: {}", etag))
    .exceptionally(throwable -> {
        log.error("Upload failed", throwable);
        return null;
    });
```

## 모범 사례

### 1. 버킷 및 키 명명

```java
// 계층 구조를 위해 슬래시 사용
String key = "user-uploads/2024/01/15/document.pdf";

// 적절한 콘텐츠 타입 감지를 위해 파일 확장자 포함
String key = "documents/report.pdf";
```

### 2. 대용량 파일 처리

```java
// 5MB보다 큰 파일의 경우 더 나은 성능을 위해 uploadLargeFile 사용
Path largeFile = Paths.get("/path/to/large-file.zip");
if (Files.size(largeFile) > 5_242_880) { // 5MB
    s3Service.uploadLargeFile(bucket, key, largeFile);
} else {
    s3Service.uploadFile(bucket, key, largeFile);
}
```

### 3. 콘텐츠 타입 관리

```java
// 바이트 배열 업로드 시 항상 콘텐츠 타입 지정
String contentType = Files.probeContentType(filePath);
s3Service.uploadBytes(bucket, key, bytes, contentType);
```

### 4. 리소스 관리

```java
// 파일 작업에 try-with-resources 사용
try (var inputStream = Files.newInputStream(filePath)) {
    byte[] data = inputStream.readAllBytes();
    s3Service.uploadBytes(bucket, key, data, "application/octet-stream");
}
```

### 5. 비동기 처리

```java
// 작업을 효율적으로 연결
s3Service.uploadFile(bucket, key, filePath)
    .thenCompose(etag -> s3Service.generatePresignedUrl(bucket, key, Duration.ofHours(1)))
    .thenAccept(url -> notificationService.sendDownloadLink(url))
    .exceptionally(throwable -> {
        log.error("Operation failed", throwable);
        return null;
    });
```

## 테스트

모듈은 LocalStack을 사용한 포괄적인 통합 테스트를 포함합니다:

```java
@SpringBootTest
@Testcontainers
class S3ServiceIntegrationTest {
    
    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.S3);
    
    @Test
    void shouldUploadAndDownloadFile() {
        // 테스트 구현
    }
}
```

테스트 실행:

```bash
./gradlew test
```

## 의존성

- AWS SDK for Java v2 (S3, S3 Transfer Manager)
- Spring Boot (자동 구성)
- AWS SDK Commons (공유 컴포넌트)

## 다른 모듈과의 통합

이 모듈은 다른 awskit 모듈과 원활하게 통합됩니다:

- **aws-sdk-commons**: 공유 AWS 구성 및 유틸리티 제공
- **aws-lambda-client**: Lambda 함수에서 S3 작업을 트리거하는 데 사용 가능
- **aws-dynamodb-client**: DynamoDB에 S3 객체 메타데이터 저장

## 성능 고려사항

- **비동기 작업**: 모든 메소드가 논블로킹 실행을 위해 `CompletableFuture` 반환
- **Transfer Manager**: 병렬 처리로 업로드/다운로드 자동 최적화
- **연결 풀링**: 더 나은 성능을 위해 HTTP 연결 재사용
- **메모리 효율성**: 메모리에 로드하지 않고 대용량 파일 스트리밍

## 보안 참고사항

- **Presigned URL**: 짧은 만료 시간 사용 및 액세스 패턴 검증
- **IAM 권한**: S3 버킷 액세스에 대해 최소 권한 원칙 따름
- **암호화**: 민감한 데이터에 대해 S3 서버 측 암호화 사용 고려
- **액세스 로깅**: 감사 추적을 위해 S3 액세스 로깅 활성화

## 라이선스

이 프로젝트는 awskit 라이브러리의 일부이며 동일한 라이선스 조건을 따릅니다.