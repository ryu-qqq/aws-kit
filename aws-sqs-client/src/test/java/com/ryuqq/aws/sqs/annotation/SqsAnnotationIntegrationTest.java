package com.ryuqq.aws.sqs.annotation;

import com.ryuqq.aws.sqs.config.SqsAnnotationClientConfiguration;
import com.ryuqq.aws.sqs.example.NotificationService;
import com.ryuqq.aws.sqs.example.OrderQueueService;
import com.ryuqq.aws.sqs.proxy.SqsClientProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test to verify annotation-based SQS client configuration and bean registration.
 */
@SpringBootTest(classes = {
        SqsAnnotationClientConfiguration.class,
        com.ryuqq.aws.sqs.AwsSqsAutoConfiguration.class
})
@ActiveProfiles("test")
class SqsAnnotationIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SqsClientProxyFactory proxyFactory;

    @Test
    void shouldConfigureAnnotationComponents() {
        // Verify core components are configured
        assertThat(applicationContext.getBean(SqsClientProxyFactory.class)).isNotNull();
        assertThat(proxyFactory).isNotNull();
    }

    @Test
    void shouldCreateProxyForSqsClientInterface() {
        // Test proxy creation directly
        OrderQueueService orderService = proxyFactory.createProxy(OrderQueueService.class);
        
        assertThat(orderService).isNotNull();
        assertThat(orderService.getClass().getName()).contains("Proxy");
        assertThat(orderService.toString()).contains("SqsClientProxy[order-service]");
    }

    @Test
    void shouldCreateProxyForAnotherInterface() {
        // Test with different interface
        NotificationService notificationService = proxyFactory.createProxy(NotificationService.class);
        
        assertThat(notificationService).isNotNull();
        assertThat(notificationService.getClass().getName()).contains("Proxy");
        assertThat(notificationService.toString()).contains("SqsClientProxy[notification-service]");
    }

    @Test
    void shouldCacheProxyInstances() {
        // Verify proxy caching
        OrderQueueService service1 = proxyFactory.createProxy(OrderQueueService.class);
        OrderQueueService service2 = proxyFactory.createProxy(OrderQueueService.class);
        
        assertThat(service1).isSameAs(service2);
    }

    @Test
    void shouldHandleObjectMethods() {
        OrderQueueService orderService = proxyFactory.createProxy(OrderQueueService.class);
        
        // Test toString
        String toString = orderService.toString();
        assertThat(toString).isEqualTo("SqsClientProxy[order-service]");
        
        // Test hashCode
        int hashCode = orderService.hashCode();
        assertThat(hashCode).isNotZero();
        
        // Test equals
        OrderQueueService anotherService = proxyFactory.createProxy(OrderQueueService.class);
        assertThat(orderService.equals(anotherService)).isTrue();
    }
}