package com.ryuqq.aws.sqs.adapter;

import com.ryuqq.aws.sqs.types.SqsMessage;
import com.ryuqq.aws.sqs.types.SqsMessageAttribute;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AWS SDK 타입과 라이브러리 타입 간의 변환을 담당하는 어댑터 클래스
 * 
 * <p>이 클래스는 AWS Kit 라이브러리의 핵심 설계 원칙을 구현합니다:</p>
 * <ul>
 *   <li><strong>타입 추상화</strong>: AWS SDK 타입을 라이브러리 고유 타입으로 변환</li>
 *   <li><strong>의존성 캡슐화</strong>: AWS SDK 의존성을 public API에서 숨김</li>
 *   <li><strong>타입 안전성</strong>: 컴파일 타임에 타입 안전성 보장</li>
 * </ul>
 * 
 * <h3>주요 변환 작업:</h3>
 * <ul>
 *   <li>AWS Message ↔ SqsMessage</li>
 *   <li>AWS MessageAttributeValue ↔ SqsMessageAttribute</li>
 *   <li>AWS MessageSystemAttributeName → String Map</li>
 * </ul>
 * 
 * <h3>변환 과정의 특징:</h3>
 * <ul>
 *   <li><strong>완전한 데이터 보존</strong>: 모든 AWS 메타데이터 유지</li>
 *   <li><strong>null 안전성</strong>: null 입력에 대한 안전한 처리</li>
 *   <li><strong>불변 객체</strong>: 변환된 객체는 불변성 보장</li>
 * </ul>
 * 
 * @since 1.0.0
 * @see SqsMessage AWS Kit의 메시지 추상화 타입
 * @see SqsMessageAttribute AWS Kit의 메시지 속성 추상화 타입
 */
public final class SqsTypeAdapter {
    
    private SqsTypeAdapter() {
        // Utility class
    }
    
    /**
     * AWS SDK Message 객체를 라이브러리의 SqsMessage로 변환합니다.
     * 
     * <p>이 메서드는 AWS SQS에서 수신한 메시지의 모든 정보를 보존하면서
     * 라이브러리 고유의 타입으로 변환합니다.</p>
     * 
     * <h4>변환되는 주요 정보:</h4>
     * <ul>
     *   <li><strong>기본 정보</strong>: messageId, body, receiptHandle</li>
     *   <li><strong>무결성 검증</strong>: MD5 체크섬 (body, messageAttributes)</li>
     *   <li><strong>시스템 속성</strong>: AWS가 자동 설정하는 메타데이터</li>
     *   <li><strong>사용자 속성</strong>: 개발자가 설정한 커스텀 속성</li>
     * </ul>
     * 
     * <h4>주요 시스템 속성:</h4>
     * <ul>
     *   <li>ApproximateReceiveCount: 메시지 수신 횟수</li>
     *   <li>SentTimestamp: 메시지 전송 시각</li>
     *   <li>ApproximateFirstReceiveTimestamp: 최초 수신 시각</li>
     *   <li>SenderId: 메시지 전송자 ID</li>
     * </ul>
     * 
     * @param awsMessage AWS SDK의 Message 객체 (null 허용)
     * @return 변환된 SqsMessage 객체 (awsMessage가 null이면 null 반환)
     */
    public static SqsMessage fromAwsMessage(Message awsMessage) {
        if (awsMessage == null) {
            return null;
        }
        
        // 기본 메시지 정보로 빌더 초기화
        SqsMessage.Builder builder = createBaseMessageBuilder(awsMessage);
        
        // 메시지 속성 처리 (있는 경우에만)
        if (awsMessage.hasMessageAttributes()) {
            Map<String, SqsMessageAttribute> messageAttributes = convertMessageAttributes(awsMessage.messageAttributes());
            builder.messageAttributes(messageAttributes);
        }
        
        return builder.build();
    }
    
    /**
     * AWS Message의 기본 정보로부터 SqsMessage.Builder를 생성합니다.
     * 
     * <p>메시지의 핵심 정보(ID, 본문, Receipt Handle, MD5 해시, 시스템 속성)를 
     * 추출하여 빌더 객체를 구성합니다.</p>
     * 
     * @param awsMessage AWS SDK Message 객체 (non-null)
     * @return 기본 정보가 설정된 SqsMessage.Builder
     */
    private static SqsMessage.Builder createBaseMessageBuilder(Message awsMessage) {
        return SqsMessage.builder()
                .messageId(awsMessage.messageId())
                .body(awsMessage.body())
                .receiptHandle(awsMessage.receiptHandle())
                .md5OfBody(awsMessage.md5OfBody())
                .md5OfMessageAttributes(awsMessage.md5OfMessageAttributes())
                .attributes(convertAwsAttributesToStringMap(awsMessage.attributes()))
                .timestamp(Instant.now()); // AWS는 직접적인 timestamp를 제공하지 않음
    }
    
    /**
     * AWS MessageAttributeValue 맵을 라이브러리 SqsMessageAttribute 맵으로 변환합니다.
     * 
     * <p>AWS SDK의 MessageAttributeValue 맵을 순회하면서 각 속성을 
     * 라이브러리의 SqsMessageAttribute 타입으로 변환합니다.</p>
     * 
     * @param awsMessageAttributes AWS SDK MessageAttributeValue 맵 (non-null, non-empty)
     * @return 변환된 SqsMessageAttribute 맵
     */
    private static Map<String, SqsMessageAttribute> convertMessageAttributes(
            Map<String, MessageAttributeValue> awsMessageAttributes) {
        Map<String, SqsMessageAttribute> messageAttributes = new HashMap<>();
        
        for (Map.Entry<String, MessageAttributeValue> entry : awsMessageAttributes.entrySet()) {
            String attributeName = entry.getKey();
            MessageAttributeValue attributeValue = entry.getValue();
            
            // null 값 체크 후 변환
            if (attributeValue != null) {
                SqsMessageAttribute convertedAttribute = fromAwsMessageAttributeValue(attributeValue);
                if (convertedAttribute != null) {
                    messageAttributes.put(attributeName, convertedAttribute);
                }
            }
        }
        
        return messageAttributes;
    }
    
    /**
     * AWS SDK Message 목록을 라이브러리 SqsMessage 목록으로 변환합니다.
     * 
     * <p>배치 수신된 메시지들을 일괄 변환하는 편의 메서드입니다.
     * 각 메시지는 개별적으로 변환되며, null 또는 empty 목록에 대해서도 안전하게 처리됩니다.</p>
     * 
     * @param awsMessages AWS SDK Message 목록 (null 허용)
     * @return 변환된 SqsMessage 목록 (awsMessages가 null이면 빈 목록 반환)
     */
    public static List<SqsMessage> fromAwsMessages(List<Message> awsMessages) {
        if (awsMessages == null) {
            return List.of();
        }
        
        return awsMessages.stream()
                .map(SqsTypeAdapter::fromAwsMessage)
                .collect(Collectors.toList());
    }
    
    /**
     * 라이브러리의 SqsMessageAttribute를 AWS SDK MessageAttributeValue로 변환합니다.
     * 
     * <p>메시지 전송 시 사용자가 설정한 커스텀 속성을 AWS SDK 형식으로 변환합니다.
     * 데이터 타입에 따라 적절한 AWS SDK 필드에 값을 설정합니다.</p>
     * 
     * <h4>지원하는 데이터 타입과 변환 규칙:</h4>
     * <ul>
     *   <li><strong>STRING</strong>: stringValue 필드에 저장</li>
     *   <li><strong>NUMBER</strong>: stringValue 필드에 문자열로 저장</li>
     *   <li><strong>BINARY</strong>: binaryValue 필드에 SdkBytes로 저장</li>
     * </ul>
     * 
     * @param attribute 변환할 SqsMessageAttribute 객체 (null 허용)
     * @return 변환된 AWS SDK MessageAttributeValue (attribute가 null이면 null 반환)
     * @throws IllegalArgumentException 지원하지 않는 데이터 타입인 경우
     */
    public static MessageAttributeValue toAwsMessageAttributeValue(SqsMessageAttribute attribute) {
        if (attribute == null) {
            return null;
        }
        
        MessageAttributeValue.Builder builder = MessageAttributeValue.builder()
                .dataType(attribute.getDataType().getValue());
        
        switch (attribute.getDataType()) {
            case STRING:
            case NUMBER:
                builder.stringValue(attribute.getStringValue());
                break;
            case BINARY:
                builder.binaryValue(software.amazon.awssdk.core.SdkBytes.fromByteArray(attribute.getBinaryValue()));
                break;
            default:
                throw new IllegalArgumentException("Unsupported data type: " + attribute.getDataType());
        }
        
        return builder.build();
    }
    
    /**
     * AWS SDK MessageAttributeValue를 라이브러리의 SqsMessageAttribute로 변환합니다.
     * 
     * <p>SQS에서 수신한 메시지 속성을 라이브러리 타입으로 변환합니다.
     * AWS SDK의 데이터 타입 정보를 기반으로 적절한 SqsMessageAttribute를 생성합니다.</p>
     * 
     * <h4>AWS SDK 데이터 타입 매핑:</h4>
     * <ul>
     *   <li><strong>"String"</strong> → SqsMessageAttribute.stringAttribute()</li>
     *   <li><strong>"Number"</strong> → SqsMessageAttribute.numberAttribute()</li>
     *   <li><strong>"Binary"</strong> → SqsMessageAttribute.binaryAttribute()</li>
     * </ul>
     * 
     * @param awsAttribute 변환할 AWS SDK MessageAttributeValue (null 허용)
     * @return 변환된 SqsMessageAttribute (awsAttribute가 null이면 null 반환)
     */
    public static SqsMessageAttribute fromAwsMessageAttributeValue(MessageAttributeValue awsAttribute) {
        if (awsAttribute == null) {
            return null;
        }
        
        SqsMessageAttribute.DataType dataType = SqsMessageAttribute.DataType.fromValue(awsAttribute.dataType());

        return switch (dataType) {
            case STRING -> SqsMessageAttribute.stringAttribute(awsAttribute.stringValue());
            case NUMBER -> SqsMessageAttribute.numberAttribute(awsAttribute.stringValue());
            case BINARY -> SqsMessageAttribute.binaryAttribute(awsAttribute.binaryValue().asByteArray());
        };
    }
    
    /**
     * 라이브러리 SqsMessageAttribute 맵을 AWS SDK MessageAttributeValue 맵으로 변환합니다.
     * 
     * <p>메시지 전송 시 여러 커스텀 속성을 AWS SDK 형식으로 일괄 변환합니다.
     * 각 속성은 개별적으로 변환되며, null 또는 empty 맵에 대해서도 안전하게 처리됩니다.</p>
     * 
     * @param attributes 변환할 SqsMessageAttribute 맵 (null 또는 empty 허용)
     * @return 변환된 AWS SDK MessageAttributeValue 맵 (null/empty 입력 시 빈 맵 반환)
     */
    public static Map<String, MessageAttributeValue> toAwsMessageAttributes(Map<String, SqsMessageAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        
        Map<String, MessageAttributeValue> awsAttributes = new HashMap<>();
        for (Map.Entry<String, SqsMessageAttribute> entry : attributes.entrySet()) {
            awsAttributes.put(entry.getKey(), toAwsMessageAttributeValue(entry.getValue()));
        }
        
        return awsAttributes;
    }
    
    /**
     * AWS SDK MessageAttributeValue 맵을 라이브러리 SqsMessageAttribute 맵으로 변환합니다.
     * 
     * <p>SQS에서 수신한 여러 메시지 속성을 라이브러리 타입으로 일괄 변환합니다.
     * 각 속성은 개별적으로 변환되며, null 또는 empty 맵에 대해서도 안전하게 처리됩니다.</p>
     * 
     * @param awsAttributes 변환할 AWS SDK MessageAttributeValue 맵 (null 또는 empty 허용)
     * @return 변환된 SqsMessageAttribute 맵 (null/empty 입력 시 빈 맵 반환)
     */
    public static Map<String, SqsMessageAttribute> fromAwsMessageAttributes(Map<String, MessageAttributeValue> awsAttributes) {
        if (awsAttributes == null || awsAttributes.isEmpty()) {
            return Map.of();
        }
        
        Map<String, SqsMessageAttribute> attributes = new HashMap<>();
        for (Map.Entry<String, MessageAttributeValue> entry : awsAttributes.entrySet()) {
            attributes.put(entry.getKey(), fromAwsMessageAttributeValue(entry.getValue()));
        }
        
        return attributes;
    }
    
    /**
     * AWS SDK MessageSystemAttributeName 맵을 문자열 맵으로 변환합니다.
     * 
     * <p>AWS가 자동으로 설정하는 시스템 속성들을 사용하기 쉬운 문자열 맵으로 변환합니다.
     * 이 변환을 통해 라이브러리 사용자는 AWS SDK의 enum 타입을 알지 못해도
     * 시스템 속성에 접근할 수 있습니다.</p>
     * 
     * <h4>주요 시스템 속성 예시:</h4>
     * <ul>
     *   <li><strong>ApproximateReceiveCount</strong>: "3" (메시지가 3번 수신됨)</li>
     *   <li><strong>SentTimestamp</strong>: "1640995200000" (Unix timestamp)</li>
     *   <li><strong>SenderId</strong>: "AIDACKCEVSQ6C2EXAMPLE" (IAM 사용자 ID)</li>
     * </ul>
     * 
     * @param awsAttributes AWS SDK MessageSystemAttributeName 맵 (null 또는 empty 허용)
     * @return 문자열 키-값 맵 (null/empty 입력 시 빈 맵 반환)
     */
    private static Map<String, String> convertAwsAttributesToStringMap(Map<MessageSystemAttributeName, String> awsAttributes) {
        if (awsAttributes == null || awsAttributes.isEmpty()) {
            return Map.of();
        }
        
        Map<String, String> stringAttributes = new HashMap<>();
        for (Map.Entry<MessageSystemAttributeName, String> entry : awsAttributes.entrySet()) {
            stringAttributes.put(entry.getKey().toString(), entry.getValue());
        }
        
        return stringAttributes;
    }
}