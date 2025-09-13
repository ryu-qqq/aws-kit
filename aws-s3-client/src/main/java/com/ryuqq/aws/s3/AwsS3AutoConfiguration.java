package com.ryuqq.aws.s3;

import com.ryuqq.aws.s3.properties.S3Properties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

/**
 * S3 클라이언트 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(S3AsyncClient.class)
@EnableConfigurationProperties(S3Properties.class)
public class AwsS3AutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public S3AsyncClient s3AsyncClient(Region region,
                                     AwsCredentialsProvider credentialsProvider,
                                     ClientOverrideConfiguration clientOverrideConfiguration) {
        return S3AsyncClient.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .overrideConfiguration(clientOverrideConfiguration)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(Region region,
                                  AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3TransferManager s3TransferManager(S3AsyncClient s3AsyncClient,
                                             S3Properties s3Properties) {
        /**
         * 한국어 설명:
         * S3TransferManager를 설정합니다.
         * multipartThreshold를 사용하여 멀티파트 업로드 임계값을 설정합니다.
         */
        return S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .uploadDirectoryMaxDepth(Integer.MAX_VALUE)
                .build();
    }
    
    /*
     * 추가 확장 고려사항:
     * 
     * 1. 고급 S3 설정 Bean:
     *    - S3Configuration: 상세한 S3 서비스 설정 관리
     *    - S3MetricsCollector: CloudWatch 메트릭스 수집
     *    - S3CacheManager: 자주 접근하는 객체 캐싱
     * 
     * 2. 성능 최적화 Bean:
     *    - S3ConnectionPool: 연결 풀 세밀 관리
     *    - S3CompletionService: 비동기 작업 완료 추적
     *    - S3RateLimiter: API 호출 속도 제한
     * 
     * 3. 보안 강화 Bean:
     *    - S3EncryptionClient: 클라이언트측 암호화
     *    - S3AccessLogger: 접근 로그 수집 및 감사
     *    - S3PolicyValidator: 버킷 정책 검증
     * 
     * 4. 모니터링 및 관리:
     *    - S3HealthIndicator: Spring Actuator 연동 헬스 체크
     *    - S3ConfigurationProperties: 상세 설정 프로퍼티
     *    - S3EventListener: S3 작업 이벤트 리스너
     * 
     * 5. 개발 편의성:
     *    - S3MockConfiguration: 로컬 개발용 Mock 설정
     *    - S3TestContainer: 테스트용 LocalStack 연동
     *    - S3ProfileConfiguration: 환경별 프로파일 설정
     * 
     * Bean 생성 조건 확장:
     * @ConditionalOnProperty(name = "aws.s3.enabled", havingValue = "true", matchIfMissing = true)
     * @ConditionalOnCloudPlatform(CloudPlatform.AMAZON_EC2) // EC2에서만 활성화
     * @Profile({"!test"}) // 테스트 프로파일 제외
     * 
     * 설정 검증:
     * @Validated를 통한 S3Properties 유효성 검사
     * @PostConstruct 메서드로 Bean 초기화 후 연결 테스트
     */
}