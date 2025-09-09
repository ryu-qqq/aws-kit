package com.ryuqq.aws.commons;

import com.ryuqq.aws.commons.config.AwsClientConfig;
import com.ryuqq.aws.commons.metrics.AwsMetricsConfiguration;
import com.ryuqq.aws.commons.properties.AwsProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/**
 * AWS SDK Commons 자동 설정 클래스
 */
@AutoConfiguration
@EnableConfigurationProperties(AwsProperties.class)
@Import({
    AwsClientConfig.class,
    AwsMetricsConfiguration.class
})
public class AwsSdkCommonsAutoConfiguration {
    
    public AwsSdkCommonsAutoConfiguration() {
        // 초기화 로그
        System.out.println("AWS SDK Commons Auto Configuration initialized");
    }
}