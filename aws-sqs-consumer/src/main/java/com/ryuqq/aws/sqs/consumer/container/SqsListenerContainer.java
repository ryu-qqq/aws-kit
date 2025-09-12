package com.ryuqq.aws.sqs.consumer.container;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ryuqq.aws.sqs.consumer.types.DlqMessage;
import com.ryuqq.aws.sqs.service.SqsService;
import com.ryuqq.aws.sqs.types.SqsMessage;
import com.ryuqq.aws.sqs.consumer.annotation.SqsListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SQS 메시지 소비를 위한 컨테이너 클래스
 * 
 * 단일 리스너 메서드에 대한 SQS 메시지 소비를 관리하는 핵심 컨테이너입니다.
 * AWS SQS Long Polling 방식을 사용하여 메시지를 지속적으로 수신하고,
 * Virtual Thread 또는 Platform Thread를 사용하여 메시지를 비동기적으로 처리합니다.
 * 
 * <h3>핵심 기능</h3>
 * <ul>
 *   <li>Long Polling을 통한 효율적인 메시지 수신</li>
 *   <li>Thread Pool을 사용한 비동기 메시지 처리</li>
 *   <li>Retry 및 Dead Letter Queue(DLQ) 지원</li>
 *   <li>Thread-safe한 컨테이너 생명주기 관리</li>
 *   <li>메시지 처리 통계 및 모니터링 지원</li>
 * </ul>
 * 
 * <h3>Thread 모델</h3>
 * <ul>
 *   <li><strong>Polling Thread</strong>: SQS로부터 메시지를 수신하는 전용 스레드</li>
 *   <li><strong>Message Processing Threads</strong>: 수신된 메시지를 실제로 처리하는 워커 스레드들</li>
 *   <li><strong>Virtual Threads</strong>: Java 21+에서 I/O 집약적 작업에 최적화된 경량 스레드</li>
 *   <li><strong>Platform Threads</strong>: 전통적인 OS 스레드를 사용한 스레드 풀</li>
 * </ul>
 * 
 * <h3>보안 개선사항</h3>
 * <ul>
 *   <li>Jackson ObjectMapper를 사용한 안전한 JSON 직렬화 (인젝션 방지)</li>
 *   <li>Atomic 연산을 통한 Thread-safe한 상태 관리</li>
 *   <li>적절한 예외 처리 및 리소스 정리</li>
 *   <li>UncaughtExceptionHandler를 통한 스레드 예외 처리</li>
 * </ul>
 * 
 * <h3>사용법</h3>
 * <pre>
 * {@literal @}SqsListener(queueName = "my-queue", maxMessagesPerPoll = 10)
 * public void handleMessage(SqsMessage message) {
 *     // 메시지 처리 로직
 * }
 * </pre>
 * 
 * @see SqsListener
 * @see ContainerState
 * @see SqsService
 * @since 1.0.0
 */
@Slf4j
public class SqsListenerContainer {
    
    private static final ObjectMapper OBJECT_MAPPER = createSecureObjectMapper();
    
    private final String containerId;
    private final Object targetBean;
    private final Method targetMethod;
    private final SqsListener listenerAnnotation;
    private final SqsService sqsService;
    private final Environment environment;
    private final ApplicationContext applicationContext;
    private final ExecutorService messageExecutorService;
    private final ExecutorService pollingExecutorService;
    
    // Atomic 연산을 통한 Thread-safe 상태 관리 - 단일 원자적 참조를 사용하여 동시성 보장
    private final AtomicReference<ContainerState> state = new AtomicReference<>(ContainerState.CREATED);
    private final AtomicLong processedMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    
    private volatile String resolvedQueueUrl;
    private volatile ScheduledExecutorService pollingExecutor;
    private volatile CompletableFuture<Void> pollingTask;
    
    // 상태 전환을 위한 동기화 객체 - 동시에 여러 스레드가 상태를 변경하는 것을 방지
    private final Object stateLock = new Object();
    
    public SqsListenerContainer(String containerId,
                              Object targetBean,
                              Method targetMethod,
                              SqsListener listenerAnnotation,
                              SqsService sqsService,
                              Environment environment,
                              ApplicationContext applicationContext,
                              ExecutorService messageExecutorService,
                              ExecutorService pollingExecutorService) {
        this.containerId = containerId;
        this.targetBean = targetBean;
        this.targetMethod = targetMethod;
        this.listenerAnnotation = listenerAnnotation;
        this.sqsService = sqsService;
        this.environment = environment;
        this.applicationContext = applicationContext;
        this.messageExecutorService = messageExecutorService;
        this.pollingExecutorService = pollingExecutorService;
        
        this.targetMethod.setAccessible(true);
    }
    
    /**
     * JSON 직렬화를 위한 보안 ObjectMapper 인스턴스 생성
     * 
     * Jackson ObjectMapper를 사용하여 DLQ 메시지를 안전하게 JSON으로 직렬화합니다.
     * AUTO_CLOSE_SOURCE 기능을 비활성화하여 보안 취약점을 방지합니다.
     * 
     * @return 보안이 강화된 ObjectMapper 인스턴스
     */
    private static ObjectMapper createSecureObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // 보안 취약점으로 악용될 수 있는 기능들을 비활성화
        mapper.getFactory().disable(com.fasterxml.jackson.core.JsonParser.Feature.AUTO_CLOSE_SOURCE);
        return mapper;
    }
    
    /**
     * 컨테이너 시작 및 상태 검증
     * 
     * 컨테이너를 시작하고 SQS 큐 URL을 해결한 후 메시지 폴링을 초기화합니다.
     * 상태 전환은 Thread-safe하게 수행되며, 실패 시 FAILED 상태로 전환됩니다.
     * 
     * <h4>시작 과정</h4>
     * <ol>
     *   <li>현재 상태가 시작 가능한지 검증 (CREATED 또는 STOPPED 상태)</li>
     *   <li>STARTING 상태로 안전하게 전환</li>
     *   <li>SQS 큐 URL 해결 (queueName 또는 queueUrl 사용)</li>
     *   <li>폴링 스레드 초기화 및 시작</li>
     *   <li>RUNNING 상태로 전환 완료</li>
     * </ol>
     * 
     * @throws RuntimeException 컨테이너 시작에 실패한 경우
     */
    public void start() {
        synchronized (stateLock) {
            ContainerState currentState = state.get();
            
            if (!currentState.canStart()) {
                log.warn("Container {} cannot be started from state: {}", containerId, currentState);
                return;
            }
            
            if (!transitionState(currentState, ContainerState.STARTING)) {
                log.warn("Failed to transition container {} to STARTING state", containerId);
                return;
            }
        }
        
        try {
            resolveQueueUrl();
            initializePolling();
            
            if (!transitionState(ContainerState.STARTING, ContainerState.RUNNING)) {
                throw new IllegalStateException("Failed to transition to RUNNING state");
            }
            
            log.info("Started SQS listener container: {} for queue: {}", containerId, resolvedQueueUrl);
        } catch (Exception e) {
            transitionState(state.get(), ContainerState.FAILED);
            log.error("Failed to start container {}: {}", containerId, e.getMessage(), e);
            throw new RuntimeException("Failed to start SQS listener container", e);
        }
    }
    
    /**
     * 컨테이너 정지 및 리소스 정리
     * 
     * 실행 중인 컨테이너를 안전하게 정지하고 모든 리소스를 정리합니다.
     * ExecutorService의 graceful shutdown을 수행하여 진행 중인 작업이 완료되도록 합니다.
     * 
     * <h4>정지 과정</h4>
     * <ol>
     *   <li>현재 상태가 정지 가능한지 검증</li>
     *   <li>STOPPING 상태로 전환</li>
     *   <li>폴링 작업 취소 (CompletableFuture.cancel)</li>
     *   <li>ExecutorService graceful shutdown (30초 대기)</li>
     *   <li>필요시 강제 종료 (10초 추가 대기)</li>
     *   <li>STOPPED 상태로 전환 완료</li>
     * </ol>
     * 
     * Thread 안전성: synchronized 블록을 사용하여 동시 정지 요청을 방지합니다.
     */
    public void stop() {
        synchronized (stateLock) {
            ContainerState currentState = state.get();
            
            if (!currentState.canStop()) {
                log.debug("Container {} is already in non-stoppable state: {}", containerId, currentState);
                return;
            }
            
            if (!transitionState(currentState, ContainerState.STOPPING)) {
                log.warn("Failed to transition container {} to STOPPING state", containerId);
                return;
            }
        }
        
        log.info("Stopping SQS listener container: {}", containerId);
        
        try {
            // 폴링 작업 취소 - CompletableFuture의 interrupt 플래그를 설정하여 안전하게 취소
            if (pollingTask != null && !pollingTask.isDone()) {
                pollingTask.cancel(true);
            }
            
            // 폴링 ExecutorService를 적절한 타임아웃과 함께 종료
            if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
                pollingExecutor.shutdown();
                if (!pollingExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    pollingExecutor.shutdownNow();
                    // 강제 종료 후 추가 대기 시간
                    if (!pollingExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                        log.warn("Polling executor for container {} did not terminate gracefully", containerId);
                    }
                }
            }
            
            transitionState(ContainerState.STOPPING, ContainerState.STOPPED);
            log.info("Stopped SQS listener container: {}", containerId);
            
        } catch (Exception e) {
            transitionState(state.get(), ContainerState.FAILED);
            log.error("Error stopping container {}: {}", containerId, e.getMessage(), e);
        }
    }
    
    /**
     * Thread-safe 상태 전환 및 유효성 검증
     * 
     * Compare-And-Set (CAS) 연산을 사용하여 원자적으로 상태를 전환합니다.
     * 상태 전환 규칙에 따라 유효하지 않은 전환은 거부됩니다.
     * 
     * <h4>지원되는 상태 전환</h4>
     * <ul>
     *   <li>CREATED → STARTING → RUNNING</li>
     *   <li>RUNNING → STOPPING → STOPPED</li>
     *   <li>모든 상태 → FAILED (예외 발생시)</li>
     * </ul>
     * 
     * @param from 현재 상태
     * @param to 목표 상태
     * @return 상태 전환 성공 여부
     */
    private boolean transitionState(ContainerState from, ContainerState to) {
        if (!from.canTransitionTo(to)) {
            log.error("Invalid state transition for container {}: {} -> {}", containerId, from, to);
            return false;
        }
        
        boolean success = state.compareAndSet(from, to);
        if (success) {
            log.debug("Container {} transitioned from {} to {}", containerId, from, to);
        }
        return success;
    }
    
    /**
     * 컨테이너 실행 상태 확인
     * 
     * @return 컨테이너가 RUNNING 상태인 경우 true
     */
    public boolean isRunning() {
        return state.get() == ContainerState.RUNNING;
    }
    
    /**
     * 현재 컨테이너 상태 조회
     * 
     * @return 현재 컨테이너의 상태 (CREATED, STARTING, RUNNING, STOPPING, STOPPED, FAILED)
     */
    public ContainerState getState() {
        return state.get();
    }
    
    /**
     * 컨테이너 통계 정보 조회
     * 
     * 컨테이너의 현재 상태와 메시지 처리 통계를 포함한 모니터링 정보를 제공합니다.
     * Atomic 변수를 사용하여 Thread-safe하게 통계를 수집합니다.
     * 
     * @return 컨테이너 ID, 상태, 처리된/실패한 메시지 수를 포함한 통계 객체
     */
    public ContainerStats getStats() {
        ContainerState currentState = state.get();
        return new ContainerStats(
            containerId,
            currentState,
            currentState == ContainerState.RUNNING,
            processedMessages.get(),
            failedMessages.get()
        );
    }
    
    /**
     * SQS 큐 URL 해결
     * 
     * @SqsListener 어노테이션에서 지정한 queueName 또는 queueUrl을 사용하여 실제 SQS 큐 URL을 해결합니다.
     * queueUrl이 직접 지정된 경우 해당 값을 사용하고, queueName이 지정된 경우 SqsService를 통해 URL을 조회합니다.
     * 
     * @throws Exception 큐 URL 해결 실패시 예외 발생
     */
    private void resolveQueueUrl() throws Exception {
        String queueName = resolveProperty(listenerAnnotation.queueName());
        String queueUrl = resolveProperty(listenerAnnotation.queueUrl());
        
        if (queueName.isEmpty() && queueUrl.isEmpty()) {
            throw new IllegalArgumentException("Either queueName or queueUrl must be specified for container: " + containerId);
        }
        
        if (!queueUrl.isEmpty()) {
            resolvedQueueUrl = queueUrl;
        } else {
            // Resolve queue URL from queue name
            resolvedQueueUrl = sqsService.getQueueUrl(queueName).get();
        }
        
        log.info("Container {} will listen to queue: {}", containerId, resolvedQueueUrl);
    }
    
    /**
     * 메시지 폴링 초기화
     * 
     * SQS Long Polling을 수행할 스레드를 초기화합니다.
     * 제공된 ExecutorService가 ScheduledExecutorService인 경우 그대로 사용하고,
     * 그렇지 않은 경우 새로운 ScheduledExecutorService로 래핑합니다.
     * 
     * UncaughtExceptionHandler를 설정하여 폴링 스레드에서 발생하는 예외를 처리합니다.
     */
    private void initializePolling() {
        // Use the provided polling executor service instead of creating a new one
        if (pollingExecutorService instanceof ScheduledExecutorService) {
            pollingExecutor = (ScheduledExecutorService) pollingExecutorService;
        } else {
            // Wrap non-scheduled executor in a scheduled executor for compatibility
            pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "sqs-poller-" + containerId);
                thread.setDaemon(true);
                // 폴링 스레드에서 처리되지 않은 예외 발생시 컨테이너 상태를 FAILED로 전환
                thread.setUncaughtExceptionHandler((t, e) -> {
                    log.error("Uncaught exception in polling thread for container {}: {}", containerId, e.getMessage(), e);
                    transitionState(state.get(), ContainerState.FAILED);
                });
                return thread;
            });
        }
        
        pollingTask = CompletableFuture.runAsync(this::pollMessages, pollingExecutor);
    }
    
    /**
     * SQS 메시지 지속적 폴링
     * 
     * 컨테이너가 RUNNING 상태인 동안 지속적으로 SQS에서 메시지를 수신합니다.
     * AWS SQS Long Polling 방식을 사용하여 효율적으로 메시지를 가져옵니다.
     * 
     * <h4>폴링 프로세스</h4>
     * <ol>
     *   <li>SqsService.receiveMessages()로 배치 단위 메시지 수신</li>
     *   <li>batchMode 설정에 따라 개별 또는 배치 처리 선택</li>
     *   <li>예외 발생시 재시도 지연 후 폴링 재개</li>
     *   <li>InterruptedException 발생시 정상적으로 폴링 중단</li>
     * </ol>
     * 
     * Thread 안전성: 이 메서드는 단일 폴링 스레드에서만 실행됩니다.
     */
    private void pollMessages() {
        while (state.get() == ContainerState.RUNNING) {
            try {
                List<SqsMessage> messages = sqsService.receiveMessages(
                    resolvedQueueUrl,
                    listenerAnnotation.maxMessagesPerPoll()
                ).get(listenerAnnotation.pollTimeoutSeconds() + 5, TimeUnit.SECONDS);
                
                if (!messages.isEmpty()) {
                    log.debug("Container {} received {} messages", containerId, messages.size());
                    
                    if (listenerAnnotation.batchMode()) {
                        processBatch(messages);
                    } else {
                        messages.forEach(this::processMessage);
                    }
                }
                
            } catch (InterruptedException e) {
                log.debug("Container {} polling interrupted", containerId);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (state.get() == ContainerState.RUNNING) {
                    log.error("Error polling messages for container {}: {}", containerId, e.getMessage(), e);
                    
                    // 시스템 과부하 방지를 위한 재시도 전 짧은 지연
                    try {
                        Thread.sleep(Math.min(1000, listenerAnnotation.retryDelayMillis()));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }
    
    /**
     * 단일 메시지 비동기 처리
     * 
     * 수신된 SQS 메시지를 별도의 워커 스레드에서 비동기적으로 처리합니다.
     * 메시지 처리 후 설정에 따라 자동으로 SQS에서 메시지를 삭제할 수 있습니다.
     * 
     * <h4>처리 흐름</h4>
     * <ol>
     *   <li>메시지 처리용 ExecutorService에 비동기 작업 제출</li>
     *   <li>processMessageWithRetry()를 통한 재시도 로직 실행</li>
     *   <li>성공시 processedMessages 카운터 증가 및 메시지 삭제</li>
     *   <li>실패시 failedMessages 카운터 증가 및 DLQ 처리</li>
     * </ol>
     * 
     * @param message 처리할 SQS 메시지
     */
    private void processMessage(SqsMessage message) {
        CompletableFuture.runAsync(() -> {
            try {
                processMessageWithRetry(message);
                processedMessages.incrementAndGet();
                
                // 설정에 따른 메시지 자동 삭제 (autoDelete=true인 경우)
                if (listenerAnnotation.autoDelete()) {
                    sqsService.deleteMessage(resolvedQueueUrl, message.getReceiptHandle())
                        .exceptionally(throwable -> {
                            log.warn("Failed to delete message {} for container {}: {}", 
                                message.getMessageId(), containerId, throwable.getMessage());
                            return null;
                        });
                }
                
            } catch (Exception e) {
                failedMessages.incrementAndGet();
                log.error("Failed to process message {} for container {}: {}", 
                    message.getMessageId(), containerId, e.getMessage(), e);
                
                handleFailedMessage(message, e);
            }
        }, messageExecutorService);
    }
    
    /**
     * 배치 메시지 비동기 처리
     * 
     * 여러 개의 SQS 메시지를 하나의 배치로 처리합니다.
     * @SqsListener의 batchMode=true로 설정된 경우에 사용됩니다.
     * 
     * <h4>배치 처리 특징</h4>
     * <ul>
     *   <li>모든 메시지를 단일 메서드 호출로 처리</li>
     *   <li>배치 전체가 성공하거나 실패하는 원자적 처리</li>
     *   <li>성공시 모든 메시지의 receiptHandle을 배치 삭제</li>
     *   <li>실패시 배치 내 모든 메시지를 개별적으로 DLQ 처리</li>
     * </ul>
     * 
     * @param messages 처리할 SQS 메시지 목록
     */
    private void processBatch(List<SqsMessage> messages) {
        CompletableFuture.runAsync(() -> {
            try {
                invokeTargetMethod(messages);
                processedMessages.addAndGet(messages.size());
                
                // 설정에 따른 메시지 배치 자동 삭제 (autoDelete=true인 경우)
                if (listenerAnnotation.autoDelete()) {
                    List<String> receiptHandles = messages.stream()
                        .map(SqsMessage::getReceiptHandle)
                        .toList();
                    
                    sqsService.deleteMessageBatch(resolvedQueueUrl, receiptHandles)
                        .exceptionally(throwable -> {
                            log.warn("Failed to delete message batch for container {}: {}", 
                                containerId, throwable.getMessage());
                            return null;
                        });
                }
                
            } catch (Exception e) {
                failedMessages.addAndGet(messages.size());
                log.error("Failed to process message batch for container {}: {}", 
                    containerId, e.getMessage(), e);
                
                messages.forEach(message -> handleFailedMessage(message, e));
            }
        }, messageExecutorService);
    }
    
    /**
     * 재시도 로직을 포함한 메시지 처리
     * 
     * @SqsListener에 설정된 maxRetryAttempts와 retryDelayMillis에 따라
     * 메시지 처리를 재시도합니다. 모든 재시도가 실패한 경우 최종 예외를 throw합니다.
     * 
     * <h4>재시도 정책</h4>
     * <ul>
     *   <li>지수 백오프 없이 고정 지연 시간 사용</li>
     *   <li>각 재시도 시도 사이에 Thread.sleep() 적용</li>
     *   <li>최대 재시도 횟수 초과시 마지막 예외를 상위로 전파</li>
     * </ul>
     * 
     * @param message 처리할 SQS 메시지
     * @throws Exception 모든 재시도 실패시 마지막 발생한 예외
     */
    private void processMessageWithRetry(SqsMessage message) throws Exception {
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= listenerAnnotation.maxRetryAttempts(); attempt++) {
            try {
                invokeTargetMethod(message);
                return; // 메시지 처리 성공 - 재시도 루프 종료
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < listenerAnnotation.maxRetryAttempts()) {
                    log.warn("Processing attempt {} failed for message {} in container {}, retrying...", 
                        attempt + 1, message.getMessageId(), containerId);
                    
                    Thread.sleep(listenerAnnotation.retryDelayMillis());
                }
            }
        }
        
        throw lastException; // 모든 재시도 소진 - 최종 예외 전파
    }
    
    /**
     * 타겟 메서드 리플렉션 호출
     * 
     * @SqsListener가 지정된 실제 메시지 처리 메서드를 리플렉션을 통해 호출합니다.
     * 메서드는 생성자에서 setAccessible(true)로 설정되어 private 메서드도 호출 가능합니다.
     * 
     * @param messageParameter 메서드에 전달할 파라미터 (SqsMessage 또는 List<SqsMessage>)
     * @throws Exception 메서드 호출 중 발생한 예외 (비즈니스 로직 예외 포함)
     */
    private void invokeTargetMethod(Object messageParameter) throws Exception {
        targetMethod.invoke(targetBean, messageParameter);
    }
    
    /**
     * 실패한 메시지의 Dead Letter Queue(DLQ) 처리
     * 
     * 메시지 처리에 실패한 경우 설정에 따라 DLQ로 메시지를 전송합니다.
     * 보안상 안전한 JSON 직렬화를 위해 Jackson ObjectMapper를 사용합니다.
     * 
     * <h4>DLQ 메시지 구조</h4>
     * <ul>
     *   <li>원본 메시지 ID 및 내용</li>
     *   <li>발생한 예외 정보 (메시지 및 타입)</li>
     *   <li>실패 시간 및 컨테이너 정보</li>
     *   <li>재시도 횟수 및 원본 큐 URL</li>
     *   <li>원본 메시지의 속성들</li>
     * </ul>
     * 
     * <h4>보안 강화</h4>
     * - 문자열 연결 대신 Jackson ObjectMapper 사용으로 JSON 인젝션 공격 방지
     * - AUTO_CLOSE_SOURCE 기능 비활성화로 보안 취약점 차단
     * 
     * @param message 처리 실패한 원본 SQS 메시지
     * @param exception 발생한 예외 객체
     */
    private void handleFailedMessage(SqsMessage message, Exception exception) {
        if (listenerAnnotation.enableDeadLetterQueue() && 
            !listenerAnnotation.deadLetterQueueName().isEmpty()) {
            
            try {
                String dlqName = resolveProperty(listenerAnnotation.deadLetterQueueName());
                String dlqUrl = sqsService.getQueueUrl(dlqName).get();
                
                // 안전한 JSON 직렬화를 사용한 DLQ 메시지 생성
                DlqMessage dlqMessage = DlqMessage.builder()
                    .originalMessageId(message.getMessageId())
                    .originalMessage(message.getBody())
                    .errorMessage(exception.getMessage())
                    .errorType(exception.getClass().getSimpleName())
                    .timestamp(Instant.now())
                    .containerId(containerId)
                    .queueUrl(resolvedQueueUrl)
                    .retryAttempts(listenerAnnotation.maxRetryAttempts())
                    .originalAttributes(message.getAttributes())
                    .build();
                
                // 보안 ObjectMapper를 사용한 JSON 직렬화
                String dlqBody = OBJECT_MAPPER.writeValueAsString(dlqMessage);
                
                sqsService.sendMessage(dlqUrl, dlqBody);
                log.info("Sent failed message {} to DLQ for container {}", 
                    message.getMessageId(), containerId);
                
            } catch (JsonProcessingException jsonException) {
                log.error("Failed to serialize DLQ message for container {}: {}", 
                    containerId, jsonException.getMessage(), jsonException);
            } catch (Exception dlqException) {
                log.error("Failed to send message to DLQ for container {}: {}", 
                    containerId, dlqException.getMessage(), dlqException);
            }
        }
    }
    
    /**
     * Spring Environment를 통한 속성값 해결
     * 
     * ${property.name} 형태의 플레이스홀더를 실제 값으로 치환합니다.
     * Spring의 PropertySourcesPlaceholderConfigurer와 동일한 방식으로 작동합니다.
     * 
     * @param value 치환할 속성값 (플레이스홀더 포함 가능)
     * @return 치환된 실제 값 또는 빈 문자열
     */
    private String resolveProperty(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return environment.resolvePlaceholders(value);
    }
    
    /**
     * 컨테이너 통계 데이터 클래스
     * 
     * 컨테이너의 현재 상태와 메시지 처리 통계를 담는 불변 객체입니다.
     * 모니터링 및 운영 관리를 위한 핵심 메트릭을 제공합니다.
     * 
     * <h4>포함 정보</h4>
     * <ul>
     *   <li>컨테이너 고유 ID</li>
     *   <li>현재 실행 상태 (ContainerState)</li>
     *   <li>실행 여부 (boolean)</li>
     *   <li>총 처리된 메시지 수</li>
     *   <li>총 실패한 메시지 수</li>
     * </ul>
     */
    public static class ContainerStats {
        private final String containerId;
        private final ContainerState state;
        private final boolean running;
        private final long processedMessages;
        private final long failedMessages;
        
        public ContainerStats(String containerId, ContainerState state, boolean running, long processedMessages, long failedMessages) {
            this.containerId = containerId;
            this.state = state;
            this.running = running;
            this.processedMessages = processedMessages;
            this.failedMessages = failedMessages;
        }
        
        /** 컨테이너 고유 식별자 반환 */
        public String getContainerId() { return containerId; }
        /** 현재 컨테이너 상태 반환 */
        public ContainerState getState() { return state; }
        /** 컨테이너 실행 중 여부 반환 */
        public boolean isRunning() { return running; }
        /** 총 처리된 메시지 수 반환 */
        public long getProcessedMessages() { return processedMessages; }
        /** 총 실패한 메시지 수 반환 */
        public long getFailedMessages() { return failedMessages; }
        
        @Override
        public String toString() {
            return String.format("ContainerStats{id='%s', state=%s, running=%s, processed=%d, failed=%d}", 
                containerId, state, running, processedMessages, failedMessages);
        }
    }
}