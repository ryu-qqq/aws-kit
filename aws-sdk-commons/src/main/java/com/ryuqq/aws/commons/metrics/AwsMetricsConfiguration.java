package com.ryuqq.aws.commons.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AWS 서비스 메트릭 설정
 */
@Slf4j
@Configuration
public class AwsMetricsConfiguration {

    /**
     * AWS SDK 메트릭 커스터마이제이션
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> awsMetricsCustomizer() {
        return registry -> {
            // AWS SDK 호출 성공률 메트릭
            registry.gauge("aws.sdk.calls.success.rate", 0.0);
            
            // AWS SDK 호출 지연시간 히스토그램
            Timer.builder("aws.sdk.calls.duration")
                    .description("AWS SDK API call duration")
                    .register(registry);
        };
    }

    /**
     * AWS 서비스별 메트릭 제공자
     */
    @Bean
    public AwsMetricsProvider awsMetricsProvider(MeterRegistry meterRegistry) {
        return new AwsMetricsProvider(meterRegistry);
    }
}