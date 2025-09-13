package com.ryuqq.aws.sqs.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;

import java.util.*;
import java.lang.reflect.InvocationTargetException;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * BatchEntryFactory 테스트
 * 
 * AWS SQS 배치 Entry 생성 팩토리의 동작을 검증하는 테스트입니다.
 * 다양한 입력 조건에서 올바른 Entry 객체들이 생성되는지 확인합니다.
 */
class BatchEntryFactoryTest {
    
    @Test
    @DisplayName("메시지 목록으로 SendMessageBatchRequestEntry를 생성해야 한다")
    void shouldCreateSendMessageEntries() {
        // given
        List<String> messages = Arrays.asList("message1", "message2", "message3");
        
        // when
        List<SendMessageBatchRequestEntry> entries = BatchEntryFactory.createSendMessageEntries(messages);
        
        // then
        assertThat(entries).hasSize(3);
        
        assertThat(entries.get(0).id()).isEqualTo("0");
        assertThat(entries.get(0).messageBody()).isEqualTo("message1");
        
        assertThat(entries.get(1).id()).isEqualTo("1");
        assertThat(entries.get(1).messageBody()).isEqualTo("message2");
        
        assertThat(entries.get(2).id()).isEqualTo("2");
        assertThat(entries.get(2).messageBody()).isEqualTo("message3");
    }
    
    @Test
    @DisplayName("Receipt Handle 목록으로 DeleteMessageBatchRequestEntry를 생성해야 한다")
    void shouldCreateDeleteMessageEntries() {
        // given
        List<String> receiptHandles = Arrays.asList("handle1", "handle2");
        
        // when
        List<DeleteMessageBatchRequestEntry> entries = BatchEntryFactory.createDeleteMessageEntries(receiptHandles);
        
        // then
        assertThat(entries).hasSize(2);
        
        assertThat(entries.get(0).id()).isEqualTo("0");
        assertThat(entries.get(0).receiptHandle()).isEqualTo("handle1");
        
        assertThat(entries.get(1).id()).isEqualTo("1");
        assertThat(entries.get(1).receiptHandle()).isEqualTo("handle2");
    }
    
    @Test
    @DisplayName("커스텀 ID로 SendMessageBatchRequestEntry를 생성해야 한다")
    void shouldCreateSendMessageEntriesWithCustomIds() {
        // given
        List<String> messages = Arrays.asList("message1", "message2");
        List<String> customIds = Arrays.asList("custom-id-1", "custom-id-2");
        
        // when
        List<SendMessageBatchRequestEntry> entries = 
            BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, customIds);
        
        // then
        assertThat(entries).hasSize(2);
        
        assertThat(entries.get(0).id()).isEqualTo("custom-id-1");
        assertThat(entries.get(0).messageBody()).isEqualTo("message1");
        
        assertThat(entries.get(1).id()).isEqualTo("custom-id-2");
        assertThat(entries.get(1).messageBody()).isEqualTo("message2");
    }
    
    @Test
    @DisplayName("커스텀 ID로 DeleteMessageBatchRequestEntry를 생성해야 한다")
    void shouldCreateDeleteMessageEntriesWithCustomIds() {
        // given
        List<String> receiptHandles = Arrays.asList("handle1", "handle2");
        List<String> customIds = Arrays.asList("custom-id-1", "custom-id-2");
        
        // when
        List<DeleteMessageBatchRequestEntry> entries = 
            BatchEntryFactory.createDeleteMessageEntriesWithCustomIds(receiptHandles, customIds);
        
        // then
        assertThat(entries).hasSize(2);
        
        assertThat(entries.get(0).id()).isEqualTo("custom-id-1");
        assertThat(entries.get(0).receiptHandle()).isEqualTo("handle1");
        
        assertThat(entries.get(1).id()).isEqualTo("custom-id-2");
        assertThat(entries.get(1).receiptHandle()).isEqualTo("handle2");
    }
    
    @Test
    @DisplayName("null 메시지 목록은 예외를 발생시켜야 한다")
    void shouldThrowExceptionForNullMessageList() {
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntries(null)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("메시지 목록은 null일 수 없습니다");
    }
    
    @Test
    @DisplayName("빈 메시지 목록은 예외를 발생시켜야 한다")
    void shouldThrowExceptionForEmptyMessageList() {
        // given
        List<String> emptyMessages = Collections.emptyList();
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntries(emptyMessages)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("메시지 목록은 비어있을 수 없습니다");
    }
    
    @Test
    @DisplayName("null 요소가 포함된 메시지 목록은 예외를 발생시켜야 한다")
    void shouldThrowExceptionForMessageListWithNullElement() {
        // given
        List<String> messagesWithNull = Arrays.asList("message1", null, "message3");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntries(messagesWithNull)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("메시지 목록에 null 요소가 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("null Receipt Handle 목록은 예외를 발생시켜야 한다")
    void shouldThrowExceptionForNullReceiptHandleList() {
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createDeleteMessageEntries(null)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Receipt Handle 목록은 null일 수 없습니다");
    }
    
    @Test
    @DisplayName("빈 Receipt Handle이 포함된 목록은 예외를 발생시켜야 한다")
    void shouldThrowExceptionForBlankReceiptHandle() {
        // given
        List<String> receiptHandlesWithBlank = Arrays.asList("handle1", "", "handle3");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createDeleteMessageEntries(receiptHandlesWithBlank)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Receipt Handle 목록에 빈 값이나 null이 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("메시지 개수와 커스텀 ID 개수가 다르면 예외를 발생시켜야 한다")
    void shouldThrowExceptionWhenMessageCountDiffersFromCustomIdCount() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        List<String> customIds = Arrays.asList("id1"); // 개수가 다름
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, customIds)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("커스텀 ID 개수(1)와 메시지 개수(2)가 일치하지 않습니다");
    }
    
    @Test
    @DisplayName("중복된 커스텀 ID는 예외를 발생시켜야 한다")
    void shouldThrowExceptionForDuplicateCustomIds() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        List<String> duplicateIds = Arrays.asList("same-id", "same-id"); // 중복 ID
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, duplicateIds)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("커스텀 ID 목록에 중복된 값이 포함될 수 없습니다");
    }
    
    @Test
    @DisplayName("null 커스텀 ID는 예외를 발생시켜야 한다")
    void shouldThrowExceptionForNullCustomId() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        List<String> idsWithNull = Arrays.asList("id1", null);
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, idsWithNull)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("커스텀 ID 목록에 빈 값이나 null이 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("단일 메시지로 Entry를 생성해야 한다")
    void shouldCreateEntryForSingleMessage() {
        // given
        List<String> singleMessage = Collections.singletonList("single-message");
        
        // when
        List<SendMessageBatchRequestEntry> entries = BatchEntryFactory.createSendMessageEntries(singleMessage);
        
        // then
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).id()).isEqualTo("0");
        assertThat(entries.get(0).messageBody()).isEqualTo("single-message");
    }
    
    @Test
    @DisplayName("인스턴스 생성이 금지되어야 한다")
    void shouldPreventInstantiation() {
        assertThatThrownBy(() -> {
            var constructor = BatchEntryFactory.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        }).isInstanceOf(InvocationTargetException.class)
          .hasCauseInstanceOf(UnsupportedOperationException.class);

        assertThat(((InvocationTargetException) catchThrowable(() -> {
            var constructor = BatchEntryFactory.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
        })).getCause())
          .hasMessage("팩토리 클래스는 인스턴스를 생성할 수 없습니다");
    }
    
    @Test
    @DisplayName("대용량 메시지 목록을 처리해야 한다")
    void shouldHandleLargeMessageList() {
        // given - AWS SQS 최대 배치 크기인 10개 메시지
        List<String> largeMessageList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            largeMessageList.add("message-" + i);
        }
        
        // when
        List<SendMessageBatchRequestEntry> entries = BatchEntryFactory.createSendMessageEntries(largeMessageList);
        
        // then
        assertThat(entries).hasSize(10);
        for (int i = 0; i < 10; i++) {
            assertThat(entries.get(i).id()).isEqualTo(String.valueOf(i));
            assertThat(entries.get(i).messageBody()).isEqualTo("message-" + i);
        }
    }
    
    @Test
    @DisplayName("긴 메시지 내용을 처리해야 한다")
    void shouldHandleLongMessageContent() {
        // given - 큰 JSON 메시지 시뮬레이션
        String longMessage = "{\"orderId\":12345," +
            "\"customerInfo\":{\"name\":\"John Doe\",\"email\":\"john@example.com\"}," +
            "\"items\":[{\"productId\":1,\"quantity\":2,\"price\":29.99}]," +
            "\"timestamp\":\"2024-01-01T10:00:00Z\"}";
        List<String> messages = Collections.singletonList(longMessage);
        
        // when
        List<SendMessageBatchRequestEntry> entries = BatchEntryFactory.createSendMessageEntries(messages);
        
        // then
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).messageBody()).isEqualTo(longMessage);
    }
    
    @Test
    @DisplayName("특수 문자가 포함된 메시지를 처리해야 한다")
    void shouldHandleSpecialCharacters() {
        // given
        List<String> specialMessages = Arrays.asList(
            "메시지 with 한글",
            "Message with émojis 🚀",
            "XML: <message><body>test</body></message>",
            "JSON: {\"key\": \"value with spaces\"}"
        );
        
        // when
        List<SendMessageBatchRequestEntry> entries = BatchEntryFactory.createSendMessageEntries(specialMessages);
        
        // then
        assertThat(entries).hasSize(4);
        for (int i = 0; i < specialMessages.size(); i++) {
            assertThat(entries.get(i).messageBody()).isEqualTo(specialMessages.get(i));
        }
    }
    
    @Test
    @DisplayName("UUID를 커스텀 ID로 사용할 수 있어야 한다")
    void shouldSupportUUIDAsCustomId() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        List<String> uuidIds = Arrays.asList(
            "550e8400-e29b-41d4-a716-446655440000",
            "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        );
        
        // when
        List<SendMessageBatchRequestEntry> entries = 
            BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, uuidIds);
        
        // then
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).id()).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
        assertThat(entries.get(1).id()).isEqualTo("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    }
    
    @Test
    @DisplayName("Receipt Handle이 공백만 포함된 경우 예외를 발생시켜야 한다")
    void shouldThrowExceptionForWhitespaceOnlyReceiptHandle() {
        // given
        List<String> receiptHandlesWithWhitespace = Arrays.asList("handle1", "   ", "handle3");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createDeleteMessageEntries(receiptHandlesWithWhitespace)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Receipt Handle 목록에 빈 값이나 null이 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("실제 Receipt Handle 형식으로 Entry를 생성해야 한다")
    void shouldCreateEntriesWithRealReceiptHandleFormat() {
        // given - AWS SQS에서 실제로 생성하는 Receipt Handle 형식 시뮬레이션
        List<String> realReceiptHandles = Arrays.asList(
            "AQEBwJnKyrHigUMZj6rYigCgxlaS3SLy0a+FRhOlMRICqBFw4W3z4YqC3z4rq8QqJ...",
            "AQEBzWwaftReNqyJjUYMrT6gZtBSgzWk0tQ3Qf9f9zGg3pX1pBNvjMHWsY7VwV..."
        );
        
        // when
        List<DeleteMessageBatchRequestEntry> entries = 
            BatchEntryFactory.createDeleteMessageEntries(realReceiptHandles);
        
        // then
        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).receiptHandle()).startsWith("AQEBwJnKyrHigUMZj6rYig");
        assertThat(entries.get(1).receiptHandle()).startsWith("AQEBzWwaftReNqyJjUYMrT");
    }
    
    @Test
    @DisplayName("커스텀 ID가 빈 문자열인 경우 예외를 발생시켜야 한다")
    void shouldThrowExceptionForEmptyCustomId() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        List<String> idsWithEmpty = Arrays.asList("id1", "");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, idsWithEmpty)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("커스텀 ID 목록에 빈 값이나 null이 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("커스텀 ID가 공백만 포함된 경우 예외를 발생시켜야 한다")
    void shouldThrowExceptionForWhitespaceOnlyCustomId() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        List<String> idsWithWhitespace = Arrays.asList("id1", "   ");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, idsWithWhitespace)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("커스텀 ID 목록에 빈 값이나 null이 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("null 커스텀 ID 목록은 예외를 발생시켜야 한다")
    void shouldThrowExceptionForNullCustomIdList() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createSendMessageEntriesWithCustomIds(messages, null)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("커스텀 ID 목록은 null일 수 없습니다");
    }
    
    @Test
    @DisplayName("Delete Entry에서도 커스텀 ID 검증이 동일하게 적용되어야 한다")
    void shouldApplySameCustomIdValidationForDeleteEntries() {
        // given
        List<String> receiptHandles = Arrays.asList("handle1", "handle2");
        List<String> duplicateIds = Arrays.asList("same-id", "same-id");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchEntryFactory.createDeleteMessageEntriesWithCustomIds(receiptHandles, duplicateIds)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("커스텀 ID 목록에 중복된 값이 포함될 수 없습니다");
    }
    
    @Test
    @DisplayName("메시지가 빈 문자열이어도 Entry를 생성해야 한다")
    void shouldCreateEntryForEmptyMessage() {
        // given - AWS SQS는 빈 메시지 본문을 허용함
        List<String> messagesWithEmpty = Arrays.asList("message1", "", "message3");
        
        // when
        List<SendMessageBatchRequestEntry> entries = BatchEntryFactory.createSendMessageEntries(messagesWithEmpty);
        
        // then
        assertThat(entries).hasSize(3);
        assertThat(entries.get(1).messageBody()).isEqualTo("");
    }
}