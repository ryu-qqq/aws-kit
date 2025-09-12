package com.ryuqq.aws.sqs.service;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlResponse;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import com.ryuqq.aws.sqs.adapter.SqsTypeAdapter;
import com.ryuqq.aws.sqs.properties.SqsProperties;
import com.ryuqq.aws.sqs.types.SqsMessage;
import com.ryuqq.aws.sqs.util.BatchEntryFactory;
import com.ryuqq.aws.sqs.util.BatchValidationUtils;
import com.ryuqq.aws.sqs.util.QueueAttributeUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * AWS SQS(Simple Queue Service) 클라이언트 서비스
 * 
 * <p>AWS SQS와의 메시지 큐 작업을 위한 비동기 서비스입니다.
 * 모든 작업은 CompletableFuture를 반환하여 논블로킹 방식으로 동작합니다.</p>
 * 
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>단일/배치 메시지 전송 및 수신</li>
 *   <li>메시지 삭제 (단일/배치)</li>
 *   <li>큐 생성 및 URL 조회</li>
 *   <li>Long Polling을 통한 효율적인 메시지 수신</li>
 * </ul>
 * 
 * <h3>AWS SQS 제한사항:</h3>
 * <ul>
 *   <li>배치 작업은 최대 10개 메시지까지 처리 가능</li>
 *   <li>메시지 크기는 최대 256KB</li>
 *   <li>메시지 보관 기간은 기본 4일, 최대 14일</li>
 * </ul>
 * 
 * @see SqsProperties SQS 설정 프로퍼티
 * @see SqsMessage 메시지 추상화 타입
 * @since 1.0.0
 */
@Slf4j
@Service
public class SqsService {

    private final SqsAsyncClient sqsAsyncClient;
    private final SqsProperties sqsProperties;

    public SqsService(SqsAsyncClient sqsAsyncClient, SqsProperties sqsProperties) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.sqsProperties = sqsProperties;
    }

    /**
     * SQS 큐에 단일 메시지를 비동기로 전송합니다.
     * 
     * <p>메시지는 즉시 큐로 전송되며, 성공 시 AWS가 생성한 고유 메시지 ID를 반환합니다.</p>
     * 
     * @param queueUrl 메시지를 전송할 SQS 큐의 URL
     * @param body 전송할 메시지 본문 (최대 256KB)
     * @return 전송된 메시지의 고유 ID를 포함한 CompletableFuture
     * @throws RuntimeException 메시지 전송 실패 시
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * sqsService.sendMessage(queueUrl, "{\"userId\": 123, \"action\": \"login\"}")
     *     .thenAccept(messageId -> log.info("메시지 전송 완료: {}", messageId))
     *     .exceptionally(throwable -> {
     *         log.error("메시지 전송 실패", throwable);
     *         return null;
     *     });
     * </code></pre>
     */
    public CompletableFuture<String> sendMessage(String queueUrl, String body) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(body)
                .build();

        return sqsAsyncClient.sendMessage(request)
                .thenApply(SendMessageResponse::messageId)
                .exceptionally(throwable -> {
                    throw new RuntimeException("Failed to send message", throwable);
                });
    }

    /**
     * SQS 큐에 여러 메시지를 한 번에 배치로 전송합니다.
     * 
     * <p>배치 전송은 개별 전송보다 효율적이며, AWS SQS 비용을 절약할 수 있습니다.
     * 일부 메시지가 실패하더라도 성공한 메시지들의 ID 목록을 반환합니다.</p>
     * 
     * <h4>AWS SQS 배치 제한사항:</h4>
     * <ul>
     *   <li>한 번에 최대 10개 메시지까지 전송 가능</li>
     *   <li>전체 배치 크기는 최대 256KB</li>
     *   <li>각 메시지는 고유한 ID가 자동으로 할당됨</li>
     * </ul>
     * 
     * @param queueUrl 메시지를 전송할 SQS 큐의 URL
     * @param messages 전송할 메시지 본문 목록 (최대 10개)
     * @return 성공적으로 전송된 메시지들의 ID 목록을 포함한 CompletableFuture
     * @throws IllegalArgumentException 메시지 개수가 최대 배치 크기를 초과할 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * List&lt;String&gt; messages = List.of(
     *     "{\"type\": \"order\", \"id\": 1}",
     *     "{\"type\": \"order\", \"id\": 2}"
     * );
     * 
     * sqsService.sendMessageBatch(queueUrl, messages)
     *     .thenAccept(messageIds -> 
     *         log.info("{}개 메시지 전송 완료", messageIds.size()));
     * </code></pre>
     */
    public CompletableFuture<List<String>> sendMessageBatch(String queueUrl, List<String> messages) {
        // 빈 컬렉션인 경우 조기 반환
        if (BatchValidationUtils.validateForBatchOperation(messages, sqsProperties, "sendMessageBatch", true)) {
            log.debug("전송할 메시지가 없어 빈 결과를 반환합니다");
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // 배치 Entry 생성 (팩토리 패턴 사용)
        List<SendMessageBatchRequestEntry> entries = BatchEntryFactory.createSendMessageEntries(messages);

        SendMessageBatchRequest request = SendMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(entries)
                .build();

        return sqsAsyncClient.sendMessageBatch(request)
                .thenApply(response -> {
                    if (!response.failed().isEmpty()) {
                        log.warn("Some messages failed to send: {}", response.failed());
                    }
                    return response.successful().stream()
                            .map(SendMessageBatchResultEntry::messageId)
                            .collect(Collectors.toList());
                });
    }

    /**
     * SQS 큐에서 메시지를 비동기로 수신합니다.
     * 
     * <p>Long Polling을 사용하여 효율적으로 메시지를 수신합니다.
     * 메시지를 수신하면 Visibility Timeout이 적용되어 다른 컨슈머가 
     * 같은 메시지를 처리하지 않도록 보장합니다.</p>
     * 
     * <h4>Long Polling vs Short Polling:</h4>
     * <ul>
     *   <li>Long Polling: 메시지가 올 때까지 대기 (비용 효율적)</li>
     *   <li>Short Polling: 즉시 응답 (빠른 응답, 높은 비용)</li>
     * </ul>
     * 
     * <h4>Visibility Timeout:</h4>
     * <ul>
     *   <li>메시지를 수신한 후 다른 컨슈머가 볼 수 없는 시간</li>
     *   <li>이 시간 동안 메시지를 처리하고 삭제해야 함</li>
     *   <li>처리 실패 시 자동으로 큐에 다시 나타남</li>
     * </ul>
     * 
     * @param queueUrl 메시지를 수신할 SQS 큐의 URL
     * @param maxMessages 한 번에 수신할 최대 메시지 개수 (1-10)
     * @return 수신된 SqsMessage 객체들의 목록을 포함한 CompletableFuture
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * sqsService.receiveMessages(queueUrl, 5)
     *     .thenAccept(messages -> {
     *         for (SqsMessage message : messages) {
     *             // 메시지 처리
     *             processMessage(message.getBody());
     *             
     *             // 처리 완료 후 삭제
     *             sqsService.deleteMessage(queueUrl, message.getReceiptHandle());
     *         }
     *     });
     * </code></pre>
     */
    public CompletableFuture<List<SqsMessage>> receiveMessages(String queueUrl, int maxMessages) {
        ReceiveMessageRequest request = ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(Math.min(maxMessages, sqsProperties.getMaxBatchSize()))
                .waitTimeSeconds(sqsProperties.getLongPollingWaitSeconds())
                .visibilityTimeout(sqsProperties.getVisibilityTimeout())
                .messageAttributeNames("All")
                .build();

        return sqsAsyncClient.receiveMessage(request)
                .thenApply(ReceiveMessageResponse::messages)
                .thenApply(SqsTypeAdapter::fromAwsMessages);
    }

    /**
     * 처리 완료된 메시지를 SQS 큐에서 삭제합니다.
     * 
     * <p>메시지를 수신하여 처리한 후에는 반드시 삭제해야 합니다.
     * 삭제하지 않으면 Visibility Timeout 후에 메시지가 다시 나타나서
     * 중복 처리될 수 있습니다.</p>
     * 
     * <h4>Receipt Handle:</h4>
     * <ul>
     *   <li>메시지를 수신할 때 AWS가 제공하는 임시 식별자</li>
     *   <li>메시지 삭제 시 반드시 필요</li>
     *   <li>각 수신마다 다른 값이 생성됨</li>
     * </ul>
     * 
     * @param queueUrl 메시지를 삭제할 SQS 큐의 URL
     * @param receiptHandle 수신 시 받은 메시지의 Receipt Handle
     * @return 삭제 완료를 나타내는 CompletableFuture&lt;Void&gt;
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * // 메시지 수신 후 처리 및 삭제
     * sqsService.receiveMessages(queueUrl, 1)
     *     .thenCompose(messages -> {
     *         if (!messages.isEmpty()) {
     *             SqsMessage message = messages.get(0);
     *             processMessage(message.getBody());
     *             return sqsService.deleteMessage(queueUrl, message.getReceiptHandle());
     *         }
     *         return CompletableFuture.completedFuture(null);
     *     });
     * </code></pre>
     */
    public CompletableFuture<Void> deleteMessage(String queueUrl, String receiptHandle) {
        DeleteMessageRequest request = DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(receiptHandle)
                .build();

        return sqsAsyncClient.deleteMessage(request)
                .thenRun(() -> log.debug("Message deleted from queue: {}", queueUrl));
    }

    /**
     * 여러 개의 처리 완료된 메시지를 한 번에 배치로 삭제합니다.
     * 
     * <p>배치 삭제는 개별 삭제보다 효율적이며, AWS SQS API 호출 횟수를
     * 줄여 성능과 비용을 개선합니다.</p>
     * 
     * <h4>배치 삭제 제한사항:</h4>
     * <ul>
     *   <li>한 번에 최대 10개 메시지까지 삭제 가능</li>
     *   <li>각 Receipt Handle은 유효해야 함</li>
     *   <li>일부 실패 시에도 성공한 삭제는 유지됨</li>
     * </ul>
     * 
     * @param queueUrl 메시지를 삭제할 SQS 큐의 URL
     * @param receiptHandles 삭제할 메시지들의 Receipt Handle 목록 (최대 10개)
     * @return 삭제 완료를 나타내는 CompletableFuture&lt;Void&gt;
     * @throws IllegalArgumentException Receipt Handle 개수가 최대 배치 크기를 초과할 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * sqsService.receiveMessages(queueUrl, 10)
     *     .thenCompose(messages -> {
     *         // 모든 메시지 처리
     *         for (SqsMessage message : messages) {
     *             processMessage(message.getBody());
     *         }
     *         
     *         // Receipt Handle 수집 후 배치 삭제
     *         List&lt;String&gt; receiptHandles = messages.stream()
     *             .map(SqsMessage::getReceiptHandle)
     *             .collect(Collectors.toList());
     *             
     *         return sqsService.deleteMessageBatch(queueUrl, receiptHandles);
     *     });
     * </code></pre>
     */
    public CompletableFuture<Void> deleteMessageBatch(String queueUrl, List<String> receiptHandles) {
        // 빈 컬렉션인 경우 조기 반환
        if (BatchValidationUtils.validateForBatchOperation(receiptHandles, sqsProperties, "deleteMessageBatch", true)) {
            log.debug("삭제할 메시지가 없어 작업을 완료합니다");
            return CompletableFuture.completedFuture(null);
        }

        // 배치 Entry 생성 (팩토리 패턴 사용)
        List<DeleteMessageBatchRequestEntry> entries = BatchEntryFactory.createDeleteMessageEntries(receiptHandles);

        DeleteMessageBatchRequest request = DeleteMessageBatchRequest.builder()
                .queueUrl(queueUrl)
                .entries(entries)
                .build();

        return sqsAsyncClient.deleteMessageBatch(request)
                .thenRun(() -> log.debug("Batch deleted messages from queue: {}", queueUrl));
    }

    /**
     * 새로운 SQS 큐를 생성합니다.
     * 
     * <p>큐 생성은 비동기로 수행되며, 성공 시 생성된 큐의 URL을 반환합니다.
     * 동일한 이름의 큐가 이미 존재하는 경우, 기존 큐의 URL이 반환됩니다.</p>
     * 
     * <h4>주요 큐 속성 (QueueAttributeName):</h4>
     * <ul>
     *   <li>VisibilityTimeout: 메시지 가시성 타임아웃 (기본 30초)</li>
     *   <li>MessageRetentionPeriod: 메시지 보관 기간 (기본 4일, 최대 14일)</li>
     *   <li>MaxReceiveCount: 최대 수신 횟수 (DLQ 설정 시)</li>
     *   <li>ReceiveMessageWaitTimeSeconds: Long Polling 대기 시간</li>
     * </ul>
     * 
     * @param queueName 생성할 큐의 이름 (영숫자, 하이픈, 언더스코어만 허용)
     * @param attributes 큐 설정 속성 맵 (null 또는 empty 가능)
     * @return 생성된 큐의 URL을 포함한 CompletableFuture
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * Map&lt;String, String&gt; attributes = Map.of(
     *     "VisibilityTimeout", "60",
     *     "MessageRetentionPeriod", "1209600", // 14일
     *     "ReceiveMessageWaitTimeSeconds", "20" // Long Polling
     * );
     * 
     * sqsService.createQueue("my-order-queue", attributes)
     *     .thenAccept(queueUrl -> 
     *         log.info("큐 생성 완료: {}", queueUrl));
     * </code></pre>
     */
    public CompletableFuture<String> createQueue(String queueName, Map<String, String> attributes) {
        CreateQueueRequest.Builder requestBuilder = CreateQueueRequest.builder()
                .queueName(queueName);
        
        // 큐 속성 변환 (유틸리티 클래스 사용)
        Map<QueueAttributeName, String> queueAttributes = QueueAttributeUtils.convertToQueueAttributes(attributes);
        if (!queueAttributes.isEmpty()) {
            requestBuilder.attributes(queueAttributes);
        }

        return sqsAsyncClient.createQueue(requestBuilder.build())
                .thenApply(CreateQueueResponse::queueUrl);
    }

    /**
     * 큐 이름을 통해 SQS 큐의 URL을 조회합니다.
     * 
     * <p>큐 URL은 SQS API 호출에 필요한 고유 식별자입니다.
     * 큐가 존재하지 않는 경우 예외가 발생합니다.</p>
     * 
     * <h4>큐 URL 형식:</h4>
     * <ul>
     *   <li>표준 큐: https://sqs.{region}.amazonaws.com/{account-id}/{queue-name}</li>
     *   <li>FIFO 큐: https://sqs.{region}.amazonaws.com/{account-id}/{queue-name}.fifo</li>
     * </ul>
     * 
     * @param queueName 조회할 큐의 이름
     * @return 큐의 URL을 포함한 CompletableFuture
     * @throws software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException 큐가 존재하지 않을 때
     * 
     * <h4>사용 예시:</h4>
     * <pre><code>
     * sqsService.getQueueUrl("my-order-queue")
     *     .thenAccept(queueUrl -> {
     *         log.info("큐 URL: {}", queueUrl);
     *         // 조회된 URL로 메시지 작업 수행
     *         return sqsService.sendMessage(queueUrl, "test message");
     *     })
     *     .exceptionally(throwable -> {
     *         if (throwable.getCause() instanceof QueueDoesNotExistException) {
     *             log.error("큐가 존재하지 않습니다: {}", queueName);
     *         }
     *         return null;
     *     });
     * </code></pre>
     */
    public CompletableFuture<String> getQueueUrl(String queueName) {
        GetQueueUrlRequest request = GetQueueUrlRequest.builder()
                .queueName(queueName)
                .build();

        return sqsAsyncClient.getQueueUrl(request)
                .thenApply(GetQueueUrlResponse::queueUrl);
    }
}