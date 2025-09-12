package com.ryuqq.aws.sqs.consumer.executor;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Java 21+ Virtual Thread ExecutorService 제공자
 * 
 * Java 21에서 도입된 Virtual Thread를 활용하여 I/O 집약적 작업에 최적화된 ExecutorService를 제공합니다.
 * Virtual Thread는 기존 Platform Thread와 달리 JVM이 관리하는 경량 스레드로,
 * 수백만 개의 동시 실행 스레드를 생성해도 메모리 부담이 적습니다.
 * 
 * <h3>Virtual Thread의 장점</h3>
 * <ul>
 *   <li><strong>경량성</strong>: 각 Virtual Thread는 몇 KB 메모리만 사용</li>
 *   <li><strong>I/O 최적화</strong>: I/O 대기 시 스레드가 park되어 CPU 자원 절약</li>
 *   <li><strong>스케일링</strong>: 수만개의 동시 연결 처리 가능</li>
 *   <li><strong>Platform Thread 호환성</strong>: 기존 Thread API와 동일한 인터페이스</li>
 * </ul>
 * 
 * <h3>SQS Consumer에서의 활용</h3>
 * <ul>
 *   <li><strong>메시지 처리</strong>: 각 메시지를 별도 Virtual Thread에서 비동기 처리</li>
 *   <li><strong>폴링</strong>: SQS Long Polling 중 I/O 대기 시 Virtual Thread park</li>
 *   <li><strong>동시성</strong>: 대량의 메시지를 동시에 처리 가능</li>
 * </ul>
 * 
 * <h3>주의사항</h3>
 * <ul>
 *   <li>Java 21+ 필수 - 이전 버전에서는 UnsupportedOperationException 발생</li>
 *   <li>CPU 집약적 작업에는 Platform Thread가 더 적합</li>
 *   <li>synchronized 블록에서는 Platform Thread로 자동 전환</li>
 * </ul>
 * 
 * @since Java 21
 * @see PlatformThreadExecutorServiceProvider
 * @see ExecutorServiceProvider
 */
@Slf4j
public class VirtualThreadExecutorServiceProvider implements ExecutorServiceProvider {
    
    private volatile ExecutorService executorService;
    
    /**
     * 메시지 처리용 Virtual Thread ExecutorService 생성
     * 
     * SQS 메시지를 처리하는 워커 스레드용 ExecutorService를 생성합니다.
     * 각 메시지는 개별 Virtual Thread에서 처리되어 높은 동시성을 제공합니다.
     * 
     * @param consumerName 컨슈머 이름 (스레드 명명에 사용)
     * @return Virtual Thread 기반 ExecutorService
     */
    @Override
    public ExecutorService createMessageProcessingExecutor(String consumerName) {
        return createVirtualThreadExecutor("msg-processor-" + consumerName);
    }
    
    /**
     * 메시지 폴링용 Virtual Thread ExecutorService 생성
     * 
     * SQS Long Polling을 수행하는 전용 Virtual Thread ExecutorService를 생성합니다.
     * I/O 대기 시간이 긴 폴링 작업에 Virtual Thread가 이상적입니다.
     * 
     * @param consumerName 컨슈머 이름 (스레드 명명에 사용)
     * @return Virtual Thread 기반 ExecutorService
     */
    @Override
    public ExecutorService createPollingExecutor(String consumerName) {
        return createVirtualThreadExecutor("poller-" + consumerName);
    }
    
    /**
     * 스레딩 모델 반환
     * 
     * @return Virtual Thread 모델 상수
     */
    @Override
    public ThreadingModel getThreadingModel() {
        return ThreadingModel.VIRTUAL_THREADS;
    }
    
    /**
     * Graceful Shutdown 지원 여부
     * 
     * Virtual Thread ExecutorService도 정상적인 shutdown 절차를 지원합니다.
     * 
     * @return 항상 true
     */
    @Override
    public boolean supportsGracefulShutdown() {
        return true;
    }
    
    /**
     * Virtual Thread ExecutorService 종료
     * 
     * Virtual Thread는 경량 스레드이므로 대부분의 경우 빠르게 종료됩니다.
     * 별도의 타임아웃 처리는 하지 않습니다.
     * 
     * @param timeoutMillis 타임아웃 시간 (사용되지 않음)
     */
    @Override
    public void shutdown(long timeoutMillis) {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("Virtual Thread ExecutorService 종료 시작");
            executorService.shutdown();
        }
    }
    
    /**
     * 현재 JVM에서 Virtual Thread 지원 여부 확인
     * 
     * Java 21+에서 도입된 Virtual Thread API의 존재 여부를 확인합니다.
     * Thread.Builder.OfVirtual 클래스의 존재를 리플렉션으로 검사하여 판단합니다.
     * 
     * @return Java 21+ 환경에서 Virtual Thread를 지원하면 true
     */
    public static boolean isVirtualThreadsSupported() {
        try {
            // Virtual Thread 가용성 검사 (Java 21+ 필수)
            Class.forName("java.lang.Thread$Builder$OfVirtual");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Virtual Thread ExecutorService 생성
     * 
     * Executors.newVirtualThreadPerTaskExecutor()를 리플렉션으로 호출하여 Virtual Thread ExecutorService를 생성합니다.
     * Java 21 이전 버전에서는 UnsupportedOperationException을 발생시킵니다.
     * 
     * <h4>Virtual Thread ExecutorService 특성</h4>
     * <ul>
     *   <li>Task당 하나의 Virtual Thread 생성 (고정 스레드 풀 없음)</li>
     *   <li>각 Virtual Thread는 Task 완료 후 자동 종료</li>
     *   <li>메모리 사용량이 매우 적음 (대량 동시 실행 가능)</li>
     * </ul>
     * 
     * @param prefix 스레드 이름 접두사 (디버깅용)
     * @return Virtual Thread 기반 ExecutorService
     * @throws UnsupportedOperationException Java 21 이전 버전에서 호출시
     * @throws RuntimeException Virtual Thread ExecutorService 생성 실패시
     */
    private ExecutorService createVirtualThreadExecutor(String prefix) {
        if (!isVirtualThreadsSupported()) {
            throw new UnsupportedOperationException(
                "Virtual Thread가 지원되지 않습니다. Java 21+ 버전이 필요합니다.");
        }
        
        log.info("접두사 '{}'를 사용하여 Virtual Thread ExecutorService 생성 중", prefix);
        
        try {
            // 리플렉션을 사용하여 Executors.newVirtualThreadPerTaskExecutor() 호출
            return (ExecutorService) Executors.class
                .getMethod("newVirtualThreadPerTaskExecutor")
                .invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Virtual Thread ExecutorService 생성에 실패했습니다", e);
        }
    }
}