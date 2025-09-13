package com.ryuqq.aws.sqs.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS SQS 클라이언트의 설정 프로퍼티 클래스
 * 
 * <p>Spring Boot의 application.yml 또는 application.properties에서 
 * aws.sqs 접두사를 통해 설정할 수 있는 SQS 관련 구성 옵션들을 정의합니다.</p>
 * 
 * <h3>설정 예시 (application.yml):</h3>
 * <pre><code>
 * aws:
 *   sqs:
 *     long-polling-wait-seconds: 20
 *     max-batch-size: 10
 *     visibility-timeout: 30
 * </code></pre>
 * 
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "aws.sqs")
public class SqsProperties {

    /**
     * Long Polling 대기 시간 (초 단위)
     * 
     * <p>SQS 메시지 수신 시 메시지가 도착할 때까지 기다리는 시간입니다.
     * Long Polling을 사용하면 API 호출 횟수를 줄여 비용을 절약할 수 있습니다.</p>
     * 
     * <ul>
     *   <li>범위: 0-20초 (AWS SDK에서 검증)</li>
     *   <li>0초: Short Polling (즉시 응답)</li>
     *   <li>1-20초: Long Polling (메시지 대기)</li>
     * </ul>
     * 
     * @default 20초 (최대 대기 시간)
     */
    private int longPollingWaitSeconds = 20;

    /**
     * 배치 작업 시 최대 메시지 개수
     * 
     * <p>sendMessageBatch(), deleteMessageBatch(), receiveMessages() 등의 배치 작업에서
     * 한 번에 처리할 수 있는 최대 메시지 개수를 설정합니다.</p>
     * 
     * <ul>
     *   <li>범위: 1-10개 (AWS SQS 제한사항)</li>
     *   <li>배치 전체 크기는 최대 256KB</li>
     *   <li>개별 메시지도 최대 256KB</li>
     * </ul>
     * 
     * @default 10개 (AWS SQS 최대값)
     */
    private int maxBatchSize = 10;

    /**
     * 메시지 가시성 타임아웃 (초 단위)
     * 
     * <p>메시지를 수신한 후 다른 컨슈머가 같은 메시지를 볼 수 없는 시간입니다.
     * 이 시간 내에 메시지를 처리하고 삭제해야 중복 처리를 방지할 수 있습니다.</p>
     * 
     * <h4>동작 원리:</h4>
     * <ul>
     *   <li>메시지 수신 시 타임아웃이 시작됨</li>
     *   <li>타임아웃 내에 메시지 삭제 시 완전히 제거됨</li>
     *   <li>타임아웃 초과 시 메시지가 다시 큐에 나타남</li>
     * </ul>
     * 
     * <h4>설정 가이드:</h4>
     * <ul>
     *   <li>짧게 설정: 빠른 재처리, 중복 처리 위험</li>
     *   <li>길게 설정: 안전한 처리, 지연 복구</li>
     * </ul>
     * 
     * @default 30초 (일반적인 메시지 처리 시간)
     */
    private int visibilityTimeout = 30;

    public int getLongPollingWaitSeconds() {
        return longPollingWaitSeconds;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public int getVisibilityTimeout() {
        return visibilityTimeout;
    }

    public void setLongPollingWaitSeconds(int longPollingWaitSeconds) {
        this.longPollingWaitSeconds = longPollingWaitSeconds;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public void setVisibilityTimeout(int visibilityTimeout) {
        this.visibilityTimeout = visibilityTimeout;
    }
}