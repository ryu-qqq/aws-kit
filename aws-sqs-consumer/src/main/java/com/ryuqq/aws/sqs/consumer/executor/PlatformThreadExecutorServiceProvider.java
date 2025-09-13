package com.ryuqq.aws.sqs.consumer.executor;

import com.ryuqq.aws.sqs.consumer.properties.SqsConsumerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Platform Thread ExecutorService 제공자
 * 
 * 전통적인 Java Platform Thread를 사용하여 스레드 풀 기반의 ExecutorService를 제공합니다.
 * Virtual Thread와 달리 OS 수준의 네이티브 스레드를 사용하여 CPU 집약적 작업에 적합합니다.
 * 
 * <h3>Platform Thread의 특징</h3>
 * <ul>
 *   <li><strong>OS 네이티브 스레드</strong>: 운영체제가 직접 관리하는 스레드</li>
 *   <li><strong>CPU 집약적 작업 최적</strong>: 계산 위주의 작업에서 높은 성능</li>
 *   <li><strong>메모리 비용</strong>: 각 스레드당 1-2MB 스택 메모리 사용</li>
 *   <li><strong>스레드 풀 관리</strong>: 고정 크기의 스레드 풀로 리소스 제어</li>
 * </ul>
 * 
 * <h3>Virtual Thread와의 비교</h3>
 * <table border="1">
 *   <tr><th>요소</th><th>Platform Thread</th><th>Virtual Thread</th></tr>
 *   <tr><td>메모리 사용량</td><td>1-2MB/스레드</td><td>수 KB/스레드</td></tr>
 *   <tr><td>동시 실행 가능 수</td><td>수십-수백개</td><td>수백만개</td></tr>
 *   <tr><td>CPU 집약적 작업</td><td>우수</td><td>보통</td></tr>
 *   <tr><td>I/O 집약적 작업</td><td>보통</td><td>우수</td></tr>
 * </table>
 * 
 * <h3>사용 구리어</h3>
 * <ul>
 *   <li>Java 21 이전 버전에서의 호환성 필요시</li>
 *   <li>계산 위주의 메시지 처리가 많은 경우</li>
 *   <li>스레드 수를 세밀하게 제어해야 하는 경우</li>
 * </ul>
 * 
 * @see VirtualThreadExecutorServiceProvider
 * @see ExecutorServiceProvider
 */
public class PlatformThreadExecutorServiceProvider implements ExecutorServiceProvider {

    private static final Logger log = LoggerFactory.getLogger(PlatformThreadExecutorServiceProvider.class);

    private final SqsConsumerProperties properties;

    public PlatformThreadExecutorServiceProvider(SqsConsumerProperties properties) {
        this.properties = properties;
    }
    private volatile ExecutorService executorService;
    
    /**
     * 메시지 처리용 Platform Thread ExecutorService 생성
     * 
     * 고정 크기의 스레드 풀을 사용하여 메시지 처리 전용 ExecutorService를 생성합니다.
     * 스레드 풀 크기는 SqsConsumerProperties에서 설정할 수 있습니다.
     * 
     * <h4>스레드 풀 설정</h4>
     * <ul>
     *   <li><strong>Core Pool Size</strong>: properties.getThreadPoolCoreSize()</li>
     *   <li><strong>Maximum Pool Size</strong>: properties.getThreadPoolMaxSize()</li>
     *   <li><strong>스레드 명명</strong>: "sqs-msg-processor-{consumerName}-{threadNumber}"</li>
     *   <li><strong>Daemon Thread</strong>: true (애플리케이션 종료시 자동 종료)</li>
     * </ul>
     * 
     * @param consumerName 컨슈머 이름 (스레드 명명에 사용)
     * @return 고정 크기 스레드 풀 ExecutorService
     */
    @Override
    public ExecutorService createMessageProcessingExecutor(String consumerName) {
        int corePoolSize = properties.getThreadPoolCoreSize();
        int maxPoolSize = properties.getThreadPoolMaxSize();
        
        // 메시지 처리 스레드용 ThreadFactory 생성
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "sqs-msg-processor-" + consumerName + "-" + threadNumber.getAndIncrement());
                thread.setDaemon(true); // 데먼 스레드로 설정 - JVM 종료시 자동 종료
                return thread;
            }
        };
        
        return Executors.newFixedThreadPool(corePoolSize, threadFactory);
    }
    
    /**
     * 메시지 폴링용 Platform Thread ExecutorService 생성
     * 
     * SQS Long Polling을 수행하는 단일 스레드 ExecutorService를 생성합니다.
     * 폴링은 단일 스레드로 충분하므로 단일 스레드 ExecutorService를 사용합니다.
     * 
     * <h4>폴링 스레드 특성</h4>
     * <ul>
     *   <li><strong>단일 스레드</strong>: 하나의 전용 폴링 스레드</li>
     *   <li><strong>스레드 명명</strong>: "sqs-poller-{consumerName}"</li>
     *   <li><strong>Daemon Thread</strong>: true (애플리케이션 종료시 자동 종료)</li>
     *   <li><strong>Long Polling 지원</strong>: I/O 대기 시간이 길어도 안정적 동작</li>
     * </ul>
     * 
     * @param consumerName 컨슈머 이름 (스레드 명명에 사용)
     * @return 단일 스레드 ExecutorService
     */
    @Override
    public ExecutorService createPollingExecutor(String consumerName) {
        // 폴링 스레드용 ThreadFactory 생성
        ThreadFactory threadFactory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "sqs-poller-" + consumerName);
                thread.setDaemon(true); // 데먼 스레드로 설정
                return thread;
            }
        };
        
        return Executors.newSingleThreadExecutor(threadFactory);
    }
    
    /**
     * 스레딩 모델 반환
     * 
     * @return Platform Thread 모델 상수
     */
    @Override
    public ThreadingModel getThreadingModel() {
        return ThreadingModel.PLATFORM_THREADS;
    }
    
    /**
     * Graceful Shutdown 지원 여부
     * 
     * Platform Thread ExecutorService는 정상적인 shutdown 절차를 지원합니다.
     * 진행 중인 작업이 완료될 때까지 대기합니다.
     * 
     * @return 항상 true
     */
    @Override
    public boolean supportsGracefulShutdown() {
        return true;
    }
    
    /**
     * Platform Thread ExecutorService Graceful Shutdown
     * 
     * 다단계 shutdown 절차를 수행하여 안전하게 스레드 풀을 종료합니다.
     * 진행 중인 작업이 완료될 때까지 대기한 후, 타임아웃 초과시 강제 종료합니다.
     * 
     * <h4>Shutdown 절차</h4>
     * <ol>
     *   <li>새로운 작업 수락 중단 (executorService.shutdown())</li>
     *   <li>지정된 시간만큼 진행 중인 작업 완료 대기</li>
     *   <li>타임아웃 초과시 강제 종료 (shutdownNow())</li>
     *   <li>5초 추가 대기 후 에러 로깅</li>
     * </ol>
     * 
     * @param timeoutMillis Graceful shutdown 대기 시간 (밀리초)
     */
    @Override
    public void shutdown(long timeoutMillis) {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("Platform Thread ExecutorService Graceful Shutdown 시작");
            executorService.shutdown();
            
            try {
                if (!executorService.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS)) {
                    log.warn("ExecutorService가 정상적으로 종료되지 않아 강제 종료를 시도합니다");
                    executorService.shutdownNow();
                    
                    // 강제 종료 후 추가 대기 시간
                    if (!executorService.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                        log.error("강제 종료 후에도 ExecutorService가 종료되지 않았습니다");
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("ExecutorService 종료 대기 중 인터럽트가 발생했습니다");
                executorService.shutdownNow();
            }
        }
    }
    
}