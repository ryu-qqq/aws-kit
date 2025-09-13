package com.ryuqq.aws.sqs.util;

import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Collectors;

/**
 * AWS SQS 배치 Entry 생성을 위한 팩토리 클래스
 * 
 * <p>SQS 배치 작업에서 사용되는 RequestEntry 객체들의 생성 로직을 중앙화합니다.
 * 공통 패턴을 추출하여 코드 중복을 제거하고 일관성을 보장합니다.</p>
 * 
 * <h3>지원하는 배치 Entry 타입:</h3>
 * <ul>
 *   <li><strong>SendMessageBatchRequestEntry</strong>: 메시지 전송 배치용</li>
 *   <li><strong>DeleteMessageBatchRequestEntry</strong>: 메시지 삭제 배치용</li>
 * </ul>
 * 
 * <h3>팩토리 패턴의 장점:</h3>
 * <ul>
 *   <li><strong>일관성</strong>: 동일한 ID 생성 규칙 적용</li>
 *   <li><strong>재사용성</strong>: 다양한 배치 작업에서 공통 사용</li>
 *   <li><strong>유지보수성</strong>: Entry 생성 로직의 중앙 관리</li>
 *   <li><strong>확장성</strong>: 새로운 Entry 타입 추가 용이</li>
 * </ul>
 * 
 * @since 1.0.0
 * @see SendMessageBatchRequestEntry
 * @see DeleteMessageBatchRequestEntry
 */
public final class BatchEntryFactory {
    
    private BatchEntryFactory() {
        // 팩토리 클래스 - 인스턴스 생성 방지
        throw new UnsupportedOperationException("팩토리 클래스는 인스턴스를 생성할 수 없습니다");
    }
    
    /**
     * 메시지 전송 배치를 위한 SendMessageBatchRequestEntry 목록을 생성합니다.
     * 
     * <p>메시지 목록을 AWS SQS SendMessageBatch API에서 요구하는 형식으로 변환합니다.
     * 각 메시지에는 배치 내에서 고유한 ID가 자동으로 할당됩니다.</p>
     * 
     * <h4>Entry ID 생성 규칙:</h4>
     * <ul>
     *   <li>0부터 시작하는 순차적인 인덱스 사용</li>
     *   <li>문자열 형태로 변환하여 AWS API 요구사항 충족</li>
     *   <li>배치 내에서 중복되지 않는 고유성 보장</li>
     * </ul>
     * 
     * @param messages 전송할 메시지 본문 목록 (null이 아니고 비어있지 않아야 함)
     * @return AWS SQS SendMessageBatch API용 Entry 목록
     * @throws IllegalArgumentException 메시지 목록이 null이거나 비어있을 때
     * @throws IllegalArgumentException 메시지 목록에 null 요소가 포함될 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * List&lt;String&gt; messages = Arrays.asList(
     *     "{\"orderId\": 1, \"status\": \"pending\"}",
     *     "{\"orderId\": 2, \"status\": \"confirmed\"}"
     * );
     * 
     * List&lt;SendMessageBatchRequestEntry&gt; entries = 
     *     BatchEntryFactory.createSendMessageEntries(messages);
     * 
     * // 생성된 Entry: 
     * // Entry[0]: id="0", body="{\"orderId\": 1, \"status\": \"pending\"}"
     * // Entry[1]: id="1", body="{\"orderId\": 2, \"status\": \"confirmed\"}"
     * </code></pre>
     */
    public static List<SendMessageBatchRequestEntry> createSendMessageEntries(List<String> messages) {
        validateMessageList(messages, "createSendMessageEntries");
        
        return IntStream.range(0, messages.size())
                .mapToObj(index -> SendMessageBatchRequestEntry.builder()
                        .id(String.valueOf(index))
                        .messageBody(messages.get(index))
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * 메시지 삭제 배치를 위한 DeleteMessageBatchRequestEntry 목록을 생성합니다.
     * 
     * <p>Receipt Handle 목록을 AWS SQS DeleteMessageBatch API에서 요구하는 형식으로 변환합니다.
     * 각 Receipt Handle에는 배치 내에서 고유한 ID가 자동으로 할당됩니다.</p>
     * 
     * <h4>Receipt Handle 특성:</h4>
     * <ul>
     *   <li>메시지 수신 시 AWS가 제공하는 임시 식별자</li>
     *   <li>메시지 삭제를 위해 반드시 필요한 값</li>
     *   <li>각 수신마다 다른 값으로 갱신됨</li>
     *   <li>Visibility Timeout 내에서만 유효</li>
     * </ul>
     * 
     * @param receiptHandles 삭제할 메시지들의 Receipt Handle 목록 (null이 아니고 비어있지 않아야 함)
     * @return AWS SQS DeleteMessageBatch API용 Entry 목록
     * @throws IllegalArgumentException Receipt Handle 목록이 null이거나 비어있을 때
     * @throws IllegalArgumentException Receipt Handle 목록에 null 또는 빈 문자열이 포함될 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * List&lt;String&gt; receiptHandles = Arrays.asList(
     *     "AQEBwJnKyrHigUMZj6rYigCgxlaS3SLy0a...",
     *     "AQEBzWwaftReNqyJjUYMrT6gZtBSgzWk0t..."
     * );
     * 
     * List&lt;DeleteMessageBatchRequestEntry&gt; entries = 
     *     BatchEntryFactory.createDeleteMessageEntries(receiptHandles);
     * 
     * // 생성된 Entry:
     * // Entry[0]: id="0", receiptHandle="AQEBwJnKyrHigUMZj6rYigCgxlaS3SLy0a..."
     * // Entry[1]: id="1", receiptHandle="AQEBzWwaftReNqyJjUYMrT6gZtBSgzWk0t..."
     * </code></pre>
     */
    public static List<DeleteMessageBatchRequestEntry> createDeleteMessageEntries(List<String> receiptHandles) {
        validateReceiptHandleList(receiptHandles, "createDeleteMessageEntries");
        
        return IntStream.range(0, receiptHandles.size())
                .mapToObj(index -> DeleteMessageBatchRequestEntry.builder()
                        .id(String.valueOf(index))
                        .receiptHandle(receiptHandles.get(index))
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * 커스텀 ID를 사용하여 메시지 전송 Entry 목록을 생성합니다.
     * 
     * <p>기본 순차 ID 대신 사용자 정의 ID를 사용해야 하는 경우에 활용합니다.
     * 메시지와 ID의 개수가 일치해야 합니다.</p>
     * 
     * <h4>커스텀 ID 사용 시나리오:</h4>
     * <ul>
     *   <li>메시지 추적을 위한 업무별 고유 ID 사용</li>
     *   <li>데이터베이스 PK와의 매핑을 위한 ID 사용</li>
     *   <li>로그 추적을 위한 UUID 기반 ID 사용</li>
     * </ul>
     * 
     * @param messages 전송할 메시지 본문 목록
     * @param customIds 각 메시지에 대응하는 커스텀 ID 목록
     * @return AWS SQS SendMessageBatch API용 Entry 목록
     * @throws IllegalArgumentException 메시지 목록이나 ID 목록이 null이거나 비어있을 때
     * @throws IllegalArgumentException 메시지 개수와 ID 개수가 일치하지 않을 때
     * @throws IllegalArgumentException ID 목록에 중복된 값이 포함될 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * List&lt;String&gt; messages = Arrays.asList("order1", "order2");
     * List&lt;String&gt; customIds = Arrays.asList("uuid-123", "uuid-456");
     * 
     * List&lt;SendMessageBatchRequestEntry&gt; entries = 
     *     BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, customIds);
     * </code></pre>
     */
    public static List<SendMessageBatchRequestEntry> createSendMessageEntriesWithCustomIds(
            List<String> messages, List<String> customIds) {
        validateMessageList(messages, "createSendMessageEntriesWithCustomIds");
        validateCustomIds(customIds, messages.size(), "createSendMessageEntriesWithCustomIds");
        
        return IntStream.range(0, messages.size())
                .mapToObj(index -> SendMessageBatchRequestEntry.builder()
                        .id(customIds.get(index))
                        .messageBody(messages.get(index))
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * 커스텀 ID를 사용하여 메시지 삭제 Entry 목록을 생성합니다.
     * 
     * <p>기본 순차 ID 대신 사용자 정의 ID를 사용해야 하는 경우에 활용합니다.
     * Receipt Handle과 ID의 개수가 일치해야 합니다.</p>
     * 
     * @param receiptHandles 삭제할 메시지들의 Receipt Handle 목록
     * @param customIds 각 Receipt Handle에 대응하는 커스텀 ID 목록
     * @return AWS SQS DeleteMessageBatch API용 Entry 목록
     * @throws IllegalArgumentException Receipt Handle 목록이나 ID 목록이 null이거나 비어있을 때
     * @throws IllegalArgumentException Receipt Handle 개수와 ID 개수가 일치하지 않을 때
     * @throws IllegalArgumentException ID 목록에 중복된 값이 포함될 때
     */
    public static List<DeleteMessageBatchRequestEntry> createDeleteMessageEntriesWithCustomIds(
            List<String> receiptHandles, List<String> customIds) {
        validateReceiptHandleList(receiptHandles, "createDeleteMessageEntriesWithCustomIds");
        validateCustomIds(customIds, receiptHandles.size(), "createDeleteMessageEntriesWithCustomIds");
        
        return IntStream.range(0, receiptHandles.size())
                .mapToObj(index -> DeleteMessageBatchRequestEntry.builder()
                        .id(customIds.get(index))
                        .receiptHandle(receiptHandles.get(index))
                        .build())
                .collect(Collectors.toList());
    }
    
    /**
     * 메시지 목록에 대한 유효성 검증을 수행합니다.
     * 
     * @param messages 검증할 메시지 목록
     * @param methodName 호출한 메서드명 (예외 메시지용)
     * @throws IllegalArgumentException 검증 실패 시
     */
    private static void validateMessageList(List<String> messages, String methodName) {
        if (messages == null) {
            throw new IllegalArgumentException(methodName + ": 메시지 목록은 null일 수 없습니다");
        }
        
        if (messages.isEmpty()) {
            throw new IllegalArgumentException(methodName + ": 메시지 목록은 비어있을 수 없습니다");
        }
        
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i) == null) {
                throw new IllegalArgumentException(
                    String.format("%s: 메시지 목록에 null 요소가 포함될 수 없습니다 (인덱스: %d)", methodName, i)
                );
            }
        }
    }
    
    /**
     * Receipt Handle 목록에 대한 유효성 검증을 수행합니다.
     * 
     * @param receiptHandles 검증할 Receipt Handle 목록
     * @param methodName 호출한 메서드명 (예외 메시지용)
     * @throws IllegalArgumentException 검증 실패 시
     */
    private static void validateReceiptHandleList(List<String> receiptHandles, String methodName) {
        if (receiptHandles == null) {
            throw new IllegalArgumentException(methodName + ": Receipt Handle 목록은 null일 수 없습니다");
        }
        
        if (receiptHandles.isEmpty()) {
            throw new IllegalArgumentException(methodName + ": Receipt Handle 목록은 비어있을 수 없습니다");
        }
        
        for (int i = 0; i < receiptHandles.size(); i++) {
            String handle = receiptHandles.get(i);
            if (handle == null || handle.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("%s: Receipt Handle 목록에 빈 값이나 null이 포함될 수 없습니다 (인덱스: %d)", methodName, i)
                );
            }
        }
    }
    
    /**
     * 커스텀 ID 목록에 대한 유효성 검증을 수행합니다.
     * 
     * @param customIds 검증할 커스텀 ID 목록
     * @param expectedSize 예상되는 ID 개수
     * @param methodName 호출한 메서드명 (예외 메시지용)
     * @throws IllegalArgumentException 검증 실패 시
     */
    private static void validateCustomIds(List<String> customIds, int expectedSize, String methodName) {
        if (customIds == null) {
            throw new IllegalArgumentException(methodName + ": 커스텀 ID 목록은 null일 수 없습니다");
        }
        
        if (customIds.size() != expectedSize) {
            throw new IllegalArgumentException(
                String.format("%s: 커스텀 ID 개수(%d)와 메시지 개수(%d)가 일치하지 않습니다", 
                    methodName, customIds.size(), expectedSize)
            );
        }
        
        // null이나 빈 ID 검증
        for (int i = 0; i < customIds.size(); i++) {
            String id = customIds.get(i);
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException(
                    String.format("%s: 커스텀 ID 목록에 빈 값이나 null이 포함될 수 없습니다 (인덱스: %d)", methodName, i)
                );
            }
        }
        
        // 중복 ID 검증
        long distinctCount = customIds.stream().distinct().count();
        if (distinctCount != customIds.size()) {
            throw new IllegalArgumentException(
                methodName + ": 커스텀 ID 목록에 중복된 값이 포함될 수 없습니다"
            );
        }
    }
}