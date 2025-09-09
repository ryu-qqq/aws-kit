package com.ryuqq.aws.sqs.client;

import com.ryuqq.aws.sqs.model.SqsMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * SQS 클라이언트 인터페이스
 */
public interface SqsClient {

    /**
     * 단일 메시지 전송
     */
    CompletableFuture<String> sendMessage(String queueUrl, String messageBody);

    /**
     * 속성이 포함된 단일 메시지 전송
     */
    CompletableFuture<String> sendMessage(String queueUrl, String messageBody, 
                                        Map<String, String> messageAttributes);

    /**
     * 배치 메시지 전송
     */
    CompletableFuture<List<String>> sendMessages(String queueUrl, List<String> messageBodies);

    /**
     * FIFO 큐 메시지 전송
     */
    CompletableFuture<String> sendFifoMessage(String queueUrl, String messageBody, 
                                            String messageGroupId, String messageDeduplicationId);

    /**
     * 단일 메시지 수신
     */
    CompletableFuture<SqsMessage> receiveMessage(String queueUrl);

    /**
     * 배치 메시지 수신
     */
    CompletableFuture<List<SqsMessage>> receiveMessages(String queueUrl, int maxMessages);

    /**
     * Long Polling으로 메시지 수신
     */
    CompletableFuture<List<SqsMessage>> receiveMessagesWithLongPolling(String queueUrl, 
                                                                      int maxMessages, 
                                                                      int waitTimeSeconds);

    /**
     * 메시지 삭제
     */
    CompletableFuture<Void> deleteMessage(String queueUrl, String receiptHandle);

    /**
     * 배치 메시지 삭제
     */
    CompletableFuture<Void> deleteMessages(String queueUrl, List<String> receiptHandles);

    /**
     * 메시지 가시성 타임아웃 연장
     */
    CompletableFuture<Void> changeMessageVisibility(String queueUrl, String receiptHandle, 
                                                   int visibilityTimeoutSeconds);

    /**
     * 큐 URL 가져오기
     */
    CompletableFuture<String> getQueueUrl(String queueName);

    /**
     * 큐 속성 가져오기
     */
    CompletableFuture<Map<String, String>> getQueueAttributes(String queueUrl);

    /**
     * 큐 생성
     */
    CompletableFuture<String> createQueue(String queueName, Map<String, String> attributes);

    /**
     * 큐 삭제
     */
    CompletableFuture<Void> deleteQueue(String queueUrl);

    /**
     * 큐 메시지 수 확인
     */
    CompletableFuture<Integer> getApproximateNumberOfMessages(String queueUrl);
}