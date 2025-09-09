package com.ryuqq.example.controller;

import com.ryuqq.aws.dynamodb.client.DynamoDbClient;
import com.ryuqq.aws.lambda.client.LambdaClient;
import com.ryuqq.aws.s3.client.S3Client;
import com.ryuqq.aws.sqs.client.SqsClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * AWS SDK 모듈 사용 예제 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/aws")
@RequiredArgsConstructor
@Tag(name = "AWS SDK 예제", description = "AWS SDK 모듈 사용 예제 API")
public class AwsExampleController {

    private final SqsClient sqsClient;
    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final LambdaClient lambdaClient;

    @Operation(summary = "SQS 메시지 전송", description = "지정된 SQS 큐에 메시지를 전송합니다")
    @PostMapping("/sqs/send")
    public CompletableFuture<ResponseEntity<String>> sendSqsMessage(
            @RequestParam String queueUrl,
            @RequestBody String message) {
        log.info("Sending SQS message to queue: {}", queueUrl);
        
        return sqsClient.sendMessage(queueUrl, message)
                .thenApply(messageId -> {
                    log.info("SQS message sent with ID: {}", messageId);
                    return ResponseEntity.ok("Message sent with ID: " + messageId);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to send SQS message", throwable);
                    return ResponseEntity.internalServerError()
                            .body("Failed to send message: " + throwable.getMessage());
                });
    }

    @Operation(summary = "DynamoDB 테이블 존재 확인", description = "DynamoDB 테이블이 존재하는지 확인합니다")
    @GetMapping("/dynamodb/table/{tableName}/exists")
    public CompletableFuture<ResponseEntity<Boolean>> checkTableExists(@PathVariable String tableName) {
        log.info("Checking if DynamoDB table exists: {}", tableName);
        
        return dynamoDbClient.tableExists(tableName)
                .thenApply(exists -> {
                    log.info("DynamoDB table '{}' exists: {}", tableName, exists);
                    return ResponseEntity.ok(exists);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to check table existence", throwable);
                    return ResponseEntity.internalServerError().body(false);
                });
    }

    @Operation(summary = "S3 객체 업로드", description = "S3 버킷에 객체를 업로드합니다")
    @PostMapping("/s3/upload")
    public CompletableFuture<ResponseEntity<String>> uploadS3Object(
            @RequestParam String bucketName,
            @RequestParam String key,
            @RequestBody String content) {
        log.info("Uploading object to S3: {}/{}", bucketName, key);
        
        return s3Client.uploadObject(bucketName, key, content.getBytes())
                .thenApply(etag -> {
                    log.info("S3 object uploaded with ETag: {}", etag);
                    return ResponseEntity.ok("Object uploaded with ETag: " + etag);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to upload S3 object", throwable);
                    return ResponseEntity.internalServerError()
                            .body("Failed to upload object: " + throwable.getMessage());
                });
    }

    @Operation(summary = "Lambda 함수 호출", description = "Lambda 함수를 동기적으로 호출합니다")
    @PostMapping("/lambda/invoke")
    public CompletableFuture<ResponseEntity<String>> invokeLambdaFunction(
            @RequestParam String functionName,
            @RequestBody String payload) {
        log.info("Invoking Lambda function: {}", functionName);
        
        return lambdaClient.invokeFunction(functionName, payload)
                .thenApply(response -> {
                    log.info("Lambda function invoked successfully");
                    return ResponseEntity.ok(response);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to invoke Lambda function", throwable);
                    return ResponseEntity.internalServerError()
                            .body("Failed to invoke function: " + throwable.getMessage());
                });
    }

    @Operation(summary = "헬스 체크", description = "애플리케이션 상태를 확인합니다")
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        log.info("Health check requested");
        return ResponseEntity.ok("AWS SDK Example App is running!");
    }
}