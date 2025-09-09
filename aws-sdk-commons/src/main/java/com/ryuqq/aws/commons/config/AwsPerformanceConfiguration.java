package com.ryuqq.aws.commons.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AWS SDK 성능 최적화 설정 (간소화 버전)
 */
@Slf4j
@Configuration
public class AwsPerformanceConfiguration {

    /**
     * AWS 작업용 최적화된 스레드 풀
     */
    @Bean("awsTaskExecutor")
    public AsyncTaskExecutor awsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        int processors = Runtime.getRuntime().availableProcessors();
        
        executor.setCorePoolSize(processors);
        executor.setMaxPoolSize(processors * 4);
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        
        executor.setThreadNamePrefix("aws-task-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        log.info("Configured AWS task executor with {} core threads, {} max threads",
                processors, processors * 4);
        
        return executor;
    }
}