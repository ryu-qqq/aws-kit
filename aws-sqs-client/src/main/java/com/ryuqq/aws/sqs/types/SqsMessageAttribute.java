package com.ryuqq.aws.sqs.types;

import java.util.Objects;

/**
 * AWS Kit SQS 라이브러리의 메시지 속성 추상화 타입
 * 
 * <p>SQS 메시지에 추가할 수 있는 커스텀 속성을 나타냅니다.
 * AWS SDK의 MessageAttributeValue 타입을 대체하여 public API에서 AWS SDK 의존성을 숨깁니다.</p>
 * 
 * <h3>AWS SQS 메시지 속성 특징:</h3>
 * <ul>
 *   <li><strong>데이터 타입 지원</strong>: String, Number, Binary 타입</li>
 *   <li><strong>속성 제한</strong>: 메시지당 최대 10개 속성</li>
 *   <li><strong>이름 제한</strong>: 속성 이름 최대 256자</li>
 *   <li><strong>값 제한</strong>: 속성 값 최대 256KB</li>
 * </ul>
 * 
 * <h3>지원하는 데이터 타입:</h3>
 * <ul>
 *   <li><strong>STRING</strong>: UTF-8 문자열 데이터</li>
 *   <li><strong>NUMBER</strong>: 숫자 데이터 (문자열로 저장)</li>
 *   <li><strong>BINARY</strong>: 이진 데이터 (byte 배열)</li>
 * </ul>
 * 
 * @since 1.0.0
 * @see SqsMessage 라이브러리의 메시지 타입
 * @see DataType 지원하는 데이터 타입 enum
 */
public final class SqsMessageAttribute {
    
    /**
     * AWS SQS 메시지 속성에서 지원하는 데이터 타입을 나타내는 enum
     * 
     * <p>AWS SQS는 메시지 속성에 대해 세 가지 기본 데이터 타입을 지원합니다.</p>
     * 
     * <h4>각 타입별 특징 및 사용법:</h4>
     * <ul>
     *   <li><strong>STRING</strong>: 대부분의 텍스트 데이터, JSON 문자열 등</li>
     *   <li><strong>NUMBER</strong>: 숫자값, 모든 숫자는 문자열로 저장됨</li>
     *   <li><strong>BINARY</strong>: 이미지, 파일, 암호화된 데이터 등</li>
     * </ul>
     */
    public enum DataType {
        /** UTF-8 문자열 데이터 타입 */
        STRING("String"),
        /** 숫자 데이터 타입 (문자열로 저장) */
        NUMBER("Number"),
        /** 이진 데이터 타입 */
        BINARY("Binary");
        
        private final String value;
        
        DataType(String value) {
            this.value = value;
        }
        
        /**
         * AWS SQS API에서 사용하는 데이터 타입 문자연 값을 반환합니다.
         * 
         * @return AWS SQS API에서 인식하는 타입 문자열
         */
        public String getValue() {
            return value;
        }
        
        /**
         * AWS SQS API 문자열 값으로부터 해당하는 DataType enum을 찾아 반환합니다.
         * 
         * <p>이 메서드는 AWS SDK로부터 데이터 타입 문자열을 수신했을 때
         * 라이브러리 enum으로 변환하기 위해 사용됩니다.</p>
         * 
         * @param value AWS SQS API 데이터 타입 문자열 ("String", "Number", "Binary")
         * @return 해당하는 DataType enum 상수
         * @throws IllegalArgumentException 알 수 없는 데이터 타입인 경우
         */
        public static DataType fromValue(String value) {
            for (DataType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown data type: " + value);
        }
    }
    
    private final String stringValue;
    private final byte[] binaryValue;
    private final DataType dataType;
    
    private SqsMessageAttribute(String stringValue, byte[] binaryValue, DataType dataType) {
        this.stringValue = stringValue;
        this.binaryValue = binaryValue;
        this.dataType = dataType;
    }
    
    /**
     * 문자열 타입 또는 숫자 타입 속성의 문자열 값을 반환합니다.
     * 
     * <p>STRING 및 NUMBER 데이터 타입의 경우에만 유효한 값을 가집니다.
     * BINARY 타입의 경우 null을 반환합니다.</p>
     * 
     * @return 문자열 값 (BINARY 타입인 경우 null)
     */
    public String getStringValue() {
        return stringValue;
    }
    
    /**
     * 이진 타입 속성의 바이트 배열 값을 반환합니다.
     * 
     * <p>BINARY 데이터 타입의 경우에만 유효한 값을 가집니다.
     * STRING 및 NUMBER 타입의 경우 null을 반환합니다.</p>
     * 
     * @return 바이트 배열 값 (STRING/NUMBER 타입인 경우 null)
     */
    public byte[] getBinaryValue() {
        return binaryValue;
    }
    
    public DataType getDataType() {
        return dataType;
    }
    
    /**
     * 문자열 데이터를 가진 SqsMessageAttribute를 생성합니다.
     * 
     * <p>일반적인 텍스트 데이터, JSON 문자열, ID 값 등에 사용합니다.
     * UTF-8 인코딩이 지원되며, 최대 256KB까지 저장할 수 있습니다.</p>
     * 
     * @param value 저장할 문자열 값 (null 불가)
     * @return STRING 타입의 SqsMessageAttribute 인스턴스
     * @throws NullPointerException value가 null인 경우
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * SqsMessageAttribute attr = SqsMessageAttribute.stringAttribute("user-123");
     * SqsMessageAttribute jsonAttr = SqsMessageAttribute.stringAttribute("{\"key\":\"value\"}");
     * </code></pre>
     */
    public static SqsMessageAttribute stringAttribute(String value) {
        Objects.requireNonNull(value, "String value cannot be null");
        return new SqsMessageAttribute(value, null, DataType.STRING);
    }
    
    /**
     * 문자열 형태의 숫자 데이터를 가진 SqsMessageAttribute를 생성합니다.
     * 
     * <p>이미 문자열로 변환된 숫자 데이터를 사용할 때 활용합니다.
     * AWS SQS에서 숫자는 내부적으로 문자열로 저장되지만,
     * 메타데이터로 숫자 타입임을 표시합니다.</p>
     * 
     * @param value 저장할 숫자 문자열 (null 불가)
     * @return NUMBER 타입의 SqsMessageAttribute 인스턴스
     * @throws NullPointerException value가 null인 경우
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * SqsMessageAttribute attr = SqsMessageAttribute.numberAttribute("123");
     * SqsMessageAttribute priceAttr = SqsMessageAttribute.numberAttribute("99.99");
     * </code></pre>
     */
    public static SqsMessageAttribute numberAttribute(String value) {
        Objects.requireNonNull(value, "Number value cannot be null");
        return new SqsMessageAttribute(value, null, DataType.NUMBER);
    }
    
    /**
     * Number 객체로부터 숫자 데이터를 가진 SqsMessageAttribute를 생성합니다.
     * 
     * <p>Integer, Long, Double, BigDecimal 등 다양한 Number 타입을 지원합니다.
     * Number 객체는 toString() 메서드를 통해 문자열로 변환되어 저장됩니다.</p>
     * 
     * @param value 저장할 Number 객체 (null 불가)
     * @return NUMBER 타입의 SqsMessageAttribute 인스턴스
     * @throws NullPointerException value가 null인 경우
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * SqsMessageAttribute countAttr = SqsMessageAttribute.numberAttribute(42);
     * SqsMessageAttribute priceAttr = SqsMessageAttribute.numberAttribute(99.99);
     * SqsMessageAttribute longAttr = SqsMessageAttribute.numberAttribute(123456789L);
     * </code></pre>
     */
    public static SqsMessageAttribute numberAttribute(Number value) {
        Objects.requireNonNull(value, "Number value cannot be null");
        return new SqsMessageAttribute(value.toString(), null, DataType.NUMBER);
    }
    
    /**
     * 바이트 배열 데이터를 가진 SqsMessageAttribute를 생성합니다.
     * 
     * <p>이미지, 파일, 암호화된 데이터 등 이진 데이터를 저장할 때 사용합니다.
     * 데이터의 무결성을 위해 내부적으로 배열을 복사하여 저장합니다.</p>
     * 
     * <h4>AWS SQS BINARY 데이터 제한:</h4>
     * <ul>
     *   <li>최대 크기: 256KB</li>
     *   <li>Base64 인코딩으로 전송됨</li>
     *   <li>빈 배열도 유효한 데이터</li>
     * </ul>
     * 
     * @param value 저장할 바이트 배열 (null 불가, 빈 배열 가능)
     * @return BINARY 타입의 SqsMessageAttribute 인스턴스
     * @throws NullPointerException value가 null인 경우
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * byte[] imageData = Files.readAllBytes(Paths.get("image.jpg"));
     * SqsMessageAttribute imageAttr = SqsMessageAttribute.binaryAttribute(imageData);
     * 
     * byte[] encryptedData = encrypt("secret data");
     * SqsMessageAttribute secureAttr = SqsMessageAttribute.binaryAttribute(encryptedData);
     * </code></pre>
     */
    public static SqsMessageAttribute binaryAttribute(byte[] value) {
        Objects.requireNonNull(value, "Binary value cannot be null");
        return new SqsMessageAttribute(null, value.clone(), DataType.BINARY);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SqsMessageAttribute that = (SqsMessageAttribute) o;
        return Objects.equals(stringValue, that.stringValue) &&
               Objects.deepEquals(binaryValue, that.binaryValue) &&
               dataType == that.dataType;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(stringValue, Objects.hashCode(binaryValue), dataType);
    }
    
    @Override
    public String toString() {
        return "SqsMessageAttribute{" +
               "dataType=" + dataType +
               ", hasStringValue=" + (stringValue != null) +
               ", hasBinaryValue=" + (binaryValue != null) +
               '}';
    }
}