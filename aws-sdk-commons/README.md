# AWS SDK Commons

AWS SDK v2를 위한 공통 설정 및 유틸리티를 제공하는 Spring Boot 라이브러리 모듈입니다.

## 개요

`aws-sdk-commons` 모듈은 awskit 프레임워크의 모든 AWS 서비스 통합을 위한 기반 레이어 역할을 합니다. 리전 설정, 자격 증명 제공자, HTTP 클라이언트 설정, 공통 설정 속성을 포함한 필수 AWS SDK 컴포넌트를 제공합니다. 이 모듈은 의존성이 없고 가벼우며, 다른 AWS 서비스 모듈에서 필요한 핵심 컴포넌트만 포함하도록 설계되었습니다.

## 주요 기능

- **공유 AWS 설정**: Spring Boot 자동 설정을 통한 중앙집중식 AWS 속성 관리
- **자격 증명 관리**: AWS SDK의 기본 자격 증명 체인을 사용한 자동 AWS 자격 증명 제공자 설정
- **HTTP 클라이언트 설정**: 타임아웃 및 연결 설정이 사전 구성된 Apache HTTP 클라이언트
- **리전 관리**: 합리적인 기본값을 가진 간단한 리전 설정
- **클라이언트 재정의 설정**: API 타임아웃 및 재시도 동작을 위한 공통 클라이언트 설정
- **Spring Boot 통합**: 조건부 빈 생성을 통한 원활한 자동 설정

## 사용법

### 의존성 추가

서비스별 AWS 모듈에 이 모듈을 의존성으로 추가합니다:

```gradle
dependencies {
    implementation 'com.github.yourusername.awskit:aws-sdk-commons:1.0.0'
}
```

### 설정

`application.yml` 또는 `application.properties`에서 AWS 속성을 설정합니다:

```yaml
aws:
  region: ap-northeast-2
  timeout: PT30S
  max-retries: 3
  endpoint: # 선택사항: LocalStack 또는 커스텀 엔드포인트용
  access-key: # 선택사항: 보통 환경변수나 IAM 역할을 통해 제공
  secret-key: # 선택사항: 보통 환경변수나 IAM 역할을 통해 제공
```

### 기본 사용 예제

모듈은 AWS 서비스 클라이언트에 주입할 수 있는 자동 구성된 빈을 제공합니다:

```java
@Service
public class MyAwsService {
    
    private final Region region;
    private final AwsCredentialsProvider credentialsProvider;
    private final SdkHttpClient httpClient;
    private final ClientOverrideConfiguration clientConfig;
    
    public MyAwsService(Region region, 
                       AwsCredentialsProvider credentialsProvider,
                       SdkHttpClient httpClient,
                       ClientOverrideConfiguration clientConfig) {
        this.region = region;
        this.credentialsProvider = credentialsProvider;
        this.httpClient = httpClient;
        this.clientConfig = clientConfig;
    }
    
    public void createAwsClient() {
        // 예제: 공유 설정으로 AWS 서비스 클라이언트 생성
        var client = S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .httpClient(httpClient)
                .overrideConfiguration(clientConfig)
                .build();
    }
}
```

## 설정 속성

| 속성 | 타입 | 기본값 | 설명 |
|----------|------|---------|-------------|
| `aws.region` | String | `us-west-2` | 모든 서비스 클라이언트를 위한 AWS 리전 |
| `aws.timeout` | Duration | `PT30S` | 연결 및 API 호출 타임아웃 (30초) |
| `aws.max-retries` | Integer | `3` | 최대 재시도 횟수 |
| `aws.endpoint` | String | `null` | 커스텀 엔드포인트 URL (LocalStack 테스트에 유용) |
| `aws.access-key` | String | `null` | AWS 액세스 키 (선택사항, 기본 자격 증명 체인 사용) |
| `aws.secret-key` | String | `null` | AWS 시크릿 키 (선택사항, 기본 자격 증명 체인 사용) |

### 타임아웃 설정

타임아웃 속성은 ISO 8601 기간 형식을 허용합니다:
- `PT30S` - 30초
- `PT2M` - 2분  
- `PT1M30S` - 1분 30초

## 자동 설정

이 모듈은 다음 빈을 생성하는 Spring Boot 자동 설정을 제공합니다:

- **`Region`**: 속성에서 구성된 AWS 리전
- **`AwsCredentialsProvider`**: 기본 AWS 자격 증명 제공자 (AWS 자격 증명 체인을 따름)
- **`SdkHttpClient`**: 구성된 타임아웃을 가진 Apache HTTP 클라이언트
- **`ClientOverrideConfiguration`**: API 호출을 위한 공통 클라이언트 설정

모든 빈은 `@ConditionalOnMissingBean`으로 생성되어 필요시 커스텀 구현으로 재정의할 수 있습니다.

## 의존성

### 필수 의존성

- **Spring Boot Starter**: 핵심 Spring Boot 기능
- **AWS SDK Core**: 필수 AWS SDK 컴포넌트
- **AWS SDK Auth**: 인증 및 자격 증명 관리
- **AWS SDK Regions**: 리전 관리
- **AWS SDK Apache Client**: HTTP 클라이언트 구현
- **Jackson JSR310**: AWS API 응답을 위한 시간/날짜 처리

### 버전 정보

- **Java**: 21+
- **Spring Boot**: 3.3.4
- **AWS SDK**: 2.28.11

## 다른 모듈과의 통합

이 commons 모듈은 awskit 프레임워크의 다른 AWS 서비스별 모듈에서 사용되도록 설계되었습니다:

- `aws-s3-client`
- `aws-dynamodb-client`
- `aws-sqs-client`
- `aws-lambda-client`

각 서비스 모듈은 이를 의존성으로 포함하고 공유 설정과 빈을 활용합니다.

## Spring Boot 자동 설정

모듈은 Spring Boot의 자동 검색을 활성화하기 위해 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`를 포함합니다. 수동 설정이 필요하지 않습니다 - 의존성만 포함하면 빈을 사용할 수 있습니다.

## 모범 사례

1. **자격 증명**: 설정 파일에 `access-key`와 `secret-key`를 하드코딩하지 마세요. 대신 환경 변수, IAM 역할 또는 AWS 자격 증명 파일을 사용하세요.

2. **환경별 설정**: Spring 프로파일을 사용하여 다른 설정을 관리하세요:
   ```yaml
   # application-dev.yml
   aws:
     endpoint: http://localhost:4566  # LocalStack
   
   # application-prod.yml  
   aws:
     region: ap-northeast-2
     timeout: PT60S
   ```

3. **빈 재정의**: 커스텀 동작이 필요한 경우, 자체 빈 구현을 제공하세요:
   ```java
   @Bean
   @Primary
   public AwsCredentialsProvider customCredentialsProvider() {
       return ProfileCredentialsProvider.create("my-profile");
   }
   ```

## 기여

이 모듈은 프로젝트의 코딩 표준을 따르며 최소한의 의존성으로 가볍게 유지되어야 합니다. 변경사항은 역호환성을 유지하고 충분히 테스트되어야 합니다.