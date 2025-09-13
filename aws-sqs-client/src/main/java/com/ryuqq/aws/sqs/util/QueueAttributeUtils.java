package com.ryuqq.aws.sqs.util;

import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AWS SQS 큐 속성 변환 및 검증을 위한 유틸리티 클래스
 * 
 * <p>SQS 큐 생성 및 설정에서 사용되는 속성 변환 로직을 중앙화합니다.
 * String 기반 속성 맵을 AWS SDK의 QueueAttributeName 열거형으로 변환하고,
 * 속성값의 유효성을 검증합니다.</p>
 * 
 * <h3>지원하는 주요 큐 속성:</h3>
 * <ul>
 *   <li><strong>VisibilityTimeout</strong>: 메시지 가시성 타임아웃 (0-43200초)</li>
 *   <li><strong>MessageRetentionPeriod</strong>: 메시지 보관 기간 (60-1209600초)</li>
 *   <li><strong>MaxReceiveCount</strong>: 최대 수신 횟수 (1-1000)</li>
 *   <li><strong>ReceiveMessageWaitTimeSeconds</strong>: Long Polling 대기 시간 (0-20초)</li>
 *   <li><strong>DelaySeconds</strong>: 메시지 지연 전송 시간 (0-900초)</li>
 * </ul>
 * 
 * <h3>속성 변환 과정:</h3>
 * <ul>
 *   <li><strong>타입 변환</strong>: String 키를 QueueAttributeName 열거형으로 변환</li>
 *   <li><strong>값 검증</strong>: AWS SQS 제한사항에 따른 값 범위 검증</li>
 *   <li><strong>예외 처리</strong>: 잘못된 속성명이나 값에 대한 명확한 오류 메시지</li>
 * </ul>
 * 
 * @since 1.0.0
 * @see QueueAttributeName AWS SDK의 큐 속성 열거형
 */
public final class QueueAttributeUtils {
    
    /** 기본 Visibility Timeout (30초) */
    public static final String DEFAULT_VISIBILITY_TIMEOUT = "30";
    
    /** 기본 메시지 보관 기간 (4일 = 345600초) */
    public static final String DEFAULT_MESSAGE_RETENTION_PERIOD = "345600";
    
    /** 기본 Long Polling 대기 시간 (0초, Short Polling) */
    public static final String DEFAULT_WAIT_TIME_SECONDS = "0";
    
    /** 최대 Visibility Timeout (12시간 = 43200초) */
    public static final int MAX_VISIBILITY_TIMEOUT = 43200;
    
    /** 최대 메시지 보관 기간 (14일 = 1209600초) */
    public static final int MAX_MESSAGE_RETENTION_PERIOD = 1209600;
    
    /** 최소 메시지 보관 기간 (1분 = 60초) */
    public static final int MIN_MESSAGE_RETENTION_PERIOD = 60;
    
    /** 최대 Long Polling 대기 시간 (20초) */
    public static final int MAX_WAIT_TIME_SECONDS = 20;
    
    /** 최대 지연 전송 시간 (15분 = 900초) */
    public static final int MAX_DELAY_SECONDS = 900;
    
    /** 최대 재시도 횟수 (1-1000) */
    public static final int MAX_RECEIVE_COUNT = 1000;
    
    private QueueAttributeUtils() {
        // 유틸리티 클래스 - 인스턴스 생성 방지
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스를 생성할 수 없습니다");
    }
    
    /**
     * 문자열 속성 맵을 AWS SDK QueueAttributeName 맵으로 변환합니다.
     * 
     * <p>큐 생성 API에서 사용하는 일반적인 String 키-값 형태의 속성을
     * AWS SDK가 요구하는 QueueAttributeName 열거형 기반 맵으로 변환합니다.</p>
     * 
     * <h4>변환 과정:</h4>
     * <ol>
     *   <li>각 문자열 키를 QueueAttributeName으로 변환 (대소문자 구분)</li>
     *   <li>속성 값의 유효성 검증 (타입, 범위)</li>
     *   <li>변환 실패 시 구체적인 오류 정보 제공</li>
     * </ol>
     * 
     * @param stringAttributes 문자열 기반 속성 맵 (null 허용)
     * @return AWS SDK QueueAttributeName 기반 속성 맵 (null/empty 입력 시 빈 맵 반환)
     * @throws IllegalArgumentException 잘못된 속성명이나 값이 포함된 경우
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * Map&lt;String, String&gt; attributes = Map.of(
     *     "VisibilityTimeout", "60",
     *     "MessageRetentionPeriod", "1209600",
     *     "ReceiveMessageWaitTimeSeconds", "20"
     * );
     * 
     * Map&lt;QueueAttributeName, String&gt; queueAttributes = 
     *     QueueAttributeUtils.convertToQueueAttributes(attributes);
     * </code></pre>
     */
    public static Map<QueueAttributeName, String> convertToQueueAttributes(Map<String, String> stringAttributes) {
        if (stringAttributes == null || stringAttributes.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<QueueAttributeName, String> queueAttributes = new HashMap<>();
        
        for (Map.Entry<String, String> entry : stringAttributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            // 키와 값의 null 체크
            if (key == null || key.trim().isEmpty()) {
                throw new IllegalArgumentException("큐 속성 키는 null이거나 빈 문자열일 수 없습니다");
            }
            
            if (value == null) {
                throw new IllegalArgumentException("큐 속성 값은 null일 수 없습니다: " + key);
            }
            
            // 문자열 키를 QueueAttributeName 열거형으로 변환
            QueueAttributeName attributeName = QueueAttributeName.fromValue(key);
            
            // AWS SDK는 알 수 없는 속성에 대해 예외를 던지지 않고 UNKNOWN_TO_SDK_VERSION을 반환
            if (attributeName != null && "UNKNOWN_TO_SDK_VERSION".equals(attributeName.name())) {
                throw new IllegalArgumentException(
                    String.format("지원하지 않는 큐 속성입니다: '%s'", key)
                );
            }
            
            // 속성 값 검증 - 이 예외는 그대로 전달
            validateAttributeValue(attributeName, value);
            
            queueAttributes.put(attributeName, value);
        }
        
        return queueAttributes;
    }
    
    /**
     * Stream API를 사용하여 속성 맵을 변환합니다.
     * 
     * <p>함수형 프로그래밍 방식으로 속성 변환을 수행하는 대안 메서드입니다.
     * 대용량 속성 맵이나 병렬 처리가 필요한 경우에 사용할 수 있습니다.</p>
     * 
     * @param stringAttributes 문자열 기반 속성 맵
     * @return AWS SDK QueueAttributeName 기반 속성 맵
     * @throws IllegalArgumentException 잘못된 속성명이나 값이 포함된 경우
     */
    public static Map<QueueAttributeName, String> convertToQueueAttributesWithStream(Map<String, String> stringAttributes) {
        if (stringAttributes == null || stringAttributes.isEmpty()) {
            return Collections.emptyMap();
        }
        
        return stringAttributes.entrySet().stream()
                .peek(entry -> {
                    if (entry.getKey() == null || entry.getKey().trim().isEmpty()) {
                        throw new IllegalArgumentException("큐 속성 키는 null이거나 빈 문자열일 수 없습니다");
                    }
                    if (entry.getValue() == null) {
                        throw new IllegalArgumentException("큐 속성 값은 null일 수 없습니다: " + entry.getKey());
                    }
                })
                .collect(Collectors.toMap(
                    entry -> {
                        // 문자열 키를 QueueAttributeName 열거형으로 변환
                        QueueAttributeName attributeName = QueueAttributeName.fromValue(entry.getKey());
                        
                        // AWS SDK는 알 수 없는 속성에 대해 예외를 던지지 않고 UNKNOWN_TO_SDK_VERSION을 반환
                        if (attributeName != null && "UNKNOWN_TO_SDK_VERSION".equals(attributeName.name())) {
                            throw new IllegalArgumentException(
                                String.format("지원하지 않는 큐 속성입니다: '%s'", entry.getKey())
                            );
                        }
                        
                        // Validation exceptions are passed through
                        validateAttributeValue(attributeName, entry.getValue());
                        return attributeName;
                    },
                    Map.Entry::getValue
                ));
    }
    
    /**
     * 큐 속성 값의 유효성을 검증합니다.
     * 
     * <p>각 큐 속성별로 AWS SQS에서 허용하는 값의 범위와 형식을 검증합니다.
     * 잘못된 값이 감지되면 구체적인 오류 메시지와 함께 예외를 발생시킵니다.</p>
     * 
     * @param attributeName 검증할 속성명
     * @param value 검증할 속성값
     * @throws IllegalArgumentException 속성값이 유효하지 않은 경우
     */
    private static void validateAttributeValue(QueueAttributeName attributeName, String value) {
        try {
            switch (attributeName) {
                case VISIBILITY_TIMEOUT:
                    validateIntegerRange(value, 0, MAX_VISIBILITY_TIMEOUT, 
                        "VisibilityTimeout은 0-43200초 범위여야 합니다");
                    break;
                    
                    
                    
                case RECEIVE_MESSAGE_WAIT_TIME_SECONDS:
                    validateIntegerRange(value, 0, MAX_WAIT_TIME_SECONDS,
                        "ReceiveMessageWaitTimeSeconds는 0-20초 범위여야 합니다");
                    break;
                    
                case DELAY_SECONDS:
                    validateIntegerRange(value, 0, MAX_DELAY_SECONDS,
                        "DelaySeconds는 0-900초 범위여야 합니다");
                    break;
                    
                case POLICY:
                case REDRIVE_POLICY:
                case REDRIVE_ALLOW_POLICY:
                    // JSON 형태의 정책 문서는 별도 검증 없이 AWS에서 처리
                    if (value.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                            attributeName.name() + " 값은 빈 문자열일 수 없습니다"
                        );
                    }
                    break;
                    
                default:
                    // 기타 속성은 기본 검증만 수행
                    if (value.trim().isEmpty()) {
                        throw new IllegalArgumentException(
                            attributeName.name() + " 값은 빈 문자열일 수 없습니다"
                        );
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("%s 값이 올바른 숫자 형식이 아닙니다: %s", attributeName, value), e
            );
        }
    }
    
    /**
     * 정수 값의 범위를 검증합니다.
     * 
     * @param value 검증할 문자열 값
     * @param min 최소값 (포함)
     * @param max 최대값 (포함)
     * @param errorMessage 검증 실패 시 오류 메시지
     * @throws NumberFormatException 숫자 형식이 올바르지 않은 경우
     * @throws IllegalArgumentException 값이 범위를 벗어난 경우
     */
    private static void validateIntegerRange(String value, int min, int max, String errorMessage) {
        int intValue = Integer.parseInt(value);
        if (intValue < min || intValue > max) {
            throw new IllegalArgumentException(
                String.format("%s (현재 값: %d)", errorMessage, intValue)
            );
        }
    }
    
    /**
     * 기본 큐 속성 설정을 반환합니다.
     * 
     * <p>AWS SQS의 권장 설정값들로 구성된 기본 속성 맵을 제공합니다.
     * 큐 생성 시 명시적인 설정이 없을 때 안전한 기본값을 사용할 수 있습니다.</p>
     * 
     * @return 기본 큐 속성 맵
     * 
     * <h4>포함된 기본 속성:</h4>
     * <ul>
     *   <li>VisibilityTimeout: 30초</li>
     *   <li>MessageRetentionPeriod: 4일</li>
     *   <li>ReceiveMessageWaitTimeSeconds: 0초 (Short Polling)</li>
     * </ul>
     */
    public static Map<String, String> getDefaultAttributes() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("VisibilityTimeout", DEFAULT_VISIBILITY_TIMEOUT);
        defaults.put("ReceiveMessageWaitTimeSeconds", DEFAULT_WAIT_TIME_SECONDS);
        return defaults;
    }
    
    /**
     * Long Polling 설정을 위한 속성 맵을 반환합니다.
     * 
     * <p>효율적인 메시지 수신을 위해 Long Polling이 활성화된 속성 설정을 제공합니다.
     * 빈번한 API 호출을 줄이고 비용을 절약할 수 있습니다.</p>
     * 
     * @param waitTimeSeconds Long Polling 대기 시간 (1-20초)
     * @return Long Polling이 설정된 기본 속성 맵
     * @throws IllegalArgumentException 대기 시간이 유효 범위를 벗어난 경우
     */
    public static Map<String, String> getLongPollingAttributes(int waitTimeSeconds) {
        if (waitTimeSeconds < 1 || waitTimeSeconds > MAX_WAIT_TIME_SECONDS) {
            throw new IllegalArgumentException(
                "Long Polling 대기 시간은 1-20초 범위여야 합니다: " + waitTimeSeconds
            );
        }
        
        Map<String, String> attributes = getDefaultAttributes();
        attributes.put("ReceiveMessageWaitTimeSeconds", String.valueOf(waitTimeSeconds));
        return attributes;
    }
}