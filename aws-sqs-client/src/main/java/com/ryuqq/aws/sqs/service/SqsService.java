package com.ryuqq.aws.sqs.service;

import com.ryuqq.aws.sqs.model.SqsMessage;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * SQS 서비스 인터페이스
 */
public interface SqsService {

    /**
     * 메시지 전송
     */
    CompletableFuture<String> sendMessage(String queueName, String messageBody);

    /**
     * 지연 메시지 전송
     */
    CompletableFuture<String> sendDelayedMessage(String queueName, String messageBody, Duration delay);

    /**
     * 속성과 함께 메시지 전송
     */
    CompletableFuture<String> sendMessageWithAttributes(String queueName, String messageBody,
                                                       Map<String, String> messageAttributes);

    /**
     * 배치 메시지 전송
     */
    CompletableFuture<List<String>> sendMessageBatch(String queueName, List<String> messages);

    /**
     * FIFO 큐 메시지 전송
     */
    CompletableFuture<String> sendFifoMessage(String queueName, String messageBody,
                                            String messageGroupId);

    /**
     * 메시지 수신 및 처리
     */
    CompletableFuture<Void> receiveAndProcessMessages(String queueName, 
                                                     Consumer<SqsMessage> processor);

    /**
     * 메시지 수신, 처리 및 자동 삭제
     */
    CompletableFuture<Void> receiveProcessAndDelete(String queueName,
                                                   Consumer<SqsMessage> processor);

    /**
     * Long Polling으로 연속 메시지 수신
     */
    void startContinuousPolling(String queueName, Consumer<SqsMessage> processor);

    /**
     * 폴링 중지
     */
    void stopPolling(String queueName);

    /**
     * DLQ로 메시지 이동
     */
    CompletableFuture<Void> moveToDeadLetterQueue(String sourceQueueName, 
                                                 String dlqName,
                                                 String receiptHandle);

    /**
     * 큐 메시지 수 확인
     */
    CompletableFuture<Long> getQueueMessageCount(String queueName);

    /**
     * 큐 purge (모든 메시지 삭제)
     */
    CompletableFuture<Void> purgeQueue(String queueName);

    /**
     * 큐 존재 여부 확인
     */
    CompletableFuture<Boolean> queueExists(String queueName);

    /**
     * 큐 생성 (존재하지 않을 경우)
     */
    CompletableFuture<String> createQueueIfNotExists(String queueName, Map<String, String> attributes);
}