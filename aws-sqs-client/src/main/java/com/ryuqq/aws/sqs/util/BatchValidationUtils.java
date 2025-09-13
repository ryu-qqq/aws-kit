package com.ryuqq.aws.sqs.util;

import com.ryuqq.aws.sqs.properties.SqsProperties;

import java.util.Collection;

/**
 * AWS SQS 배치 작업 검증을 위한 유틸리티 클래스
 * 
 * <p>SQS 서비스의 배치 작업에서 공통으로 사용되는 검증 로직을 제공합니다.
 * 중복 코드를 제거하고 일관성 있는 검증을 보장합니다.</p>
 * 
 * <h3>제공하는 검증 기능:</h3>
 * <ul>
 *   <li><strong>배치 크기 검증</strong>: AWS SQS 최대 배치 크기 제한 확인</li>
 *   <li><strong>빈 컬렉션 검증</strong>: null 또는 empty 컬렉션 확인</li>
 *   <li><strong>컬렉션 유효성 검증</strong>: null 요소 포함 여부 확인</li>
 * </ul>
 * 
 * <h3>AWS SQS 배치 제한사항:</h3>
 * <ul>
 *   <li>한 번에 최대 10개 요소까지 처리 가능</li>
 *   <li>전체 배치 페이로드는 최대 256KB</li>
 *   <li>각 요소는 고유한 ID를 가져야 함</li>
 * </ul>
 * 
 * @since 1.0.0
 * @see SqsProperties SQS 설정 프로퍼티
 */
public final class BatchValidationUtils {
    
    private BatchValidationUtils() {
        // 유틸리티 클래스 - 인스턴스 생성 방지
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스를 생성할 수 없습니다");
    }
    
    /**
     * 배치 컬렉션의 크기가 SQS 최대 배치 크기를 초과하는지 검증합니다.
     * 
     * <p>AWS SQS는 한 번의 배치 작업에 최대 10개의 요소만 처리할 수 있습니다.
     * 이를 초과하는 경우 IllegalArgumentException이 발생합니다.</p>
     * 
     * @param collection 검증할 컬렉션 (null 허용)
     * @param sqsProperties SQS 설정 프로퍼티 (배치 크기 설정 포함)
     * @param operationName 작업명 (예외 메시지에 포함됨)
     * @throws IllegalArgumentException 배치 크기가 최대값을 초과할 때
     * @throws IllegalArgumentException 필수 매개변수가 null일 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * List&lt;String&gt; messages = Arrays.asList("msg1", "msg2", "msg3");
     * BatchValidationUtils.validateBatchSize(messages, sqsProperties, "sendMessageBatch");
     * 
     * // 크기 초과 시: IllegalArgumentException("sendMessageBatch: 배치 크기는 최대 10개를 초과할 수 없습니다")
     * </code></pre>
     */
    public static void validateBatchSize(Collection<?> collection, SqsProperties sqsProperties, String operationName) {
        if (sqsProperties == null) {
            throw new IllegalArgumentException("SqsProperties는 null일 수 없습니다");
        }
        
        if (operationName == null || operationName.trim().isEmpty()) {
            throw new IllegalArgumentException("작업명은 null이거나 빈 문자열일 수 없습니다");
        }
        
        if (collection == null) {
            return; // null 컬렉션은 크기 검증을 통과
        }
        
        int maxBatchSize = sqsProperties.getMaxBatchSize();
        if (collection.size() > maxBatchSize) {
            throw new IllegalArgumentException(
                String.format("%s: 배치 크기는 최대 %d개를 초과할 수 없습니다. 현재: %d개", 
                    operationName, maxBatchSize, collection.size())
            );
        }
    }
    
    /**
     * 컬렉션이 비어있는지 검증합니다.
     * 
     * <p>배치 작업에서는 처리할 요소가 없으면 의미가 없으므로, 
     * 빈 컬렉션에 대한 조기 검출이 필요합니다.</p>
     * 
     * @param collection 검증할 컬렉션
     * @param operationName 작업명 (예외 메시지에 포함됨)
     * @return 컬렉션이 비어있으면 true, 그렇지 않으면 false
     * @throws IllegalArgumentException 작업명이 null이거나 빈 문자열일 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * if (BatchValidationUtils.isEmpty(messages, "sendMessageBatch")) {
     *     log.warn("전송할 메시지가 없습니다");
     *     return CompletableFuture.completedFuture(Collections.emptyList());
     * }
     * </code></pre>
     */
    public static boolean isEmpty(Collection<?> collection, String operationName) {
        if (operationName == null || operationName.trim().isEmpty()) {
            throw new IllegalArgumentException("작업명은 null이거나 빈 문자열일 수 없습니다");
        }
        
        return collection == null || collection.isEmpty();
    }
    
    /**
     * 컬렉션에 null 요소가 포함되어 있는지 검증합니다.
     * 
     * <p>AWS SQS API는 null 값을 허용하지 않으므로, 배치 작업 전에 
     * null 요소를 확인하여 런타임 오류를 방지합니다.</p>
     * 
     * @param collection 검증할 컬렉션 (null 허용)
     * @param operationName 작업명 (예외 메시지에 포함됨)
     * @throws IllegalArgumentException null 요소가 발견될 때
     * @throws IllegalArgumentException 작업명이 null이거나 빈 문자열일 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * List&lt;String&gt; messages = Arrays.asList("msg1", null, "msg3");
     * BatchValidationUtils.validateNoNullElements(messages, "sendMessageBatch");
     * // IllegalArgumentException: sendMessageBatch: 컬렉션에 null 요소가 포함될 수 없습니다 (인덱스: 1)
     * </code></pre>
     */
    public static void validateNoNullElements(Collection<?> collection, String operationName) {
        if (operationName == null || operationName.trim().isEmpty()) {
            throw new IllegalArgumentException("작업명은 null이거나 빈 문자열일 수 없습니다");
        }
        
        if (collection == null) {
            return; // null 컬렉션은 null 요소 검증을 통과
        }
        
        int index = 0;
        for (Object element : collection) {
            if (element == null) {
                throw new IllegalArgumentException(
                    String.format("%s: 컬렉션에 null 요소가 포함될 수 없습니다 (인덱스: %d)", operationName, index)
                );
            }
            index++;
        }
    }
    
    /**
     * 문자열 컬렉션에서 빈 문자열이나 공백만 포함된 문자열을 검증합니다.
     * 
     * <p>SQS 메시지 본문이나 Receipt Handle과 같은 문자열 값은 
     * 의미 있는 내용을 포함해야 합니다.</p>
     * 
     * @param stringCollection 검증할 문자열 컬렉션 (null 허용)
     * @param operationName 작업명 (예외 메시지에 포함됨)
     * @throws IllegalArgumentException 빈 문자열이나 공백 문자열이 발견될 때
     * @throws IllegalArgumentException 작업명이 null이거나 빈 문자열일 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * List&lt;String&gt; receiptHandles = Arrays.asList("handle1", "", "  ", "handle4");
     * BatchValidationUtils.validateNoBlankStrings(receiptHandles, "deleteMessageBatch");
     * // IllegalArgumentException: deleteMessageBatch: 빈 문자열이나 공백 문자열이 포함될 수 없습니다 (인덱스: 1)
     * </code></pre>
     */
    public static void validateNoBlankStrings(Collection<String> stringCollection, String operationName) {
        if (operationName == null || operationName.trim().isEmpty()) {
            throw new IllegalArgumentException("작업명은 null이거나 빈 문자열일 수 없습니다");
        }
        
        if (stringCollection == null) {
            return; // null 컬렉션은 빈 문자열 검증을 통과
        }
        
        int index = 0;
        for (String element : stringCollection) {
            if (element == null || element.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("%s: 빈 문자열이나 공백 문자열이 포함될 수 없습니다 (인덱스: %d)", operationName, index)
                );
            }
            index++;
        }
    }
    
    /**
     * 배치 작업을 위한 포괄적인 검증을 수행합니다.
     * 
     * <p>이 메서드는 배치 작업에 필요한 모든 기본 검증을 한 번에 수행하는 편의 메서드입니다:</p>
     * <ul>
     *   <li>배치 크기 검증</li>
     *   <li>null 요소 검증</li>
     *   <li>빈 컬렉션 검증 (선택적)</li>
     * </ul>
     * 
     * @param collection 검증할 컬렉션
     * @param sqsProperties SQS 설정 프로퍼티
     * @param operationName 작업명
     * @param allowEmpty 빈 컬렉션 허용 여부 (true면 빈 컬렉션 허용, false면 예외 발생)
     * @throws IllegalArgumentException 검증 실패 시
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * // 빈 컬렉션을 허용하지 않는 경우
     * BatchValidationUtils.validateForBatchOperation(messages, sqsProperties, "sendMessageBatch", false);
     * 
     * // 빈 컬렉션을 허용하는 경우 (조기 반환 처리용)
     * if (BatchValidationUtils.validateForBatchOperation(messages, sqsProperties, "sendMessageBatch", true)) {
     *     return CompletableFuture.completedFuture(Collections.emptyList());
     * }
     * </code></pre>
     */
    public static boolean validateForBatchOperation(Collection<?> collection, SqsProperties sqsProperties, 
                                                   String operationName, boolean allowEmpty) {
        // 빈 컬렉션 검증
        if (isEmpty(collection, operationName)) {
            if (!allowEmpty) {
                throw new IllegalArgumentException(
                    String.format("%s: 처리할 요소가 없습니다. 최소 1개 이상의 요소가 필요합니다", operationName)
                );
            }
            return true; // 빈 컬렉션이 허용되는 경우 조기 반환 신호
        }
        
        // 배치 크기 검증
        validateBatchSize(collection, sqsProperties, operationName);
        
        // null 요소 검증
        validateNoNullElements(collection, operationName);
        
        return false; // 정상적인 처리 계속 신호
    }
}