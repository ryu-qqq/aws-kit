package com.ryuqq.aws.sqs.consumer.executor;

import com.ryuqq.aws.sqs.consumer.properties.SqsConsumerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for SQS consumer executor services.
 * 
 * <p>This configuration provides automatic selection and setup of ExecutorServiceProvider
 * based on the application properties and runtime environment:</p>
 * 
 * <ul>
 *   <li>PLATFORM_THREADS: Traditional thread pool with configurable sizing</li>
 *   <li>VIRTUAL_THREADS: Java 21 virtual threads with fallback to platform threads</li>
 *   <li>CUSTOM: User-provided ExecutorServiceProvider bean</li>
 *   <li>AUTO: Intelligent selection based on Java version and availability</li>
 * </ul>
 * 
 * <p>The configuration also handles proper shutdown and cleanup of executors
 * during application shutdown.</p>
 * 
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "aws.sqs.consumer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SqsExecutorConfiguration implements ApplicationContextAware, DisposableBean {
    
    private ApplicationContext applicationContext;
    private ExecutorServiceProvider activeProvider;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Creates the primary ExecutorServiceProvider bean based on configuration.
     * 
     * @param properties the SQS consumer properties
     * @return configured ExecutorServiceProvider
     */
    @Bean
    @ConditionalOnMissingBean
    public ExecutorServiceProvider executorServiceProvider(SqsConsumerProperties properties) {
        SqsConsumerProperties.ExecutorType configuredType = properties.getExecutor().getType();
        boolean preferVirtualThreads = properties.getExecutor().isPreferVirtualThreads();
        
        log.info("Configuring SQS executor with type: {}, preferVirtualThreads: {}", 
                configuredType, preferVirtualThreads);
        
        ExecutorServiceProvider provider = createExecutorProvider(
                configuredType, preferVirtualThreads, properties);
        
        this.activeProvider = provider;
        
        log.info("Created ExecutorServiceProvider: {} (threading model: {})", 
                provider.getClass().getSimpleName(), 
                provider.getThreadingModel());
        
        return provider;
    }
    
    /**
     * Creates a platform thread executor provider.
     * Only created when explicitly configured or as fallback.
     */
    @Bean
    @ConditionalOnProperty(prefix = "aws.sqs.consumer.executor", name = "type", havingValue = "PLATFORM_THREADS")
    @ConditionalOnMissingBean(name = "platformThreadExecutorServiceProvider")
    public PlatformThreadExecutorServiceProvider platformThreadExecutorServiceProvider(
            SqsConsumerProperties properties) {
        log.info("Creating explicit PlatformThreadExecutorServiceProvider");
        return new PlatformThreadExecutorServiceProvider(properties);
    }
    
    /**
     * Creates a virtual thread executor provider when explicitly configured.
     * Requires Java 21+ with virtual threads support.
     */
    @Bean
    @ConditionalOnClass(name = "java.lang.Thread$Builder$OfVirtual")
    @ConditionalOnProperty(prefix = "aws.sqs.consumer.executor", name = "type", havingValue = "VIRTUAL_THREADS")
    @ConditionalOnMissingBean(name = "virtualThreadExecutorServiceProvider")
    public VirtualThreadExecutorServiceProvider virtualThreadExecutorServiceProvider() {
        log.info("Creating explicit VirtualThreadExecutorServiceProvider");
        return new VirtualThreadExecutorServiceProvider();
    }
    
    @Override
    public void destroy() throws Exception {
        if (activeProvider != null && activeProvider.supportsGracefulShutdown()) {
            log.info("Shutting down active ExecutorServiceProvider: {}", 
                    activeProvider.getClass().getSimpleName());
            
            try {
                // Use configured shutdown timeout or default to 30 seconds
                long shutdownTimeout = 30000L;
                if (applicationContext.containsBean("sqsConsumerProperties")) {
                    SqsConsumerProperties properties = applicationContext.getBean(SqsConsumerProperties.class);
                    shutdownTimeout = properties.getShutdownTimeoutMillis();
                }
                
                activeProvider.shutdown(shutdownTimeout);
                log.info("Successfully shut down ExecutorServiceProvider");
                
            } catch (Exception e) {
                log.error("Error during ExecutorServiceProvider shutdown: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Creates the appropriate ExecutorServiceProvider based on configuration and runtime capabilities.
     */
    private ExecutorServiceProvider createExecutorProvider(
            SqsConsumerProperties.ExecutorType configuredType,
            boolean preferVirtualThreads,
            SqsConsumerProperties properties) {
        
        switch (configuredType) {
            case PLATFORM_THREADS:
                return new PlatformThreadExecutorServiceProvider(properties);
                
            case VIRTUAL_THREADS:
                return createVirtualThreadsProvider(properties);
                
            case CUSTOM:
                return createCustomProvider(properties);
                
            default:
                // Auto-selection logic
                if (preferVirtualThreads && VirtualThreadExecutorServiceProvider.isVirtualThreadsSupported()) {
                    log.info("Auto-selecting VirtualThreadExecutorServiceProvider (virtual threads available)");
                    return new VirtualThreadExecutorServiceProvider();
                } else {
                    log.info("Auto-selecting PlatformThreadExecutorServiceProvider " +
                            "(virtual threads not available or not preferred)");
                    return new PlatformThreadExecutorServiceProvider(properties);
                }
        }
    }
    
    /**
     * Creates VirtualThreadExecutorServiceProvider with fallback to platform threads.
     */
    private ExecutorServiceProvider createVirtualThreadsProvider(SqsConsumerProperties properties) {
        if (VirtualThreadExecutorServiceProvider.isVirtualThreadsSupported()) {
            log.info("Creating VirtualThreadExecutorServiceProvider (Java 21+ virtual threads)");
            return new VirtualThreadExecutorServiceProvider();
        } else {
            log.warn("Virtual threads requested but not available, falling back to platform threads. " +
                    "Requires Java 21+ with virtual threads support.");
            return new PlatformThreadExecutorServiceProvider(properties);
        }
    }
    
    /**
     * Creates CustomExecutorServiceProvider using user-provided bean.
     */
    private ExecutorServiceProvider createCustomProvider(SqsConsumerProperties properties) {
        String customBeanName = properties.getExecutor().getCustomProviderBeanName();
        
        if (customBeanName == null || customBeanName.trim().isEmpty()) {
            // Try to find any ExecutorServiceProvider bean
            try {
                ExecutorServiceProvider customProvider = applicationContext.getBean(ExecutorServiceProvider.class);
                log.info("Found custom ExecutorServiceProvider bean: {}", 
                        customProvider.getClass().getSimpleName());
                return customProvider;
            } catch (BeansException e) {
                throw new IllegalStateException(
                    "Custom executor type specified but no ExecutorServiceProvider bean found. " +
                    "Either provide a custom ExecutorServiceProvider bean or specify the bean name in " +
                    "aws.sqs.consumer.executor.customProviderBeanName", e);
            }
        } else {
            // Use specifically named bean
            try {
                ExecutorServiceProvider customProvider = applicationContext.getBean(
                        customBeanName, ExecutorServiceProvider.class);
                log.info("Found custom ExecutorServiceProvider bean '{}': {}", 
                        customBeanName, customProvider.getClass().getSimpleName());
                return customProvider;
            } catch (BeansException e) {
                throw new IllegalStateException(
                    "Custom ExecutorServiceProvider bean '" + customBeanName + "' not found", e);
            }
        }
    }
}