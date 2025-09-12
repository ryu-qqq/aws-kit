package com.ryuqq.aws.sqs.types;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * AWS Kit SQS 라이브러리의 메시지 추상화 타입
 * 
 * <p>AWS SDK의 Message 클래스를 대체하여 public API에서 AWS SDK 의존성을 숨깁니다.
 * SQS 메시지의 모든 중요 정보를 포함하며, 불변 객체로 설계되었습니다.</p>
 * 
 * <h3>주요 구성 요소:</h3>
 * <ul>
 *   <li><strong>기본 정보</strong>: messageId, body, receiptHandle</li>
 *   <li><strong>무결성 검증</strong>: MD5 체크섬 (본문 및 속성)</li>
 *   <li><strong>시스템 속성</strong>: AWS가 자동 설정하는 메타데이터</li>
 *   <li><strong>사용자 속성</strong>: 개발자가 정의한 커스텀 속성</li>
 *   <li><strong>타임스탬프</strong>: 라이브러리에서 설정하는 수신 시간</li>
 * </ul>
 * 
 * <h3>주요 시스템 속성:</h3>
 * <ul>
 *   <li><strong>ApproximateReceiveCount</strong>: 메시지가 수신된 대략적인 횟수</li>
 *   <li><strong>SentTimestamp</strong>: 메시지가 SQS에 전송된 시각 (Unix timestamp)</li>
 *   <li><strong>ApproximateFirstReceiveTimestamp</strong>: 최초 수신 시각 (Unix timestamp)</li>
 *   <li><strong>SenderId</strong>: 메시지를 전송한 AWS 사용자의 ID</li>
 * </ul>
 * 
 * @since 1.0.0
 * @see SqsMessageAttribute 라이브러리의 메시지 속성 타입
 * @see SqsService SQS 서비스 클래스
 */
public final class SqsMessage {
    
    private final String messageId;
    private final String body;
    private final String receiptHandle;
    private final String md5OfBody;
    private final String md5OfMessageAttributes;
    private final Map<String, String> attributes;
    private final Map<String, SqsMessageAttribute> messageAttributes;
    private final Instant timestamp;
    
    private SqsMessage(Builder builder) {
        this.messageId = builder.messageId;
        this.body = builder.body;
        this.receiptHandle = builder.receiptHandle;
        this.md5OfBody = builder.md5OfBody;
        this.md5OfMessageAttributes = builder.md5OfMessageAttributes;
        this.attributes = builder.attributes != null ? Map.copyOf(builder.attributes) : Map.of();
        this.messageAttributes = builder.messageAttributes != null ? Map.copyOf(builder.messageAttributes) : Map.of();
        this.timestamp = builder.timestamp;
    }
    
    /**
     * AWS SQS가 생성한 메시지의 고유 식별자를 반환합니다.
     * 
     * <p>Message ID는 다른 메시지와 구별하기 위한 고유한 식별자입니다.
     * 이 ID는 로깅이나 메시지 추적에 사용할 수 있습니다.</p>
     * 
     * @return 메시지 고유 ID
     */
    public String getMessageId() {
        return messageId;
    }
    
    /**
     * 메시지의 본문 내용을 반환합니다.
     * 
     * <p>본문은 메시지의 실제 데이터를 포함하며, 일반적으로 JSON, XML 또는
     * 일반 텍스트 형식으로 이루어집니다. 최대 256KB까지 저장 가능합니다.</p>
     * 
     * @return 메시지 본문 내용
     */
    public String getBody() {
        return body;
    }
    
    /**
     * 메시지 삭제에 필요한 Receipt Handle을 반환합니다.
     * 
     * <p>Receipt Handle은 메시지를 수신할 때 AWS가 제공하는 임시 식별자입니다.
     * 메시지 처리 후 삭제할 때 반드시 이 값을 사용해야 합니다.</p>
     * 
     * <h4>주의사항:</h4>
     * <ul>
     *   <li>Receipt Handle은 각 수신마다 다르게 생성됨</li>
     *   <li>Visibility Timeout 내에만 삭제에 사용 가능</li>
     *   <li>다른 수신에서 얻은 Receipt Handle은 사용 불가</li>
     * </ul>
     * 
     * @return 메시지 Receipt Handle
     */
    public String getReceiptHandle() {
        return receiptHandle;
    }
    
    public String getMd5OfBody() {
        return md5OfBody;
    }
    
    public String getMd5OfMessageAttributes() {
        return md5OfMessageAttributes;
    }
    
    public Map<String, String> getAttributes() {
        return attributes;
    }
    
    public Map<String, SqsMessageAttribute> getMessageAttributes() {
        return messageAttributes;
    }
    
    public Instant getTimestamp() {
        return timestamp;
    }
    
    /**
     * 지정한 시스템 속성의 값을 반환합니다.
     * 
     * <p>시스템 속성은 AWS SQS가 자동으로 설정하는 메타데이터입니다.
     * 일반적인 속성으로는 ApproximateReceiveCount, SentTimestamp 등이 있습니다.</p>
     * 
     * @param attributeName 조회할 속성 이름
     * @return 속성 값 (없으면 null)
     */
    public String getAttribute(String attributeName) {
        return attributes.get(attributeName);
    }
    
    /**
     * 지정한 사용자 정의 메시지 속성을 반환합닁니다.
     * 
     * <p>메시지 속성은 개발자가 메시지 전송 시 설정한 커스텀 데이터입니다.
     * String, Number, Binary 타입을 지원합니다.</p>
     * 
     * @param attributeName 조회할 메시지 속성 이름
     * @return 메시지 속성 객체 (없으면 null)
     */
    public SqsMessageAttribute getMessageAttribute(String attributeName) {
        return messageAttributes.get(attributeName);
    }
    
    /**
     * 메시지가 수신된 대략적인 횟수를 반환합니다.
     * 
     * <p>이 값은 AWS SQS가 자동으로 계산하는 수치로, 메시지가 얼마나 많이
     * 수신되었는지를 나타냅니다. DLQ(Dead Letter Queue) 설정에 사용되는 중요한 지표입니다.</p>
     * 
     * @return 수신 횟수 문자열 (예: "1", "5")
     */
    public String getApproximateReceiveCount() {
        return attributes.get("ApproximateReceiveCount");
    }
    
    /**
     * 메시지가 최초로 수신된 대략적인 시간을 반환합니다.
     * 
     * <p>Unix timestamp 형식으로 반환되며, 메시지가 처음 수신된 시점을 나타냅니다.
     * 메시지 처리 지연 시간을 계산하거나 모니터링에 유용합니다.</p>
     * 
     * @return Unix timestamp 문자열 (예: "1640995200000")
     */
    public String getApproximateFirstReceiveTimestamp() {
        return attributes.get("ApproximateFirstReceiveTimestamp");
    }
    
    /**
     * 메시지가 SQS에 전송된 시간을 반환합니다.
     * 
     * <p>Unix timestamp 형식으로 반환되며, 메시지가 큐에 추가된 정확한 시점을 나타냅니다.
     * 메시지 수명이나 성능 모니터링에 활용할 수 있습니다.</p>
     * 
     * @return Unix timestamp 문자열 (예: "1640995200000")
     */
    public String getSentTimestamp() {
        return attributes.get("SentTimestamp");
    }
    
    /**
     * 메시지를 전송한 AWS 사용자의 ID를 반환합니다.
     * 
     * <p>IAM 사용자 ID 또는 AWS 계정 ID 형식으로 반환됩니다.
     * 메시지 출처를 추적하거나 보안 감사에 사용할 수 있습니다.</p>
     * 
     * @return 메시지 전송자 ID (예: "AIDACKCEVSQ6C2EXAMPLE")
     */
    public String getSenderId() {
        return attributes.get("SenderId");
    }
    
    /**
     * SqsMessage 객체를 생성하기 위한 Builder 인스턴스를 반환합니다.
     * 
     * <p>Builder 패턴을 사용하여 단계적으로 SqsMessage 객체를 생성할 수 있습니다.</p>
     * 
     * @return SqsMessage.Builder 인스턴스
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * SqsMessage message = SqsMessage.builder()
     *     .messageId("msg-123")
     *     .body("{\"userId\": 123}")
     *     .receiptHandle("receipt-handle")
     *     .timestamp(Instant.now())
     *     .build();
     * </code></pre>
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private String messageId;
        private String body;
        private String receiptHandle;
        private String md5OfBody;
        private String md5OfMessageAttributes;
        private Map<String, String> attributes;
        private Map<String, SqsMessageAttribute> messageAttributes;
        private Instant timestamp;
        
        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }
        
        public Builder body(String body) {
            this.body = body;
            return this;
        }
        
        public Builder receiptHandle(String receiptHandle) {
            this.receiptHandle = receiptHandle;
            return this;
        }
        
        public Builder md5OfBody(String md5OfBody) {
            this.md5OfBody = md5OfBody;
            return this;
        }
        
        public Builder md5OfMessageAttributes(String md5OfMessageAttributes) {
            this.md5OfMessageAttributes = md5OfMessageAttributes;
            return this;
        }
        
        public Builder attributes(Map<String, String> attributes) {
            this.attributes = attributes;
            return this;
        }
        
        public Builder messageAttributes(Map<String, SqsMessageAttribute> messageAttributes) {
            this.messageAttributes = messageAttributes;
            return this;
        }
        
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public SqsMessage build() {
            return new SqsMessage(this);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqsMessage that = (SqsMessage) o;
        return Objects.equals(messageId, that.messageId) &&
               Objects.equals(body, that.body) &&
               Objects.equals(receiptHandle, that.receiptHandle) &&
               Objects.equals(md5OfBody, that.md5OfBody) &&
               Objects.equals(md5OfMessageAttributes, that.md5OfMessageAttributes) &&
               Objects.equals(attributes, that.attributes) &&
               Objects.equals(messageAttributes, that.messageAttributes) &&
               Objects.equals(timestamp, that.timestamp);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(messageId, body, receiptHandle, md5OfBody, md5OfMessageAttributes, 
                          attributes, messageAttributes, timestamp);
    }
    
    @Override
    public String toString() {
        return "SqsMessage{" +
               "messageId='" + messageId + '\'' +
               ", bodyLength=" + (body != null ? body.length() : 0) +
               ", receiptHandle='" + receiptHandle + '\'' +
               ", attributeCount=" + attributes.size() +
               ", messageAttributeCount=" + messageAttributes.size() +
               ", timestamp=" + timestamp +
               '}';
    }
}