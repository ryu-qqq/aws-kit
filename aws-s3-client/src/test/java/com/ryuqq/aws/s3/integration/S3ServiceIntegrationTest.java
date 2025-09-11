package com.ryuqq.aws.s3.integration;

import com.ryuqq.aws.s3.service.S3Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.transfer.s3.S3TransferManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/**
 * S3Service 통합 테스트 - 간소화된 S3 서비스 테스트
 * LocalStack를 사용한 실제 S3 작업 테스트
 */
@Testcontainers
class S3ServiceIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0"))
            .withServices(Service.S3);

    private S3AsyncClient s3AsyncClient;
    private S3TransferManager transferManager;
    private S3Presigner s3Presigner;
    private S3Service s3Service;

    private static final String TEST_BUCKET = "test-bucket";

    // 테스트는 Spring 컨텍스트 없이 직접 AWS 클라이언트를 구성합니다

    @BeforeEach
    void setUp() throws Exception {
        // S3AsyncClient 설정
        s3AsyncClient = S3AsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .endpointOverride(localstack.getEndpoint())
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .build())
                .httpClient(NettyNioAsyncHttpClient.builder().build())
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();

        // S3Presigner 설정
        s3Presigner = S3Presigner.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .endpointOverride(localstack.getEndpoint())
                .build();

        // S3TransferManager 설정
        transferManager = S3TransferManager.builder()
                .s3Client(s3AsyncClient)
                .build();

        // S3Service 설정 (직접 생성 - 스프링 컨텍스트 없이)
        s3Service = new TestS3Service(s3AsyncClient, transferManager, s3Presigner);

        // 테스트 버킷 생성
        createBucket(TEST_BUCKET);
    }

    @AfterEach
    void tearDown() {
        if (s3AsyncClient != null) {
            s3AsyncClient.close();
        }
        if (s3Presigner != null) {
            s3Presigner.close();
        }
        if (transferManager != null) {
            transferManager.close();
        }
    }

    @Test
    void uploadAndDownloadFile_ShouldWorkCorrectly() throws Exception {
        // Given
        String key = "test-file.txt";
        String content = "Hello, S3!";
        Path tempFile = Files.createTempFile("test", ".txt");
        Files.write(tempFile, content.getBytes());

        try {
            // When - Upload
            CompletableFuture<String> uploadResult = s3Service.uploadFile(TEST_BUCKET, key, tempFile);
            String etag = uploadResult.join();

            // Then - Upload success
            assertThat(etag).isNotNull().isNotEmpty();

            // When - Download
            CompletableFuture<byte[]> downloadResult = s3Service.downloadFile(TEST_BUCKET, key);
            byte[] downloadedContent = downloadResult.join();

            // Then - Download success
            assertThat(new String(downloadedContent)).isEqualTo(content);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void uploadBytes_ShouldWorkCorrectly() {
        // Given
        String key = "bytes-file.txt";
        String content = "Hello from bytes!";
        byte[] bytes = content.getBytes();
        String contentType = "text/plain";

        // When
        CompletableFuture<String> uploadResult = s3Service.uploadBytes(TEST_BUCKET, key, bytes, contentType);
        String etag = uploadResult.join();

        // Then
        assertThat(etag).isNotNull().isNotEmpty();

        // Verify by downloading
        CompletableFuture<byte[]> downloadResult = s3Service.downloadFile(TEST_BUCKET, key);
        byte[] downloadedBytes = downloadResult.join();
        assertThat(new String(downloadedBytes)).isEqualTo(content);
    }

    @Test
    void downloadToFile_ShouldWorkCorrectly() throws Exception {
        // Given
        String key = "download-to-file.txt";
        String content = "Download to file content";
        byte[] bytes = content.getBytes();

        // Upload first
        s3Service.uploadBytes(TEST_BUCKET, key, bytes, "text/plain").join();

        Path targetFile = Files.createTempFile("download", ".txt");
        try {
            // When
            CompletableFuture<Void> downloadResult = s3Service.downloadToFile(TEST_BUCKET, key, targetFile);
            downloadResult.join();

            // Then
            assertThat(Files.exists(targetFile)).isTrue();
            String downloadedContent = Files.readString(targetFile);
            assertThat(downloadedContent).isEqualTo(content);
        } finally {
            Files.deleteIfExists(targetFile);
        }
    }

    @Test
    void listObjects_ShouldReturnUploadedFiles() {
        // Given
        String prefix = "list-test/";
        String key1 = prefix + "file1.txt";
        String key2 = prefix + "file2.txt";
        byte[] content = "test content".getBytes();

        // Upload files
        s3Service.uploadBytes(TEST_BUCKET, key1, content, "text/plain").join();
        s3Service.uploadBytes(TEST_BUCKET, key2, content, "text/plain").join();

        // When
        CompletableFuture<List<String>> listResult = s3Service.listObjects(TEST_BUCKET, prefix);
        List<String> keys = listResult.join();

        // Then
        assertThat(keys).hasSize(2);
        assertThat(keys).containsExactlyInAnyOrder(key1, key2);
    }

    @Test
    void deleteObject_ShouldRemoveFile() {
        // Given
        String key = "delete-test.txt";
        byte[] content = "content to delete".getBytes();

        // Upload first
        s3Service.uploadBytes(TEST_BUCKET, key, content, "text/plain").join();

        // Verify exists
        List<String> beforeDelete = s3Service.listObjects(TEST_BUCKET, "delete-test").join();
        assertThat(beforeDelete).contains(key);

        // When
        CompletableFuture<Void> deleteResult = s3Service.deleteObject(TEST_BUCKET, key);
        deleteResult.join();

        // Then
        List<String> afterDelete = s3Service.listObjects(TEST_BUCKET, "delete-test").join();
        assertThat(afterDelete).doesNotContain(key);
    }

    @Test
    void generatePresignedUrl_ShouldReturnValidUrl() {
        // Given
        String key = "presigned-test.txt";
        byte[] content = "presigned content".getBytes();
        Duration expiration = Duration.ofMinutes(15);

        // Upload first
        s3Service.uploadBytes(TEST_BUCKET, key, content, "text/plain").join();

        // When
        CompletableFuture<String> urlResult = s3Service.generatePresignedUrl(TEST_BUCKET, key, expiration);
        String presignedUrl = urlResult.join();

        // Then
        assertThat(presignedUrl).isNotNull().isNotEmpty();
        assertThat(presignedUrl).contains(TEST_BUCKET);
        assertThat(presignedUrl).contains(key);
        assertThat(presignedUrl).contains("X-Amz-Expires");
    }

    @Test
    void uploadLargeFile_ShouldUseTransferManager() throws Exception {
        // Given
        String key = "large-file.txt";
        String content = "A".repeat(10 * 1024 * 1024); // 10MB file
        Path largeFile = Files.createTempFile("large", ".txt");
        Files.write(largeFile, content.getBytes());

        try {
            // When
            CompletableFuture<String> uploadResult = s3Service.uploadLargeFile(TEST_BUCKET, key, largeFile);
            String etag = uploadResult.join();

            // Then
            assertThat(etag).isNotNull().isNotEmpty();

            // Verify by checking if file exists
            List<String> objects = s3Service.listObjects(TEST_BUCKET, key).join();
            assertThat(objects).contains(key);
        } finally {
            Files.deleteIfExists(largeFile);
        }
    }

    private void createBucket(String bucketName) {
        try {
            CreateBucketRequest request = CreateBucketRequest.builder()
                    .bucket(bucketName)
                    .build();
            s3AsyncClient.createBucket(request).join();
        } catch (Exception e) {
            // Bucket might already exist
        }
    }

    // 테스트용 S3Service 구현체 (AwsOperationTemplate 없이)
    private static class TestS3Service implements S3Service {
        private final S3AsyncClient s3AsyncClient;
        private final S3TransferManager transferManager;
        private final S3Presigner s3Presigner;

        public TestS3Service(S3AsyncClient s3AsyncClient, S3TransferManager transferManager, S3Presigner s3Presigner) {
            this.s3AsyncClient = s3AsyncClient;
            this.transferManager = transferManager;
            this.s3Presigner = s3Presigner;
        }

        @Override
        public CompletableFuture<String> uploadFile(String bucket, String key, Path file) {
            software.amazon.awssdk.transfer.s3.model.UploadFileRequest request = 
                    software.amazon.awssdk.transfer.s3.model.UploadFileRequest.builder()
                            .source(file)
                            .putObjectRequest(software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(key)
                                    .build())
                            .build();

            return transferManager.uploadFile(request)
                    .completionFuture()
                    .thenApply(upload -> upload.response().eTag());
        }

        @Override
        public CompletableFuture<String> uploadBytes(String bucket, String key, byte[] bytes, String contentType) {
            software.amazon.awssdk.services.s3.model.PutObjectRequest request = 
                    software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) bytes.length)
                            .build();

            return s3AsyncClient.putObject(request, software.amazon.awssdk.core.async.AsyncRequestBody.fromBytes(bytes))
                    .thenApply(software.amazon.awssdk.services.s3.model.PutObjectResponse::eTag);
        }

        @Override
        public CompletableFuture<byte[]> downloadFile(String bucket, String key) {
            software.amazon.awssdk.services.s3.model.GetObjectRequest request = 
                    software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build();

            return s3AsyncClient.getObject(request, software.amazon.awssdk.core.async.AsyncResponseTransformer.toBytes())
                    .thenApply(response -> response.asByteArray());
        }

        @Override
        public CompletableFuture<Void> downloadToFile(String bucket, String key, Path targetFile) {
            software.amazon.awssdk.transfer.s3.model.DownloadFileRequest request = 
                    software.amazon.awssdk.transfer.s3.model.DownloadFileRequest.builder()
                            .destination(targetFile)
                            .getObjectRequest(software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                                    .bucket(bucket)
                                    .key(key)
                                    .build())
                            .build();

            return transferManager.downloadFile(request)
                    .completionFuture()
                    .thenApply(download -> null);
        }

        @Override
        public CompletableFuture<Void> deleteObject(String bucket, String key) {
            software.amazon.awssdk.services.s3.model.DeleteObjectRequest request = 
                    software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build();

            return s3AsyncClient.deleteObject(request).thenApply(response -> null);
        }

        @Override
        public CompletableFuture<List<String>> listObjects(String bucket, String prefix) {
            software.amazon.awssdk.services.s3.model.ListObjectsV2Request request = 
                    software.amazon.awssdk.services.s3.model.ListObjectsV2Request.builder()
                            .bucket(bucket)
                            .prefix(prefix)
                            .build();

            return s3AsyncClient.listObjectsV2(request)
                    .thenApply(response -> response.contents().stream()
                            .map(software.amazon.awssdk.services.s3.model.S3Object::key)
                            .toList());
        }

        @Override
        public CompletableFuture<String> generatePresignedUrl(String bucket, String key, Duration expiration) {
            return CompletableFuture.supplyAsync(() -> {
                software.amazon.awssdk.services.s3.model.GetObjectRequest getObjectRequest = 
                        software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .build();

                software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest presignRequest = 
                        software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest.builder()
                                .signatureDuration(expiration)
                                .getObjectRequest(getObjectRequest)
                                .build();

                return s3Presigner.presignGetObject(presignRequest).url().toString();
            });
        }

        @Override
        public CompletableFuture<String> uploadLargeFile(String bucket, String key, Path file) {
            return uploadFile(bucket, key, file); // Transfer Manager handles multipart automatically
        }
    }
}