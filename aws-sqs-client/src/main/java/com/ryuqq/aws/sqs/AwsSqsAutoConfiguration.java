package com.ryuqq.aws.sqs;

import com.ryuqq.aws.sqs.config.SqsClientConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/**
 * SQS 클라이언트 자동 설정
 */
@AutoConfiguration
@ConditionalOnClass(SqsAsyncClient.class)
@Import(SqsClientConfiguration.class)
public class AwsSqsAutoConfiguration {
}