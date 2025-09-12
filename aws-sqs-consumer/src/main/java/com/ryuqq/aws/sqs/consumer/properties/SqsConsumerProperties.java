package com.ryuqq.aws.sqs.consumer.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;

/**
 * SQS Consumer 설정 속성 클래스
 * 
 * AWS SQS Consumer의 동작을 제어하는 모든 설정 값들을 중앙에서 관리합니다.
 * Spring Boot의 @ConfigurationProperties를 사용하여 application.yml/properties 파일에서 설정을 주입받습니다.
 * 
 * <h3>설정 카테고리</h3>
 * <ul>
 *   <li><strong>메시지 처리 설정</strong>: 동시성, 타임아웃, 배치 사이즈 등</li>
 *   <li><strong>스레드 풀 설정</strong>: Platform/Virtual Thread 설정</li>
 *   <li><strong>재시도 및 오류 처리</strong>: 재시도 정책, DLQ 설정</li>
 *   <li><strong>모니터링</strong>: 메트릭 수집, 헬스 체크 설정</li>
 * </ul>
 * 
 * <h3>설정 예시</h3>
 * <pre>
 * aws:
 *   sqs:
 *     consumer:
 *       thread-pool-core-size: 10
 *       thread-pool-max-size: 50
 *       default-max-concurrent-messages: 10
 *       executor:
 *         type: VIRTUAL_THREADS
 *         prefer-virtual-threads: true
 * </pre>
 * 
 * @see SqsListener
 * @see ExecutorServiceProvider
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "aws.sqs.consumer")
public class SqsConsumerProperties {
    
    /**
     * 리스너당 기본 최대 동시 연결 메시지 수
     * 
     * 개별 리스너에서 @SqsListener의 maxConcurrentMessages를 지정하지 않을 경우 사용되는 기본값입니다.
     * 너무 큰 값을 설정하면 메모리 부족이나 데이터베이스 연결 고갈이 발생할 수 있습니다.
     */
    private int defaultMaxConcurrentMessages = 10;
    
    /**
     * 기본 Long Polling 타임아웃 (초 단위, 0-20 범위)
     * 
     * AWS SQS Long Polling의 대기 시간을 설정합니다.
     * 20초로 설정하면 메시지가 도착할 때까지 최대 20초간 대기하다가 결과를 반환합니다.
     * 높은 값일수록 빈 폴링 요청이 줄어들어 비용이 절약됩니다.
     */
    private int defaultPollTimeoutSeconds = 20;
    
    /**
     * 기본 메시지 가시성 타임아웃 (초 단위)
     * 
     * 메시지를 수신한 후 다른 소비자에게 보이지 않는 시간입니다.
     * 이 시간 내에 메시지를 삭제하지 않으면 다시 다른 소비자가 수신할 수 있습니다.
     * 메시지 처리 시간보다 충분히 긴 값으로 설정해야 합니다.
     */
    private int defaultMessageVisibilitySeconds = 30;
    
    /**
     * 기본 폴링당 최대 메시지 수 (1-10 범위)
     * 
     * 한 번의 SQS receiveMessage 호출로 가져올 수 있는 최대 메시지 수입니다.
     * AWS SQS의 제한으로 인해 최대 10개까지만 가능합니다.
     * 높은 값일수록 네트워크 효율성이 좋아지지만 메시지 처리 지연이 발생할 수 있습니다.
     */
    private int defaultMaxMessagesPerPoll = 10;
    
    /**
     * 기본 배치 처리 크기
     * 
     * @SqsListener의 batchMode=true일 때 한 번에 처리할 메시지 개수입니다.
     * 배치 처리는 성능을 향상시키지만 오류 전파성이 높아질 수 있습닄다.
     * maxMessagesPerPoll 값과 일치시키는 것이 일반적입니다.
     */
    private int defaultBatchSize = 10;
    
    /**
     * 기본 최대 재시도 횟수
     * 
     * 메시지 처리 실패 시 재시도할 최대 횟수입니다.
     * 0으로 설정하면 재시도하지 않고 즉시 실패 처리를 수행합니다.
     * 너무 높은 값은 비정상 메시지의 무한 반복 처리를 유발할 수 있습니다.
     */
    private int defaultMaxRetryAttempts = 3;
    
    /**
     * 기본 재시도 지연 시간 (밀리초 단위)
     * 
     * 재시도 사이의 대기 시간입니다.
     * 너무 짧으면 서버에 부하를 주고, 너무 길면 전체 처리 성능이 저하됩니다.
     * Exponential Backoff 전략 대신 고정 지연 시간을 사용합니다.
     */
    private long defaultRetryDelayMillis = 1000L;
    
    /**
     * 기본 메시지 자동 삭제 동작
     * 
     * true로 설정하면 메시지 처리 성공 후 SQS에서 자동으로 메시지를 삭제합니다.
     * false로 설정하면 수동으로 메시지 삭제를 처리해야 합니다.
     * 대부분의 경우 true로 사용하여 중복 처리를 방지합니다.
     */
    private boolean defaultAutoDelete = true;
    
    /**
     * 메시지 처리용 스레드 풀 크기 (하위 호환성)
     * 
     * @deprecated threadPoolCoreSize와 threadPoolMaxSize를 사용하세요
     * 기존 설정과의 하위 호환성을 위해 유지되는 속성입니다.
     */
    private int threadPoolSize = 20;
    
    /**
     * 스레드 풀 기본 크기
     * 
     * Platform Thread 모드에서 스레드 풀의 생성되는 기본 스레드 수입니다.
     * 이 수만큼의 스레드는 작업이 없어도 대기 상태로 유지됩니다.
     * Virtual Thread 모드에서는 이 설정이 무시됩니다.
     */
    private int threadPoolCoreSize = 10;
    
    /**
     * 스레드 풀 최대 크기
     * 
     * Platform Thread 모드에서 생성될 수 있는 최대 스레드 수입니다.
     * 작업 대기열이 꽉 찌고 새로운 작업이 들어올 때 이 값까지 스레드를 생성합니다.
     * Virtual Thread 모드에서는 이 설정이 무시됩니다.
     */
    private int threadPoolMaxSize = 50;
    
    /**
     * 스레드 풀 대기열 용량
     * 
     * Platform Thread 모드에서 작업이 대기할 수 있는 최대 크기입니다.
     * 이 크기를 초과하면 새로운 스레드를 생성하거나 작업을 거부합니다.
     * Virtual Thread 모드에서는 대기열 개념이 없어 이 설정이 무시됩니다.
     */
    private int threadPoolQueueCapacity = 100;
    
    /**
     * 스레드 풀 생존 시간 (초 단위)
     * 
     * Platform Thread 모드에서 추가로 생성된 스레드(코어 사이즈 초과)가 유휴 상태로 대기하는 최대 시간입니다.
     * 이 시간이 초과하면 유휴 스레드는 종료되어 자원을 절약합니다.
     * Virtual Thread 모드에서는 이 설정이 무시됩니다.
     */
    private int threadPoolKeepAliveSeconds = 60;
    
    /**
     * 컨슈머 스레드 이름 접두사
     * 
     * 생성되는 스레드의 이름 앞에 붙이는 접두사입니다.
     * 디버깅과 모니터링에서 스레드를 식별하는 데 도움이 됩니다.
     * 예시: "sqs-consumer-1", "sqs-consumer-poller-queue1" 등
     */
    private String threadNamePrefix = "sqs-consumer-";
    
    /**
     * 컨슈머 메트릭 수집 활성화 여부
     * 
     * true로 설정하면 메시지 처리 통계, 성능 지표, 컨테이너 상태 등을 수집합니다.
     * false로 설정하면 메트릭 수집을 중단하여 성능을 약간 향상시킬 수 있습니다.
     * 프로덕션 환경에서는 모니터링을 위해 활성화하는 것을 권장합니다.
     */
    private boolean enableMetrics = true;
    
    /**
     * 헬스 체크 간격 (밀리초 단위)
     * 
     * 컨테이너들의 상태를 주기적으로 체크하는 간격입니다.
     * 이 간격으로 컨테이너의 상태, 메트릭, 네트워크 연결 상태 등을 모니터링합니다.
     * 너무 짧으면 성능에 영향을 주고, 너무 길면 문제 탐지가 지연됩니다.
     */
    private long healthCheckIntervalMillis = 30000L;
    
    /**
     * 종료 타임아웃 (밀리초 단위)
     * 
     * 애플리케이션 종료 시 컨슈머가 정상적으로 종료될 때까지 대기하는 최대 시간입니다.
     * 이 시간을 초과하면 강제로 종료되며, 진행 중인 메시지 처리가 중단될 수 있습니다.
     * 최대 메시지 처리 시간보다 충분히 큰 값으로 설정해야 합니다.
     */
    private long shutdownTimeoutMillis = 30000L;
    
    /**
     * ExecutorService 설정 속성
     * 
     * Platform Thread와 Virtual Thread 중 선택하고 세부 동작을 제어하는 설정들입니다.
     * Java 21 환경에서 Virtual Thread를 사용할 수 있으며, 그렇지 않은 경우 Platform Thread로 자동 동작합니다.
     */
    private final Executor executor = new Executor();
    
    @Getter
    @Setter
    public static class Executor {
        
        /**
         * ExecutorService 타입: PLATFORM_THREADS, VIRTUAL_THREADS, CUSTOM 중 선택
         * 
         * <ul>
         *   <li><strong>PLATFORM_THREADS</strong>: 전통적인 OS 스레드 사용 (기본값, 하위 호환성)</li>
         *   <li><strong>VIRTUAL_THREADS</strong>: Java 21+ Virtual Thread 사용 (고성능, I/O 집약적)</li>
         *   <li><strong>CUSTOM</strong>: 사용자 정의 ExecutorServiceProvider 사용</li>
         * </ul>
         * 
         * Java 21 이전 버전에서 VIRTUAL_THREADS로 설정하면 PLATFORM_THREADS로 대체됩니다.
         */
        private ExecutorType type = ExecutorType.PLATFORM_THREADS;
        
        /**
         * Virtual Thread 우선 사용 여부
         * 
         * true로 설정하면 Java 21+ 환경에서 Virtual Thread가 사용 가능할 때 자동으로 전환합니다.
         * type 속성보다 더 지능적인 선택을 원할 때 사용합니다.
         * 
         * <h4>동작 방식</h4>
         * <ul>
         *   <li>Java 21+ & Virtual Thread 지원 → Virtual Thread 사용</li>
         *   <li>그 외의 경우 → Platform Thread로 자동 대체</li>
         * </ul>
         */
        private boolean preferVirtualThreads = false;
        
        /**
         * Executor 모니터링 및 메트릭 활성화 여부
         * 
         * true로 설정하면 ExecutorService의 상태, 성능, 스레드 풀 사용률 등을 모니터링합니다.
         * 메트릭 수집은 약간의 성능 비용이 있지만 운영 모니터링에 매우 유용합니다.
         */
        private boolean enableMonitoring = true;
        
        /**
         * 사용자 정의 ExecutorServiceProvider 빈 이름
         * 
         * type=CUSTOM으로 설정했을 때만 사용되며, Spring 컨텍스트에서 해당 이름의 빈을 찾아 사용합니다.
         * 사용자가 특별한 ExecutorService 구현체를 제공하고 싶을 때 유용합니다.
         * 
         * 예시: "myCustomExecutorProvider"
         */
        private String customProviderBeanName;
    }
    
    /**
     * 지원되는 ExecutorService 타입들
     * 
     * AWS SQS Consumer에서 사용할 수 있는 스레딩 모델을 정의합니다.
     * 각 타입은 서로 다른 성능 특성과 사용 사례를 가지고 있습니다.
     */
    public enum ExecutorType {
        /** 전통적인 OS 수준 플랫폼 스레드 - 모든 Java 버전에서 사용 가능, CPU 집약적 작업에 적합 */
        PLATFORM_THREADS,
        
        /** Java 21+ Virtual Thread - 경량 스레드, I/O 집약적 작업에 최적화, 대량 동시 실행 가능 */
        VIRTUAL_THREADS,
        
        /** 사용자 정의 Executor - ExecutorServiceProvider 인터페이스를 구현한 사용자 정의 구현체 */
        CUSTOM
    }
    
    /**
     * 설정 초기화 후 검증을 수행합니다.
     * 
     * <p>Spring Boot가 속성을 주입한 후 자동으로 호출되어 
     * 설정 값의 유효성을 검증하고 필요시 보정 작업을 수행합니다.</p>
     * 
     * @throws IllegalArgumentException 유효하지 않은 설정 값이 있을 때
     */
    @PostConstruct
    public void validateAndAdjustSettings() {
        validateConcurrencySettings();
        validateTimeoutSettings();
        validateRetrySettings();
        validateThreadPoolSettings();
        validateExecutorSettings();
        adjustCompatibilitySettings();
        
        logConfigurationSummary();
    }
    
    /**
     * 동시성 관련 설정을 검증합니다.
     */
    private void validateConcurrencySettings() {
        if (defaultMaxConcurrentMessages < 1 || defaultMaxConcurrentMessages > 100) {
            throw new IllegalArgumentException(
                "defaultMaxConcurrentMessages는 1-100 범위여야 합니다: " + defaultMaxConcurrentMessages
            );
        }
        
        if (defaultMaxMessagesPerPoll < 1 || defaultMaxMessagesPerPoll > 10) {
            throw new IllegalArgumentException(
                "defaultMaxMessagesPerPoll은 1-10 범위여야 합니다: " + defaultMaxMessagesPerPoll
            );
        }
        
        if (defaultBatchSize < 1 || defaultBatchSize > 10) {
            throw new IllegalArgumentException(
                "defaultBatchSize는 1-10 범위여야 합니다: " + defaultBatchSize
            );
        }
        
        // 배치 크기는 폴링당 메시지 수보다 클 수 없음
        if (defaultBatchSize > defaultMaxMessagesPerPoll) {
            throw new IllegalArgumentException(
                String.format("defaultBatchSize(%d)는 defaultMaxMessagesPerPoll(%d)보다 클 수 없습니다", 
                    defaultBatchSize, defaultMaxMessagesPerPoll)
            );
        }
    }
    
    /**
     * 타임아웃 관련 설정을 검증합니다.
     */
    private void validateTimeoutSettings() {
        if (defaultPollTimeoutSeconds < 0 || defaultPollTimeoutSeconds > 20) {
            throw new IllegalArgumentException(
                "defaultPollTimeoutSeconds는 0-20 범위여야 합니다: " + defaultPollTimeoutSeconds
            );
        }
        
        if (defaultMessageVisibilitySeconds < 0 || defaultMessageVisibilitySeconds > 43200) {
            throw new IllegalArgumentException(
                "defaultMessageVisibilitySeconds는 0-43200 범위여야 합니다: " + defaultMessageVisibilitySeconds
            );
        }
        
        if (healthCheckIntervalMillis < 1000 || healthCheckIntervalMillis > 300000) {
            throw new IllegalArgumentException(
                "healthCheckIntervalMillis는 1000-300000 범위여야 합니다: " + healthCheckIntervalMillis
            );
        }
        
        if (shutdownTimeoutMillis < 0 || shutdownTimeoutMillis > 600000) {
            throw new IllegalArgumentException(
                "shutdownTimeoutMillis는 0-600000 범위여야 합니다: " + shutdownTimeoutMillis
            );
        }
    }
    
    /**
     * 재시도 관련 설정을 검증합니다.
     */
    private void validateRetrySettings() {
        if (defaultMaxRetryAttempts < 0 || defaultMaxRetryAttempts > 10) {
            throw new IllegalArgumentException(
                "defaultMaxRetryAttempts는 0-10 범위여야 합니다: " + defaultMaxRetryAttempts
            );
        }
        
        if (defaultRetryDelayMillis < 100 || defaultRetryDelayMillis > 60000) {
            throw new IllegalArgumentException(
                "defaultRetryDelayMillis는 100-60000 범위여야 합니다: " + defaultRetryDelayMillis
            );
        }
    }
    
    /**
     * 스레드 풀 관련 설정을 검증합니다.
     */
    private void validateThreadPoolSettings() {
        if (threadPoolCoreSize < 1 || threadPoolCoreSize > 200) {
            throw new IllegalArgumentException(
                "threadPoolCoreSize는 1-200 범위여야 합니다: " + threadPoolCoreSize
            );
        }
        
        if (threadPoolMaxSize < threadPoolCoreSize || threadPoolMaxSize > 500) {
            throw new IllegalArgumentException(
                "threadPoolMaxSize는 " + threadPoolCoreSize + "-500 범위여야 합니다: " + threadPoolMaxSize
            );
        }
        
        if (threadPoolQueueCapacity < 0 || threadPoolQueueCapacity > 10000) {
            throw new IllegalArgumentException(
                "threadPoolQueueCapacity는 0-10000 범위여야 합니다: " + threadPoolQueueCapacity
            );
        }
        
        if (threadPoolKeepAliveSeconds < 1 || threadPoolKeepAliveSeconds > 3600) {
            throw new IllegalArgumentException(
                "threadPoolKeepAliveSeconds는 1-3600 범위여야 합니다: " + threadPoolKeepAliveSeconds
            );
        }
        
        if (!StringUtils.hasText(threadNamePrefix)) {
            throw new IllegalArgumentException("threadNamePrefix는 비어있을 수 없습니다");
        }
        
        // 하위 호환성 필드 검증
        if (threadPoolSize < 1 || threadPoolSize > 500) {
            throw new IllegalArgumentException(
                "threadPoolSize는 1-500 범위여야 합니다: " + threadPoolSize
            );
        }
    }
    
    /**
     * ExecutorService 관련 설정을 검증합니다.
     */
    private void validateExecutorSettings() {
        if (executor.type == null) {
            throw new IllegalArgumentException("executor.type은 null일 수 없습니다");
        }
        
        // CUSTOM 타입일 때 빈 이름 검증
        if (executor.type == ExecutorType.CUSTOM) {
            if (!StringUtils.hasText(executor.customProviderBeanName)) {
                throw new IllegalArgumentException(
                    "executor.type이 CUSTOM일 때 customProviderBeanName은 필수입니다"
                );
            }
        }
    }
    
    /**
     * 하위 호환성을 위한 설정 보정을 수행합니다.
     */
    private void adjustCompatibilitySettings() {
        // threadPoolSize (deprecated)가 설정되었지만 새로운 설정이 기본값인 경우
        // 하위 호환성을 위해 threadPoolSize 값을 새로운 설정에 적용
        if (threadPoolSize != 20 && threadPoolCoreSize == 10 && threadPoolMaxSize == 50) {
            threadPoolCoreSize = Math.max(1, threadPoolSize / 2);
            threadPoolMaxSize = threadPoolSize;
            
            // 로그를 통해 마이그레이션 권고
            System.out.printf("경고: threadPoolSize는 deprecated입니다. " +
                "threadPoolCoreSize=%d, threadPoolMaxSize=%d로 마이그레이션하세요%n", 
                threadPoolCoreSize, threadPoolMaxSize);
        }
        
        // Virtual Thread 우선 설정이 true이고 PLATFORM_THREADS로 설정된 경우
        if (executor.preferVirtualThreads && executor.type == ExecutorType.PLATFORM_THREADS) {
            // Java 21+ 환경에서는 Virtual Thread로 변경 시도
            try {
                Class.forName("java.lang.Thread$Builder$OfVirtual");
                executor.type = ExecutorType.VIRTUAL_THREADS;
                
                System.out.println("정보: preferVirtualThreads=true이고 Virtual Thread가 지원되어 " +
                    "executor.type을 VIRTUAL_THREADS로 자동 변경했습니다");
            } catch (ClassNotFoundException e) {
                System.out.println("정보: preferVirtualThreads=true이지만 Virtual Thread가 " +
                    "지원되지 않아 PLATFORM_THREADS를 사용합니다");
            }
        }
    }
    
    /**
     * 현재 설정 요약을 로그로 출력합니다.
     */
    private void logConfigurationSummary() {
        if (System.getProperty("aws.sqs.consumer.log-config", "false").equals("true")) {
            System.out.println("=== AWS SQS Consumer 설정 요약 ===");
            System.out.printf("동시성: 최대 동시 메시지=%d, 폴링당 메시지=%d, 배치 크기=%d%n",
                defaultMaxConcurrentMessages, defaultMaxMessagesPerPoll, defaultBatchSize);
            System.out.printf("타임아웃: 폴링=%d초, 가시성=%d초, 종료=%d밀리초%n",
                defaultPollTimeoutSeconds, defaultMessageVisibilitySeconds, shutdownTimeoutMillis);
            System.out.printf("재시도: 최대 재시도=%d회, 지연=%d밀리초%n",
                defaultMaxRetryAttempts, defaultRetryDelayMillis);
            System.out.printf("스레드 풀: 코어=%d, 최대=%d, 큐=%d, 생존시간=%d초%n",
                threadPoolCoreSize, threadPoolMaxSize, threadPoolQueueCapacity, threadPoolKeepAliveSeconds);
            System.out.printf("Executor: 타입=%s, 모니터링=%s%n",
                executor.type, executor.enableMonitoring);
            System.out.println("====================================");
        }
    }
}