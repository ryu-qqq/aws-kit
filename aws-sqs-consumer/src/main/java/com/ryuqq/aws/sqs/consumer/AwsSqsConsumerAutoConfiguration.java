package com.ryuqq.aws.sqs.consumer;

import com.ryuqq.aws.sqs.consumer.executor.SqsExecutorConfiguration;
import com.ryuqq.aws.sqs.consumer.processor.SqsListenerAnnotationBeanPostProcessor;
import com.ryuqq.aws.sqs.consumer.properties.SqsConsumerProperties;
import com.ryuqq.aws.sqs.consumer.registry.SqsListenerContainerRegistry;
import com.ryuqq.aws.sqs.service.SqsService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for SQS Consumer functionality.
 * Provides declarative SQS message consumption using @SqsListener annotation.
 */
@AutoConfiguration
@ConditionalOnClass({SqsService.class})
@ConditionalOnProperty(
    prefix = "aws.sqs.consumer", 
    name = "enabled", 
    havingValue = "true", 
    matchIfMissing = true
)
@EnableConfigurationProperties(SqsConsumerProperties.class)
@Import(SqsExecutorConfiguration.class)
public class AwsSqsConsumerAutoConfiguration {
    
    /**
     * Container registry for managing SQS listener containers.
     */
    @Bean
    @ConditionalOnMissingBean
    public SqsListenerContainerRegistry sqsListenerContainerRegistry() {
        return new SqsListenerContainerRegistry();
    }
    
    /**
     * Bean post-processor for scanning @SqsListener annotations.
     */
    @Bean
    @ConditionalOnMissingBean
    public SqsListenerAnnotationBeanPostProcessor sqsListenerAnnotationBeanPostProcessor() {
        return new SqsListenerAnnotationBeanPostProcessor();
    }
}