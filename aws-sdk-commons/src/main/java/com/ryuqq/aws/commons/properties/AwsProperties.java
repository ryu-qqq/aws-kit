package com.ryuqq.aws.commons.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/**
 * AWS SDK 공통 설정 Properties
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "aws")
public class AwsProperties {

    @NotBlank(message = "AWS region은 필수입니다")
    private String region = "us-west-2";

    @Valid
    @NotNull
    private Credentials credentials = new Credentials();

    @Valid  
    @NotNull
    private ClientConfig clientConfig = new ClientConfig();

    @Valid
    @NotNull
    private RetryPolicy retryPolicy = new RetryPolicy();

    @Getter
    @Setter
    public static class Credentials {
        private String accessKeyId;
        private String secretAccessKey;
        private String sessionToken;
        private String profileName = "default";
        private boolean useInstanceProfile = true;
    }

    @Getter
    @Setter
    public static class ClientConfig {
        private Duration connectionTimeout = Duration.ofSeconds(10);
        private Duration socketTimeout = Duration.ofSeconds(30);
        private Duration apiCallTimeout = Duration.ofMinutes(2);
        private Duration apiCallAttemptTimeout = Duration.ofSeconds(30);
        private int maxConcurrency = 50;
        private int maxPendingConnectionAcquires = 10000;
        private boolean useHttp2 = true;
        private String userAgentPrefix = "ryuqq-aws-sdk";
    }

    @Getter
    @Setter
    public static class RetryPolicy {
        private int maxRetries = 3;
        private Duration baseDelay = Duration.ofMillis(100);
        private Duration maxBackoffTime = Duration.ofSeconds(20);
        private boolean retryOnThrottling = true;
        private boolean retryOnClientError = false;
    }
}