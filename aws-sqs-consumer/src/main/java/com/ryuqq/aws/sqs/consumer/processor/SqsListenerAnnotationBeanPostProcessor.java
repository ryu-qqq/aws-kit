package com.ryuqq.aws.sqs.consumer.processor;

import com.ryuqq.aws.sqs.consumer.annotation.SqsListener;
import com.ryuqq.aws.sqs.consumer.container.SqsListenerContainer;
import com.ryuqq.aws.sqs.consumer.executor.ExecutorServiceProvider;
import com.ryuqq.aws.sqs.consumer.registry.SqsListenerContainerRegistry;
import com.ryuqq.aws.sqs.service.SqsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;

/**
 * Bean post-processor that scans for @SqsListener annotations 
 * and creates corresponding listener containers.
 */
@Slf4j
@Component
public class SqsListenerAnnotationBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
    
    private ApplicationContext applicationContext;
    private SqsListenerContainerRegistry containerRegistry;
    private SqsService sqsService;
    private Environment environment;
    private ExecutorServiceProvider executorServiceProvider;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (applicationContext == null) {
            return bean;
        }
        
        // Lazy initialization of dependencies
        initializeDependenciesIfNeeded();
        
        Class<?> beanClass = bean.getClass();
        Method[] methods = beanClass.getDeclaredMethods();
        
        for (Method method : methods) {
            SqsListener sqsListener = method.getAnnotation(SqsListener.class);
            if (sqsListener != null) {
                processListenerMethod(bean, method, sqsListener, beanName);
            }
        }
        
        return bean;
    }
    
    private void initializeDependenciesIfNeeded() {
        if (containerRegistry == null) {
            containerRegistry = applicationContext.getBean(SqsListenerContainerRegistry.class);
        }
        if (sqsService == null) {
            sqsService = applicationContext.getBean(SqsService.class);
        }
        if (environment == null) {
            environment = applicationContext.getEnvironment();
        }
        if (executorServiceProvider == null) {
            executorServiceProvider = applicationContext.getBean(ExecutorServiceProvider.class);
        }
    }
    
    private void processListenerMethod(Object bean, Method method, SqsListener annotation, String beanName) {
        try {
            validateListenerMethod(method, annotation);
            
            String containerId = generateContainerId(beanName, method, annotation);
            
            // Create dedicated executors for this container using the provider
            ExecutorService messageExecutor = executorServiceProvider.createMessageProcessingExecutor(containerId);
            ExecutorService pollingExecutor = executorServiceProvider.createPollingExecutor(containerId);
            
            SqsListenerContainer container = new SqsListenerContainer(
                containerId,
                bean,
                method,
                annotation,
                sqsService,
                environment,
                applicationContext,
                messageExecutor,
                pollingExecutor
            );
            
            containerRegistry.registerContainer(containerId, container);
            
            log.info("Created SQS listener container '{}' for method '{}.{}' using {} threading model", 
                containerId, bean.getClass().getSimpleName(), method.getName(),
                executorServiceProvider.getThreadingModel());
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to process @SqsListener method: " + 
                bean.getClass().getSimpleName() + "." + method.getName(), e);
        }
    }
    
    private void validateListenerMethod(Method method, SqsListener annotation) {
        // Validate method parameters
        Class<?>[] parameterTypes = method.getParameterTypes();
        
        if (annotation.batchMode()) {
            // Batch mode validation
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException("@SqsListener batch mode methods must have exactly one parameter");
            }
            
            Class<?> paramType = parameterTypes[0];
            if (!java.util.List.class.isAssignableFrom(paramType)) {
                throw new IllegalArgumentException("@SqsListener batch mode methods must accept List parameter");
            }
        } else {
            // Single message mode validation
            if (parameterTypes.length != 1) {
                throw new IllegalArgumentException("@SqsListener methods must have exactly one parameter");
            }
            
            Class<?> paramType = parameterTypes[0];
            if (!com.ryuqq.aws.sqs.types.SqsMessage.class.isAssignableFrom(paramType)) {
                throw new IllegalArgumentException("@SqsListener methods must accept SqsMessage parameter");
            }
        }
        
        // Validate queue configuration
        if (annotation.queueName().isEmpty() && annotation.queueUrl().isEmpty()) {
            throw new IllegalArgumentException("@SqsListener must specify either queueName or queueUrl");
        }
        
        if (!annotation.queueName().isEmpty() && !annotation.queueUrl().isEmpty()) {
            throw new IllegalArgumentException("@SqsListener cannot specify both queueName and queueUrl");
        }
        
        // Validate numeric parameters
        if (annotation.maxConcurrentMessages() <= 0) {
            throw new IllegalArgumentException("@SqsListener maxConcurrentMessages must be positive");
        }
        
        if (annotation.pollTimeoutSeconds() < 0 || annotation.pollTimeoutSeconds() > 20) {
            throw new IllegalArgumentException("@SqsListener pollTimeoutSeconds must be between 0 and 20");
        }
        
        if (annotation.maxMessagesPerPoll() < 1 || annotation.maxMessagesPerPoll() > 10) {
            throw new IllegalArgumentException("@SqsListener maxMessagesPerPoll must be between 1 and 10");
        }
        
        if (annotation.batchSize() <= 0) {
            throw new IllegalArgumentException("@SqsListener batchSize must be positive");
        }
        
        if (annotation.maxRetryAttempts() < 0) {
            throw new IllegalArgumentException("@SqsListener maxRetryAttempts must be non-negative");
        }
        
        if (annotation.retryDelayMillis() < 0) {
            throw new IllegalArgumentException("@SqsListener retryDelayMillis must be non-negative");
        }
    }
    
    private String generateContainerId(String beanName, Method method, SqsListener annotation) {
        if (!annotation.id().isEmpty()) {
            return annotation.id();
        }
        
        return beanName + "." + method.getName();
    }
}