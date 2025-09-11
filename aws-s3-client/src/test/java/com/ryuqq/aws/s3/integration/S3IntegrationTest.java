package com.ryuqq.aws.s3.integration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 통합 테스트 - LocalStack 사용
 */
@Testcontainers
class S3IntegrationTest {

    @Container
    static final LocalStackContainer localstack = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:latest"))
            .withServices(LocalStackContainer.Service.S3)
            .withEnv("DEFAULT_REGION", "us-east-1");

    private static S3AsyncClient s3Client;
    private static final String TEST_BUCKET = "test-bucket";

    @BeforeAll
    static void setUp() {
        s3Client = S3AsyncClient.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .region(Region.US_EAST_1)
                .forcePathStyle(true) // Required for LocalStack
                .build();
    }

    @Test
    void shouldCreateBucketAndPutObject() throws Exception {
        // Given - Create bucket
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(TEST_BUCKET)
                .build();

        CompletableFuture<CreateBucketResponse> createResponse = s3Client.createBucket(createBucketRequest);
        assertThat(createResponse.get()).isNotNull();

        // When - Put object
        String objectKey = "test-object.txt";
        String content = "Hello LocalStack S3!";
        
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(TEST_BUCKET)
                .key(objectKey)
                .contentType("text/plain")
                .build();

        CompletableFuture<PutObjectResponse> putResponse = s3Client.putObject(
                putRequest, 
                AsyncRequestBody.fromString(content));
        PutObjectResponse response = putResponse.get();

        // Then
        assertThat(response.eTag()).isNotBlank();
    }

    @Test
    void shouldPutAndGetObject() throws Exception {
        // Given - Create bucket
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(TEST_BUCKET + "-get")
                .build();
        s3Client.createBucket(createBucketRequest).get();

        // Put object
        String objectKey = "test-file.txt";
        String content = "Test content for retrieval";
        
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(TEST_BUCKET + "-get")
                .key(objectKey)
                .build();

        s3Client.putObject(putRequest, AsyncRequestBody.fromString(content)).get();

        // When - Get object
        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(TEST_BUCKET + "-get")
                .key(objectKey)
                .build();

        CompletableFuture<String> getResponse = s3Client.getObject(
                getRequest, 
                AsyncResponseTransformer.toBytes())
                .thenApply(responseBytes -> responseBytes.asString(StandardCharsets.UTF_8));

        // Then
        String retrievedContent = getResponse.get();
        assertThat(retrievedContent).isEqualTo(content);
    }

    @Test
    void shouldListObjects() throws Exception {
        // Given - Create bucket and put multiple objects
        String bucketName = TEST_BUCKET + "-list";
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.createBucket(createBucketRequest).get();

        // Put multiple objects
        for (int i = 1; i <= 3; i++) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key("file-" + i + ".txt")
                    .build();
            s3Client.putObject(putRequest, AsyncRequestBody.fromString("Content " + i)).get();
        }

        // When - List objects
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        CompletableFuture<ListObjectsV2Response> listResponse = s3Client.listObjectsV2(listRequest);
        ListObjectsV2Response response = listResponse.get();

        // Then
        assertThat(response.contents()).hasSize(3);
        assertThat(response.contents().stream().map(S3Object::key))
                .containsExactlyInAnyOrder("file-1.txt", "file-2.txt", "file-3.txt");
    }

    @Test
    void shouldDeleteObject() throws Exception {
        // Given - Create bucket and put object
        String bucketName = TEST_BUCKET + "-delete";
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.createBucket(createBucketRequest).get();

        String objectKey = "file-to-delete.txt";
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        s3Client.putObject(putRequest, AsyncRequestBody.fromString("Content to delete")).get();

        // When - Delete object
        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        CompletableFuture<DeleteObjectResponse> deleteResponse = s3Client.deleteObject(deleteRequest);
        assertThat(deleteResponse.get()).isNotNull();

        // Then - Verify object is deleted
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest).get();
        assertThat(listResponse.contents()).isEmpty();
    }

    @Test
    void shouldGetObjectMetadata() throws Exception {
        // Given - Create bucket and put object with metadata
        String bucketName = TEST_BUCKET + "-metadata";
        CreateBucketRequest createBucketRequest = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
        s3Client.createBucket(createBucketRequest).get();

        String objectKey = "metadata-test.txt";
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType("text/plain")
                .metadata(Map.of("custom-key", "custom-value"))
                .build();

        s3Client.putObject(putRequest, AsyncRequestBody.fromString("Test content")).get();

        // When - Get object metadata
        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        CompletableFuture<HeadObjectResponse> headResponse = s3Client.headObject(headRequest);
        HeadObjectResponse response = headResponse.get();

        // Then
        assertThat(response.contentType()).isEqualTo("text/plain");
        assertThat(response.metadata()).containsEntry("custom-key", "custom-value");
        assertThat(response.contentLength()).isEqualTo(12L); // "Test content".length()
    }
}