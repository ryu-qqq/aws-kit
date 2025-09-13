package com.ryuqq.aws.sqs.util;

import com.ryuqq.aws.sqs.properties.SqsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * BatchValidationUtils 테스트
 * 
 * AWS SQS 배치 검증 유틸리티의 동작을 검증하는 테스트입니다.
 * 다양한 입력 조건에서의 검증 로직이 올바르게 작동하는지 확인합니다.
 */
class BatchValidationUtilsTest {
    
    private SqsProperties sqsProperties;
    
    @BeforeEach
    void setUp() {
        sqsProperties = new SqsProperties();
        sqsProperties.setMaxBatchSize(10); // AWS SQS 기본 최대 배치 크기
    }
    
    @Test
    @DisplayName("정상적인 배치 크기는 검증을 통과해야 한다")
    void shouldPassValidBatchSize() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2", "msg3");
        
        // when & then
        assertThatCode(() -> 
            BatchValidationUtils.validateBatchSize(messages, sqsProperties, "testOperation")
        ).doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("최대 배치 크기를 초과하면 예외가 발생해야 한다")
    void shouldThrowExceptionWhenBatchSizeExceeds() {
        // given
        List<String> messages = Collections.nCopies(11, "message");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchValidationUtils.validateBatchSize(messages, sqsProperties, "testOperation")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("testOperation: 배치 크기는 최대 10개를 초과할 수 없습니다");
    }
    
    @Test
    @DisplayName("null 컬렉션은 크기 검증을 통과해야 한다")
    void shouldPassWhenCollectionIsNull() {
        // when & then
        assertThatCode(() -> 
            BatchValidationUtils.validateBatchSize(null, sqsProperties, "testOperation")
        ).doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("빈 컬렉션은 isEmpty에서 true를 반환해야 한다")
    void shouldReturnTrueForEmptyCollection() {
        // given
        List<String> emptyList = Collections.emptyList();
        
        // when
        boolean result = BatchValidationUtils.isEmpty(emptyList, "testOperation");
        
        // then
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("null 컬렉션은 isEmpty에서 true를 반환해야 한다")
    void shouldReturnTrueForNullCollection() {
        // when
        boolean result = BatchValidationUtils.isEmpty(null, "testOperation");
        
        // then
        assertThat(result).isTrue();
    }
    
    @Test
    @DisplayName("null 요소가 포함된 컬렉션은 검증에 실패해야 한다")
    void shouldFailWhenCollectionContainsNull() {
        // given
        List<String> messagesWithNull = Arrays.asList("msg1", null, "msg3");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchValidationUtils.validateNoNullElements(messagesWithNull, "testOperation")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("testOperation: 컬렉션에 null 요소가 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("빈 문자열이 포함된 컬렉션은 검증에 실패해야 한다")
    void shouldFailWhenCollectionContainsBlankString() {
        // given
        List<String> messagesWithBlank = Arrays.asList("msg1", "", "msg3");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchValidationUtils.validateNoBlankStrings(messagesWithBlank, "testOperation")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("testOperation: 빈 문자열이나 공백 문자열이 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("공백만 포함된 문자열이 있으면 검증에 실패해야 한다")
    void shouldFailWhenCollectionContainsWhitespaceOnlyString() {
        // given
        List<String> messagesWithWhitespace = Arrays.asList("msg1", "  ", "msg3");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchValidationUtils.validateNoBlankStrings(messagesWithWhitespace, "testOperation")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("testOperation: 빈 문자열이나 공백 문자열이 포함될 수 없습니다 (인덱스: 1)");
    }
    
    @Test
    @DisplayName("포괄적 검증이 정상 컬렉션에 대해 통과해야 한다")
    void shouldPassComprehensiveValidationForValidCollection() {
        // given
        List<String> validMessages = Arrays.asList("msg1", "msg2", "msg3");
        
        // when
        boolean shouldEarlyReturn = BatchValidationUtils.validateForBatchOperation(
            validMessages, sqsProperties, "testOperation", true
        );
        
        // then
        assertThat(shouldEarlyReturn).isFalse(); // 정상 처리 계속 신호
    }
    
    @Test
    @DisplayName("빈 컬렉션에 대해 조기 반환 신호를 보내야 한다")
    void shouldReturnEarlyReturnSignalForEmptyCollection() {
        // given
        List<String> emptyMessages = Collections.emptyList();
        
        // when
        boolean shouldEarlyReturn = BatchValidationUtils.validateForBatchOperation(
            emptyMessages, sqsProperties, "testOperation", true
        );
        
        // then
        assertThat(shouldEarlyReturn).isTrue(); // 조기 반환 신호
    }
    
    @Test
    @DisplayName("빈 컬렉션을 허용하지 않으면 예외가 발생해야 한다")
    void shouldThrowExceptionWhenEmptyCollectionNotAllowed() {
        // given
        List<String> emptyMessages = Collections.emptyList();
        
        // when & then
        assertThatThrownBy(() -> 
            BatchValidationUtils.validateForBatchOperation(
                emptyMessages, sqsProperties, "testOperation", false
            )
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("testOperation: 처리할 요소가 없습니다");
    }
    
    @Test
    @DisplayName("작업명이 null이면 예외가 발생해야 한다")
    void shouldThrowExceptionWhenOperationNameIsNull() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchValidationUtils.validateBatchSize(messages, sqsProperties, null)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("작업명은 null이거나 빈 문자열일 수 없습니다");
    }
    
    @Test
    @DisplayName("SqsProperties가 null이면 예외가 발생해야 한다")
    void shouldThrowExceptionWhenSqsPropertiesIsNull() {
        // given
        List<String> messages = Arrays.asList("msg1", "msg2");
        
        // when & then
        assertThatThrownBy(() -> 
            BatchValidationUtils.validateBatchSize(messages, null, "testOperation")
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("SqsProperties는 null일 수 없습니다");
    }
}