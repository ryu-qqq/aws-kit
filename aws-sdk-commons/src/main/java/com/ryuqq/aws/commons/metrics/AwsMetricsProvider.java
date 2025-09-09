package com.ryuqq.aws.commons.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Tags;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AWS 서비스 메트릭 수집 및 제공
 */
@Component
@RequiredArgsConstructor
public class AwsMetricsProvider {

    private final MeterRegistry meterRegistry;

    /**
     * AWS API 호출 타이머 시작
     */
    public Timer.Sample startApiCallTimer(String serviceName, String operation) {
        return Timer.start(meterRegistry);
    }

    /**
     * AWS API 호출 타이머 종료
     */
    public void stopApiCallTimer(Timer.Sample sample, String serviceName, String operation, boolean success) {
        sample.stop(Timer.builder("aws.api.call.duration")
                .description("AWS API call duration")
                .tag("aws.service", serviceName)
                .tag("operation", operation)
                .tag("success", String.valueOf(success))
                .register(meterRegistry));
    }

    /**
     * AWS API 호출 성공 카운터 증가
     */
    public void incrementSuccessCounter(String serviceName, String operation) {
        Counter.builder("aws.api.call.success")
                .description("AWS API call success count")
                .tag("aws.service", serviceName)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }

    /**
     * AWS API 호출 실패 카운터 증가
     */
    public void incrementErrorCounter(String serviceName, String operation, String errorCode) {
        Counter.builder("aws.api.call.error")
                .description("AWS API call error count")
                .tag("aws.service", serviceName)
                .tag("operation", operation)
                .tag("error.code", errorCode)
                .register(meterRegistry)
                .increment();
    }

    /**
     * AWS 리소스 사용량 게이지 업데이트
     */
    public void updateResourceGauge(String serviceName, String resource, double value) {
        meterRegistry.gauge("aws.resource.usage", 
                Tags.of("aws.service", serviceName, "resource", resource),
                value);
    }

    /**
     * 재시도 카운터 증가
     */
    public void incrementRetryCounter(String serviceName, String operation) {
        Counter.builder("aws.api.call.retry")
                .description("AWS API call retry count")
                .tag("aws.service", serviceName)
                .tag("operation", operation)
                .register(meterRegistry)
                .increment();
    }
}