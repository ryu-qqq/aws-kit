package com.ryuqq.aws.sqs.service;

import com.ryuqq.aws.commons.async.AwsAsyncUtils;
import com.ryuqq.aws.commons.exception.AwsExceptionHandler;
import com.ryuqq.aws.sqs.client.SqsClient;
import com.ryuqq.aws.sqs.model.SqsMessage;
import com.ryuqq.aws.sqs.properties.SqsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * SQS 서비스 구현체
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqsServiceImpl implements SqsService {

    private final SqsClient sqsClient;
    private final SqsProperties sqsProperties;
    
    private final Map<String, CompletableFuture<Void>> pollingTasks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pollingFlags = new ConcurrentHashMap<>();
    private final ExecutorService pollingExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        t.setName("sqs-polling-" + t.getId());
        return t;
    });

    @Override
    public CompletableFuture<String> sendMessage(String queueName, String messageBody) {
        return getQueueUrl(queueName)
                .thenCompose(queueUrl -> sqsClient.sendMessage(queueUrl, messageBody))
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "sendMessage"));
    }

    @Override
    public CompletableFuture<String> sendDelayedMessage(String queueName, String messageBody, Duration delay) {
        if (delay.getSeconds() > 900) { // SQS max delay is 15 minutes
            throw new IllegalArgumentException("Delay cannot exceed 15 minutes");
        }

        Map<String, String> attributes = Map.of(
                "DelaySeconds", String.valueOf(delay.getSeconds())
        );

        return sendMessageWithAttributes(queueName, messageBody, attributes);
    }

    @Override
    public CompletableFuture<String> sendMessageWithAttributes(String queueName, String messageBody,
                                                              Map<String, String> messageAttributes) {
        return getQueueUrl(queueName)
                .thenCompose(queueUrl -> sqsClient.sendMessage(queueUrl, messageBody, messageAttributes))
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "sendMessageWithAttributes"));
    }

    @Override
    public CompletableFuture<List<String>> sendMessageBatch(String queueName, List<String> messages) {
        if (messages.size() > sqsProperties.getBatchConfig().getMaxBatchSize()) {
            // 배치 크기가 초과하면 분할하여 전송
            return sendMessageBatchInChunks(queueName, messages);
        }

        return getQueueUrl(queueName)
                .thenCompose(queueUrl -> sqsClient.sendMessages(queueUrl, messages))
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "sendMessageBatch"));
    }

    private CompletableFuture<List<String>> sendMessageBatchInChunks(String queueName, List<String> messages) {
        int batchSize = sqsProperties.getBatchConfig().getMaxBatchSize();
        List<List<String>> chunks = new ArrayList<>();
        
        for (int i = 0; i < messages.size(); i += batchSize) {
            chunks.add(messages.subList(i, Math.min(i + batchSize, messages.size())));
        }

        List<CompletableFuture<List<String>>> futures = chunks.stream()
                .map(chunk -> sendMessageBatch(queueName, chunk))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .collect(Collectors.toList()));
    }

    @Override
    public CompletableFuture<String> sendFifoMessage(String queueName, String messageBody,
                                                    String messageGroupId) {
        String deduplicationId = UUID.randomUUID().toString();
        
        return getQueueUrl(queueName)
                .thenCompose(queueUrl -> 
                        sqsClient.sendFifoMessage(queueUrl, messageBody, messageGroupId, deduplicationId))
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "sendFifoMessage"));
    }

    @Override
    public CompletableFuture<Void> receiveAndProcessMessages(String queueName, Consumer<SqsMessage> processor) {
        return getQueueUrl(queueName)
                .thenCompose(queueUrl -> 
                        sqsClient.receiveMessages(queueUrl, sqsProperties.getBatchConfig().getMaxReceiveMessages()))
                .thenCompose(messages -> {
                    if (messages.isEmpty()) {
                        return CompletableFuture.completedFuture((Void) null);
                    }

                    // 병렬로 메시지 처리
                    List<CompletableFuture<Void>> processingFutures = messages.stream()
                            .map(message -> CompletableFuture.runAsync(() -> {
                                try {
                                    processor.accept(message);
                                } catch (Exception e) {
                                    log.error("Error processing message: {}", message.getMessageId(), e);
                                    throw e;
                                }
                            }, pollingExecutor))
                            .toList();

                    return CompletableFuture.allOf(processingFutures.toArray(new CompletableFuture<?>[0]));
                })
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "receiveAndProcessMessages"));
    }

    @Override
    public CompletableFuture<Void> receiveProcessAndDelete(String queueName, Consumer<SqsMessage> processor) {
        return getQueueUrl(queueName)
                .thenCompose(queueUrl -> 
                        sqsClient.receiveMessages(queueUrl, sqsProperties.getBatchConfig().getMaxReceiveMessages())
                                .thenCompose(messages -> {
                                    if (messages.isEmpty()) {
                                        return CompletableFuture.completedFuture((Void) null);
                                    }

                                    // 메시지 처리 및 삭제
                                    List<CompletableFuture<Void>> futures = messages.stream()
                                            .map(message -> processAndDeleteMessage(queueUrl, message, processor))
                                            .toList();

                                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
                                }))
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "receiveProcessAndDelete"));
    }

    private CompletableFuture<Void> processAndDeleteMessage(String queueUrl, SqsMessage message, 
                                                           Consumer<SqsMessage> processor) {
        return CompletableFuture.runAsync(() -> processor.accept(message), pollingExecutor)
                .thenCompose(v -> sqsClient.deleteMessage(queueUrl, message.getReceiptHandle()))
                .exceptionally(throwable -> {
                    log.error("Failed to process or delete message: {}", message.getMessageId(), throwable);
                    return (Void) null;
                });
    }

    @Override
    public void startContinuousPolling(String queueName, Consumer<SqsMessage> processor) {
        if (pollingTasks.containsKey(queueName)) {
            log.warn("Polling already started for queue: {}", queueName);
            return;
        }

        pollingFlags.put(queueName, true);
        
        CompletableFuture<Void> pollingTask = CompletableFuture.runAsync(() -> {
            log.info("Starting continuous polling for queue: {}", queueName);
            
            while (pollingFlags.getOrDefault(queueName, false)) {
                try {
                    String queueUrl = getQueueUrl(queueName).get(5, TimeUnit.SECONDS);
                    
                    List<SqsMessage> messages = sqsClient.receiveMessagesWithLongPolling(
                            queueUrl,
                            sqsProperties.getBatchConfig().getMaxReceiveMessages(),
                            sqsProperties.getMessageConfig().getReceiveMessageWaitTime()
                    ).get(30, TimeUnit.SECONDS);

                    if (!messages.isEmpty()) {
                        log.debug("Received {} messages from queue: {}", messages.size(), queueName);
                        
                        for (SqsMessage message : messages) {
                            try {
                                processor.accept(message);
                                sqsClient.deleteMessage(queueUrl, message.getReceiptHandle()).get();
                            } catch (Exception e) {
                                log.error("Error processing message from queue {}: {}", 
                                        queueName, message.getMessageId(), e);
                            }
                        }
                    }
                } catch (TimeoutException e) {
                    log.debug("Polling timeout for queue: {}, continuing...", queueName);
                } catch (Exception e) {
                    log.error("Error during polling for queue: {}", queueName, e);
                    
                    // 에러 발생 시 잠시 대기 후 재시도
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            log.info("Stopped continuous polling for queue: {}", queueName);
        }, pollingExecutor);

        pollingTasks.put(queueName, pollingTask);
    }

    @Override
    public void stopPolling(String queueName) {
        pollingFlags.put(queueName, false);
        
        CompletableFuture<Void> task = pollingTasks.remove(queueName);
        if (task != null) {
            task.cancel(true);
            log.info("Polling stopped for queue: {}", queueName);
        }
    }

    @Override
    public CompletableFuture<Void> moveToDeadLetterQueue(String sourceQueueName, String dlqName,
                                                        String receiptHandle) {
        return CompletableFuture.allOf(
                getQueueUrl(sourceQueueName),
                getQueueUrl(dlqName)
        ).thenCompose(v -> {
            String sourceQueueUrl = getQueueUrl(sourceQueueName).join();
            String dlqUrl = getQueueUrl(dlqName).join();
            
            // 먼저 메시지를 가져오고
            return sqsClient.receiveMessage(sourceQueueUrl)
                    .thenCompose(message -> {
                        if (message == null) {
                            return CompletableFuture.completedFuture((Void) null);
                        }
                        
                        // DLQ로 전송
                        return sqsClient.sendMessage(dlqUrl, message.getBody())
                                .thenCompose(messageId -> 
                                        // 원본 큐에서 삭제
                                        sqsClient.deleteMessage(sourceQueueUrl, receiptHandle));
                    });
        }).exceptionally(AwsExceptionHandler.handleAsync("sqs", "moveToDeadLetterQueue"));
    }

    @Override
    public CompletableFuture<Long> getQueueMessageCount(String queueName) {
        return getQueueUrl(queueName)
                .thenCompose(sqsClient::getApproximateNumberOfMessages)
                .thenApply(Integer::longValue)
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "getQueueMessageCount"));
    }

    @Override
    public CompletableFuture<Void> purgeQueue(String queueName) {
        log.warn("Purging all messages from queue: {}", queueName);
        
        return getQueueUrl(queueName)
                .thenCompose(queueUrl -> {
                    // SQS doesn't have a direct purge method in SDK v2, 
                    // so we need to receive and delete all messages
                    return purgeQueueMessages(queueUrl);
                })
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "purgeQueue"));
    }

    private CompletableFuture<Void> purgeQueueMessages(String queueUrl) {
        return sqsClient.receiveMessages(queueUrl, 10)
                .thenCompose(messages -> {
                    if (messages.isEmpty()) {
                        return CompletableFuture.completedFuture((Void) null);
                    }

                    List<String> receiptHandles = messages.stream()
                            .map(SqsMessage::getReceiptHandle)
                            .collect(Collectors.toList());

                    return sqsClient.deleteMessages(queueUrl, receiptHandles)
                            .thenCompose(v -> purgeQueueMessages(queueUrl)); // Recursive call
                });
    }

    @Override
    public CompletableFuture<Boolean> queueExists(String queueName) {
        return sqsClient.getQueueUrl(queueName)
                .thenApply(url -> true)
                .exceptionally(throwable -> {
                    log.debug("Queue {} does not exist", queueName);
                    return false;
                });
    }

    @Override
    public CompletableFuture<String> createQueueIfNotExists(String queueName, Map<String, String> attributes) {
        return queueExists(queueName)
                .thenCompose(exists -> {
                    if (exists) {
                        return getQueueUrl(queueName);
                    }
                    
                    log.info("Creating queue: {} with attributes: {}", queueName, attributes);
                    return sqsClient.createQueue(queueName, attributes);
                })
                .exceptionally(AwsExceptionHandler.handleAsync("sqs", "createQueueIfNotExists"));
    }

    private CompletableFuture<String> getQueueUrl(String queueName) {
        String prefix = sqsProperties.getDefaultQueueUrlPrefix();
        
        if (prefix != null && !prefix.isEmpty()) {
            // LocalStack 또는 커스텀 엔드포인트 사용
            return CompletableFuture.completedFuture(prefix + queueName);
        }
        
        return sqsClient.getQueueUrl(queueName);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down SQS service...");
        
        // 모든 폴링 중지
        pollingFlags.keySet().forEach(this::stopPolling);
        
        // Executor 종료
        pollingExecutor.shutdown();
        try {
            if (!pollingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                pollingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pollingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}