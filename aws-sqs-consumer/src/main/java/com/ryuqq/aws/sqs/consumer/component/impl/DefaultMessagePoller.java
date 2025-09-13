package com.ryuqq.aws.sqs.consumer.component.impl;

import com.ryuqq.aws.sqs.consumer.component.MessagePoller;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Default implementation of MessagePoller.
 * Handles SQS message polling with configurable parameters.
 */
@Component
public class DefaultMessagePoller implements MessagePoller {

    private static final Logger log = LoggerFactory.getLogger(DefaultMessagePoller.class);

    private final SqsService sqsService;

    public DefaultMessagePoller(SqsService sqsService) {
        this.sqsService = sqsService;
    }
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private ScheduledExecutorService pollingExecutor;
    private CompletableFuture<Void> pollingTask;
    
    @Override
    public List<SqsMessage> pollMessages(String queueUrl, int maxMessages, int pollTimeoutSeconds) {
        try {
            return sqsService.receiveMessages(queueUrl, maxMessages)
                    .get(pollTimeoutSeconds + 5, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error polling messages from queue {}: {}", queueUrl, e.getMessage(), e);
            return List.of();
        }
    }
    
    @Override
    public void startPolling(String queueUrl, int maxMessages, int pollTimeoutSeconds, 
                           MessageHandler messageHandler) {
        if (polling.getAndSet(true)) {
            log.warn("Polling is already active for queue: {}", queueUrl);
            return;
        }
        
        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "sqs-poller-" + queueUrl.hashCode());
            thread.setDaemon(true);
            return thread;
        });
        
        pollingTask = CompletableFuture.runAsync(() -> 
            pollContinuously(queueUrl, maxMessages, pollTimeoutSeconds, messageHandler), 
            pollingExecutor
        );
        
        log.info("Started polling for queue: {}", queueUrl);
    }
    
    @Override
    public void stopPolling() {
        if (!polling.getAndSet(false)) {
            return;
        }
        
        try {
            if (pollingTask != null && !pollingTask.isDone()) {
                pollingTask.cancel(true);
            }
            
            if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
                pollingExecutor.shutdown();
                if (!pollingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    pollingExecutor.shutdownNow();
                }
            }
            
            log.info("Stopped polling");
        } catch (Exception e) {
            log.error("Error stopping poller: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isPolling() {
        return polling.get();
    }
    
    private void pollContinuously(String queueUrl, int maxMessages, int pollTimeoutSeconds, 
                                 MessageHandler messageHandler) {
        while (polling.get()) {
            try {
                List<SqsMessage> messages = pollMessages(queueUrl, maxMessages, pollTimeoutSeconds);
                
                if (!messages.isEmpty()) {
                    log.debug("Polled {} messages from queue: {}", messages.size(), queueUrl);
                    messageHandler.handleMessages(messages);
                }
                
            } catch (Exception e) {
                log.error("Error in polling loop for queue {}: {}", queueUrl, e.getMessage(), e);
                
                // Brief pause before retrying to avoid tight error loops
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}