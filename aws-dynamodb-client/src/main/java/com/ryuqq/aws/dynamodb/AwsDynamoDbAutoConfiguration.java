package com.ryuqq.aws.dynamodb;

import com.ryuqq.aws.dynamodb.config.DynamoDbClientConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

/**
 * DynamoDB 클라이언트 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(DynamoDbAsyncClient.class)
@Import(DynamoDbClientConfiguration.class)
public class AwsDynamoDbAutoConfiguration {
}