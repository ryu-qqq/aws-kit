package com.ryuqq.aws.s3.integration;

import com.ryuqq.aws.s3.service.S3Service;
import com.ryuqq.aws.s3.types.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.S3;

/**
 * S3 고급 기능 통합 테스트 (LocalStack 사용)
 * 
 * 한국어 설명:
 * LocalStack을 사용하여 실제 S3와 유사한 환경에서 새로운 S3 기능들을 테스트합니다.
 * 메타데이터 조회, 객체 복사, 배치 삭제, 태그 관리, 진행률 추적 등을 검증합니다.
 * 
 * 실행 조건:
 * - Docker가 설치되어 있어야 함
 * - 네트워크 연결이 가능해야 함
 * - INTEGRATION_TEST=true 환경변수 설정 시에만 실행
 */
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
@DisplayName("S3 고급 기능 통합 테스트")
class S3AdvancedIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.3"))
            .withServices(S3)
            .withEnv("DEBUG", "1");

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint", localstack::getEndpoint);
        registry.add("aws.region", () -> "us-east-1");
        registry.add("aws.credentials.access-key-id", () -> "test");
        registry.add("aws.credentials.secret-access-key", () -> "test");
    }

    @Autowired
    private S3Service s3Service;

    @Autowired
    private S3AsyncClient s3AsyncClient;

    private static final String TEST_BUCKET = "integration-test-bucket";
    private static final String METADATA_TEST_BUCKET = "metadata-test-bucket";
    
    private Path tempTestFile;
    private Path tempLargeFile;

    @BeforeAll
    static void setUpBuckets(@Autowired S3AsyncClient s3AsyncClient) throws Exception {
        // 테스트용 버킷 생성
        s3AsyncClient.createBucket(CreateBucketRequest.builder().bucket(TEST_BUCKET).build()).join();
        s3AsyncClient.createBucket(CreateBucketRequest.builder().bucket(METADATA_TEST_BUCKET).build()).join();
    }

    @BeforeEach
    void setUpFiles() throws IOException {
        // 작은 테스트 파일 생성
        tempTestFile = Files.createTempFile("s3-test", ".txt");
        Files.write(tempTestFile, "Integration test content for S3 operations".getBytes());

        // 큰 테스트 파일 생성 (진행률 추적 테스트용)
        tempLargeFile = Files.createTempFile("s3-large-test", ".dat");
        byte[] largeData = new byte[1024 * 1024]; // 1MB
        Arrays.fill(largeData, (byte) 'A');
        Files.write(tempLargeFile, largeData);
    }

    @AfterEach
    void cleanupFiles() throws IOException {
        if (tempTestFile != null && Files.exists(tempTestFile)) {
            Files.delete(tempTestFile);
        }
        if (tempLargeFile != null && Files.exists(tempLargeFile)) {
            Files.delete(tempLargeFile);
        }
    }

    @Test
    @Order(1)
    @DisplayName("메타데이터 조회 통합 테스트")
    void headObject_IntegrationTest() throws Exception {
        // Given
        String key = "metadata-test/document.txt";
        Map<String, String> metadata = new HashMap<>();
        metadata.put("author", "integration-tester");
        metadata.put("document-type", "test");
        metadata.put("한글키", "한글값"); // 한글 메타데이터 테스트

        // 메타데이터와 함께 파일 업로드
        String etag = s3Service.uploadFileWithMetadata(
                METADATA_TEST_BUCKET, key, tempTestFile, metadata, S3StorageClass.STANDARD_IA)
                .join();

        assertThat(etag).isNotNull();

        // When
        S3Metadata result = s3Service.headObject(METADATA_TEST_BUCKET, key).join();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContentLength()).isGreaterThan(0);
        assertThat(result.getContentType()).isNotBlank();
        assertThat(result.getETag()).isEqualTo(etag);
        assertThat(result.getLastModified()).isNotNull();
        assertThat(result.getStorageClass()).isEqualTo("STANDARD_IA");
        
        // 사용자 메타데이터 검증
        assertThat(result.getUserMetadata()).containsEntry("author", "integration-tester");
        assertThat(result.getUserMetadata()).containsEntry("document-type", "test");
        assertThat(result.getUserMetadata()).containsEntry("한글키", "한글값");
    }

    @Test
    @Order(2)
    @DisplayName("존재하지 않는 객체 메타데이터 조회 시 예외 발생")
    void headObject_ShouldThrowException_WhenObjectNotExists() {
        // Given
        String nonExistentKey = "non-existent/file.txt";

        // When & Then
        CompletableFuture<S3Metadata> future = s3Service.headObject(TEST_BUCKET, nonExistentKey);
        
        assertThatThrownBy(future::join)
                .hasCauseInstanceOf(NoSuchKeyException.class);
    }

    @Test
    @Order(3)
    @DisplayName("객체 복사 통합 테스트")
    void copyObject_IntegrationTest() throws Exception {
        // Given
        String sourceKey = "copy-test/source.txt";
        String destKey = "copy-test/destination.txt";

        // 원본 파일 업로드
        String originalEtag = s3Service.uploadFile(TEST_BUCKET, sourceKey, tempTestFile).join();
        assertThat(originalEtag).isNotNull();

        // When
        String copyEtag = s3Service.copyObject(TEST_BUCKET, sourceKey, TEST_BUCKET, destKey).join();

        // Then
        assertThat(copyEtag).isNotNull();
        assertThat(copyEtag).isEqualTo(originalEtag); // 동일한 내용이므로 ETag가 같아야 함

        // 복사된 파일 검증
        byte[] copiedContent = s3Service.downloadFile(TEST_BUCKET, destKey).join();
        byte[] originalContent = Files.readAllBytes(tempTestFile);
        
        assertThat(copiedContent).isEqualTo(originalContent);
    }

    @Test
    @Order(4)
    @DisplayName("메타데이터 변경하여 복사")
    void copyObjectWithMetadata_IntegrationTest() throws Exception {
        // Given
        String sourceKey = "metadata-copy-test/source.pdf";
        String destKey = "metadata-copy-test/destination.pdf";

        // 원본 파일 업로드 (STANDARD 스토리지)
        String originalEtag = s3Service.uploadFileWithMetadata(
                TEST_BUCKET, sourceKey, tempTestFile, 
                Map.of("version", "1.0", "status", "draft"), 
                S3StorageClass.STANDARD
        ).join();

        // When - 메타데이터와 스토리지 클래스 변경하여 복사
        Map<String, String> newMetadata = new HashMap<>();
        newMetadata.put("version", "2.0");
        newMetadata.put("status", "published");
        newMetadata.put("processed", "true");

        String copyEtag = s3Service.copyObjectWithMetadata(
                TEST_BUCKET, sourceKey, 
                TEST_BUCKET, destKey, 
                newMetadata, S3StorageClass.GLACIER
        ).join();

        // Then
        assertThat(copyEtag).isNotNull();

        // 복사된 파일의 메타데이터 검증
        S3Metadata copiedMetadata = s3Service.headObject(TEST_BUCKET, destKey).join();
        assertThat(copiedMetadata.getUserMetadata()).containsEntry("version", "2.0");
        assertThat(copiedMetadata.getUserMetadata()).containsEntry("status", "published");
        assertThat(copiedMetadata.getUserMetadata()).containsEntry("processed", "true");
        assertThat(copiedMetadata.getStorageClass()).isEqualTo("GLACIER");

        // 원본 파일의 메타데이터는 변경되지 않았는지 확인
        S3Metadata originalMetadata = s3Service.headObject(TEST_BUCKET, sourceKey).join();
        assertThat(originalMetadata.getUserMetadata()).containsEntry("version", "1.0");
        assertThat(originalMetadata.getUserMetadata()).containsEntry("status", "draft");
        assertThat(originalMetadata.getStorageClass()).isEqualTo("STANDARD");
    }

    @Test
    @Order(5)
    @DisplayName("배치 삭제 통합 테스트")
    void deleteObjects_IntegrationTest() throws Exception {
        // Given
        List<String> keysToUpload = Arrays.asList(
                "batch-delete/file1.txt",
                "batch-delete/file2.txt", 
                "batch-delete/file3.txt",
                "batch-delete/file4.txt",
                "batch-delete/file5.txt"
        );

        // 여러 파일 업로드
        List<CompletableFuture<String>> uploadFutures = keysToUpload.stream()
                .map(key -> s3Service.uploadFile(TEST_BUCKET, key, tempTestFile))
                .toList();

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();

        // 업로드된 파일들이 존재하는지 확인
        for (String key : keysToUpload) {
            S3Metadata metadata = s3Service.headObject(TEST_BUCKET, key).join();
            assertThat(metadata).isNotNull();
        }

        // When
        List<String> failedKeys = s3Service.deleteObjects(TEST_BUCKET, keysToUpload).join();

        // Then
        assertThat(failedKeys).isEmpty(); // 모든 삭제가 성공해야 함

        // 삭제된 파일들이 존재하지 않는지 확인
        for (String key : keysToUpload) {
            CompletableFuture<S3Metadata> future = s3Service.headObject(TEST_BUCKET, key);
            assertThatThrownBy(future::join).hasCauseInstanceOf(NoSuchKeyException.class);
        }
    }

    @Test
    @Order(6)
    @DisplayName("객체 태그 관리 통합 테스트")
    void objectTagging_IntegrationTest() throws Exception {
        // Given
        String key = "tag-test/tagged-file.jpg";

        // 파일 업로드
        String etag = s3Service.uploadFile(TEST_BUCKET, key, tempTestFile).join();
        assertThat(etag).isNotNull();

        // When - 태그 설정
        S3Tag tags = S3Tag.builder()
                .tags(Map.of(
                        "Environment", "test",
                        "Project", "awskit",
                        "Owner", "integration-test",
                        "한글태그", "한글값"
                ))
                .build();

        s3Service.putObjectTags(TEST_BUCKET, key, tags).join();

        // Then - 태그 조회 및 검증
        S3Tag retrievedTags = s3Service.getObjectTags(TEST_BUCKET, key).join();
        assertThat(retrievedTags).isNotNull();
        assertThat(retrievedTags.getTagValue("Environment")).isEqualTo("test");
        assertThat(retrievedTags.getTagValue("Project")).isEqualTo("awskit");
        assertThat(retrievedTags.getTagValue("Owner")).isEqualTo("integration-test");
        assertThat(retrievedTags.getTagValue("한글태그")).isEqualTo("한글값");
        assertThat(retrievedTags.getTagCount()).isEqualTo(4);

        // 태그 삭제
        s3Service.deleteObjectTags(TEST_BUCKET, key).join();

        // 태그가 삭제되었는지 확인
        S3Tag emptyTags = s3Service.getObjectTags(TEST_BUCKET, key).join();
        assertThat(emptyTags.getTags()).isEmpty();
    }

    @Test
    @Order(7)
    @DisplayName("진행률 추적 업로드 통합 테스트")
    void uploadWithProgress_IntegrationTest() throws Exception {
        // Given
        String key = "progress-test/large-file.dat";
        AtomicInteger progressCallCount = new AtomicInteger(0);
        AtomicLong maxBytesTransferred = new AtomicLong(0);
        AtomicLong totalBytes = new AtomicLong(0);

        S3ProgressListener progressListener = event -> {
            progressCallCount.incrementAndGet();
            maxBytesTransferred.updateAndGet(current -> Math.max(current, event.getBytesTransferred()));
            totalBytes.set(event.getTotalBytes());

            // 이벤트 검증
            assertThat(event.getBucket()).isEqualTo(TEST_BUCKET);
            assertThat(event.getKey()).isEqualTo(key);
            assertThat(event.getTransferType()).isEqualTo(S3ProgressListener.TransferType.UPLOAD);
            assertThat(event.getProgressPercentage()).isBetween(0.0, 100.0);
        };

        // When
        String etag = s3Service.uploadFileWithProgress(TEST_BUCKET, key, tempLargeFile, progressListener).join();

        // Then
        assertThat(etag).isNotNull();
        assertThat(progressCallCount.get()).isGreaterThan(0); // 적어도 한 번은 호출
        assertThat(totalBytes.get()).isEqualTo(1024 * 1024); // 1MB
        assertThat(maxBytesTransferred.get()).isEqualTo(1024 * 1024); // 완료 시 전체 크기

        // 업로드된 파일 검증
        byte[] downloadedContent = s3Service.downloadFile(TEST_BUCKET, key).join();
        byte[] originalContent = Files.readAllBytes(tempLargeFile);
        assertThat(downloadedContent).isEqualTo(originalContent);
    }

    @Test
    @Order(8)
    @DisplayName("진행률 추적 다운로드 통합 테스트")
    void downloadWithProgress_IntegrationTest() throws Exception {
        // Given
        String key = "download-progress-test/large-download.dat";

        // 먼저 큰 파일 업로드
        String etag = s3Service.uploadFile(TEST_BUCKET, key, tempLargeFile).join();
        assertThat(etag).isNotNull();

        // 다운로드 진행률 추적 설정
        AtomicInteger progressCallCount = new AtomicInteger(0);
        AtomicLong totalDownloaded = new AtomicLong(0);

        S3ProgressListener progressListener = event -> {
            progressCallCount.incrementAndGet();
            totalDownloaded.set(event.getBytesTransferred());

            assertThat(event.getBucket()).isEqualTo(TEST_BUCKET);
            assertThat(event.getKey()).isEqualTo(key);
            assertThat(event.getTransferType()).isEqualTo(S3ProgressListener.TransferType.DOWNLOAD);
        };

        Path downloadTarget = Files.createTempFile("s3-download-test", ".dat");

        try {
            // When
            s3Service.downloadToFileWithProgress(TEST_BUCKET, key, downloadTarget, progressListener).join();

            // Then
            assertThat(progressCallCount.get()).isGreaterThan(0);
            assertThat(totalDownloaded.get()).isEqualTo(1024 * 1024);

            // 다운로드된 파일 내용 검증
            byte[] downloadedContent = Files.readAllBytes(downloadTarget);
            byte[] originalContent = Files.readAllBytes(tempLargeFile);
            assertThat(downloadedContent).isEqualTo(originalContent);

        } finally {
            Files.deleteIfExists(downloadTarget);
        }
    }

    @Test
    @Order(9)
    @DisplayName("S3Tag 빌더 메서드 통합 테스트")
    void s3TagBuilderMethods_IntegrationTest() throws Exception {
        // Given
        String key = "tag-builder-test/enterprise-file.log";

        String etag = s3Service.uploadFile(TEST_BUCKET, key, tempTestFile).join();
        assertThat(etag).isNotNull();

        // When - 빌더 메서드를 사용하여 태그 생성
        S3Tag enterpriseTags = S3Tag.builder()
                .build()
                .withEnvironment("production")
                .withProject("enterprise-system")
                .withOwner("devops-team")
                .withCostCenter("COST-001")
                .addTag("Backup", "enabled")
                .addTag("Retention", "7years");

        s3Service.putObjectTags(TEST_BUCKET, key, enterpriseTags).join();

        // Then
        S3Tag retrievedTags = s3Service.getObjectTags(TEST_BUCKET, key).join();
        
        assertThat(retrievedTags.getTagValue("Environment")).isEqualTo("production");
        assertThat(retrievedTags.getTagValue("Project")).isEqualTo("enterprise-system");
        assertThat(retrievedTags.getTagValue("Owner")).isEqualTo("devops-team");
        assertThat(retrievedTags.getTagValue("CostCenter")).isEqualTo("COST-001");
        assertThat(retrievedTags.getTagValue("Backup")).isEqualTo("enabled");
        assertThat(retrievedTags.getTagValue("Retention")).isEqualTo("7years");
        assertThat(retrievedTags.getTagCount()).isEqualTo(6);
    }

    @Test
    @Order(10)
    @DisplayName("크로스 버킷 복사 통합 테스트")
    void crossBucketCopy_IntegrationTest() throws Exception {
        // Given
        String sourceKey = "cross-bucket/source-file.txt";
        String destKey = "cross-bucket/dest-file.txt";

        // 원본 버킷에 파일 업로드
        String originalEtag = s3Service.uploadFile(TEST_BUCKET, sourceKey, tempTestFile).join();
        assertThat(originalEtag).isNotNull();

        // When - 다른 버킷으로 복사
        String copyEtag = s3Service.copyObject(TEST_BUCKET, sourceKey, METADATA_TEST_BUCKET, destKey).join();

        // Then
        assertThat(copyEtag).isNotNull();
        assertThat(copyEtag).isEqualTo(originalEtag);

        // 복사된 파일이 대상 버킷에 존재하는지 확인
        S3Metadata copiedMetadata = s3Service.headObject(METADATA_TEST_BUCKET, destKey).join();
        assertThat(copiedMetadata).isNotNull();
        assertThat(copiedMetadata.getETag()).isEqualTo(originalEtag);

        // 내용 검증
        byte[] copiedContent = s3Service.downloadFile(METADATA_TEST_BUCKET, destKey).join();
        byte[] originalContent = Files.readAllBytes(tempTestFile);
        assertThat(copiedContent).isEqualTo(originalContent);
    }

    @Test
    @Order(11)
    @DisplayName("Presigned URL 생성 및 검증 통합 테스트")
    void presignedUrl_IntegrationTest() throws Exception {
        // Given
        String key = "presigned-test/secure-document.pdf";

        String etag = s3Service.uploadFile(TEST_BUCKET, key, tempTestFile).join();
        assertThat(etag).isNotNull();

        // When
        Duration expiration = Duration.ofMinutes(30);
        String presignedUrl = s3Service.generatePresignedUrl(TEST_BUCKET, key, expiration).join();

        // Then
        assertThat(presignedUrl).isNotNull();
        assertThat(presignedUrl).startsWith("http");
        assertThat(presignedUrl).contains(TEST_BUCKET);
        assertThat(presignedUrl).contains(key);
        assertThat(presignedUrl).contains("X-Amz-Expires");
        assertThat(presignedUrl).contains("X-Amz-Signature");
        
        // URL 형식 검증
        assertThat(presignedUrl).matches(".*X-Amz-Expires=\\d+.*");
    }

    @Test
    @Order(12)
    @DisplayName("대용량 배치 삭제 통합 테스트 (100개 객체)")
    void largeBatchDelete_IntegrationTest() throws Exception {
        // Given - 100개 파일 업로드
        List<String> largeKeyList = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            largeKeyList.add(String.format("large-batch-delete/file-%03d.txt", i));
        }

        // 병렬 업로드
        List<CompletableFuture<String>> uploadFutures = largeKeyList.parallelStream()
                .map(key -> s3Service.uploadFile(TEST_BUCKET, key, tempTestFile))
                .toList();

        CompletableFuture.allOf(uploadFutures.toArray(new CompletableFuture[0])).join();

        // When
        List<String> failedKeys = s3Service.deleteObjects(TEST_BUCKET, largeKeyList).join();

        // Then
        assertThat(failedKeys).isEmpty();

        // 샘플링으로 삭제 확인 (모든 파일 확인은 시간이 오래 걸림)
        List<String> sampleKeys = Arrays.asList(
                "large-batch-delete/file-001.txt",
                "large-batch-delete/file-050.txt", 
                "large-batch-delete/file-100.txt"
        );

        for (String key : sampleKeys) {
            CompletableFuture<S3Metadata> future = s3Service.headObject(TEST_BUCKET, key);
            assertThatThrownBy(future::join).hasCauseInstanceOf(NoSuchKeyException.class);
        }
    }

    @Test
    @Order(13)
    @DisplayName("스토리지 클래스 전환 통합 테스트")
    void storageClassTransition_IntegrationTest() throws Exception {
        // Given
        String key = "storage-class-test/archive-file.log";

        // STANDARD로 업로드
        String etag = s3Service.uploadFileWithMetadata(
                TEST_BUCKET, key, tempTestFile, 
                Map.of("content", "log-data"), 
                S3StorageClass.STANDARD
        ).join();

        S3Metadata originalMetadata = s3Service.headObject(TEST_BUCKET, key).join();
        assertThat(originalMetadata.getStorageClass()).isEqualTo("STANDARD");

        // When - GLACIER로 복사 (스토리지 클래스 변경)
        String newKey = "storage-class-test/archive-file-glacier.log";
        String copyEtag = s3Service.copyObjectWithMetadata(
                TEST_BUCKET, key,
                TEST_BUCKET, newKey,
                null, // 메타데이터 유지
                S3StorageClass.GLACIER
        ).join();

        // Then
        assertThat(copyEtag).isNotNull();

        S3Metadata glacierMetadata = s3Service.headObject(TEST_BUCKET, newKey).join();
        assertThat(glacierMetadata.getStorageClass()).isEqualTo("GLACIER");
        assertThat(glacierMetadata.getUserMetadata()).containsEntry("content", "log-data");
        
        // 원본은 여전히 STANDARD
        S3Metadata unchangedMetadata = s3Service.headObject(TEST_BUCKET, key).join();
        assertThat(unchangedMetadata.getStorageClass()).isEqualTo("STANDARD");
    }
}