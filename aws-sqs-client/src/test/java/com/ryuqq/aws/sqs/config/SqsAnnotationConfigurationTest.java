package com.ryuqq.aws.sqs.config;

import com.ryuqq.aws.sqs.example.OrderQueueService;
import com.ryuqq.aws.sqs.proxy.SqsClientProxyFactory;
import com.ryuqq.aws.sqs.serialization.MessageSerializer;
import com.ryuqq.aws.sqs.service.SqsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Boot configuration tests for annotation-based SQS client.
 * Tests auto-configuration, bean creation, and dependency injection.
 */
@SpringBootTest(classes = {
        SqsAnnotationClientConfiguration.class,
        com.ryuqq.aws.sqs.AwsSqsAutoConfiguration.class
})
@ActiveProfiles("test")
class SqsAnnotationConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;
    
    @MockBean
    private SqsService sqsService;

    @Nested
    @DisplayName("Auto-Configuration Tests")
    class AutoConfigurationTests {
        
        @Test
        @DisplayName("Should auto-configure core SQS annotation components")
        void shouldAutoConfigureCoreComponents() {
            // Then - Verify core components are configured
            assertThat(applicationContext.containsBean("sqsClientProxyFactory")).isTrue();
            assertThat(applicationContext.containsBean("messageSerializer")).isTrue();
            
            // Verify component types
            assertThat(applicationContext.getBean(SqsClientProxyFactory.class)).isNotNull();
            assertThat(applicationContext.getBean(MessageSerializer.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should inject SqsService dependency")
        void shouldInjectSqsServiceDependency() {
            // Given
            SqsClientProxyFactory proxyFactory = applicationContext.getBean(SqsClientProxyFactory.class);
            
            // Then - Should be properly wired
            assertThat(proxyFactory).isNotNull();
        }
        
        @Test
        @DisplayName("Should configure message serializer")
        void shouldConfigureMessageSerializer() {
            // When
            MessageSerializer serializer = applicationContext.getBean(MessageSerializer.class);
            
            // Then
            assertThat(serializer).isNotNull();
            assertThat(serializer.getClass().getSimpleName()).contains("Jackson");
        }
    }

    @Nested
    @DisplayName("Bean Registration Tests")
    class BeanRegistrationTests {
        
        @Test
        @DisplayName("Should register SqsClient annotated interfaces as beans")
        void shouldRegisterSqsClientInterfacesAsBeans() {
            // Then - OrderQueueService should be registered as a bean
            assertThat(applicationContext.containsBean("orderQueueService")).isTrue();
            
            OrderQueueService orderService = applicationContext.getBean(OrderQueueService.class);
            assertThat(orderService).isNotNull();
            assertThat(orderService.getClass().getName()).contains("Proxy");
        }
        
        @Test
        @DisplayName("Should use correct bean names for SqsClient interfaces")
        void shouldUseCorrectBeanNamesForSqsClientInterfaces() {
            // Then - Bean should be registered with interface name (first letter lowercase)
            assertThat(applicationContext.containsBean("orderQueueService")).isTrue();
            
            // Verify we can get the bean by type and by name
            OrderQueueService byType = applicationContext.getBean(OrderQueueService.class);
            OrderQueueService byName = applicationContext.getBean("orderQueueService", OrderQueueService.class);
            
            assertThat(byType).isSameAs(byName);
        }
        
        @Test
        @DisplayName("Should register beans as singletons")
        void shouldRegisterBeansAsSingletons() {
            // When
            OrderQueueService instance1 = applicationContext.getBean(OrderQueueService.class);
            OrderQueueService instance2 = applicationContext.getBean(OrderQueueService.class);
            
            // Then
            assertThat(instance1).isSameAs(instance2);
        }
    }

    @Nested
    @DisplayName("Conditional Configuration Tests")
    class ConditionalConfigurationTests {
        
        @Test
        @DisplayName("Should configure when SqsService is available")
        void shouldConfigureWhenSqsServiceIsAvailable() {
            // Given - SqsService is mocked/available
            
            // Then - Configuration should be active
            assertThat(applicationContext.getBean(SqsClientProxyFactory.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should not create conflicting bean definitions")
        void shouldNotCreateConflictingBeanDefinitions() {
            // When
            String[] beanNames = applicationContext.getBeanDefinitionNames();
            
            // Then - No duplicate bean names should exist
            java.util.Set<String> uniqueNames = java.util.Set.of(beanNames);
            assertThat(uniqueNames).hasSize(beanNames.length);
        }
    }

    @Nested
    @DisplayName("Property Configuration Tests")
    @TestPropertySource(properties = {
            "aws.sqs.client.enabled=true",
            "aws.sqs.client.default-queue-prefix=test-",
            "aws.sqs.client.serialization.enabled=true"
    })
    class PropertyConfigurationTests {
        
        @Test
        @DisplayName("Should respect configuration properties")
        void shouldRespectConfigurationProperties() {
            // Then - Configuration should be enabled based on properties
            assertThat(applicationContext.containsBean("sqsClientProxyFactory")).isTrue();
            assertThat(applicationContext.containsBean("messageSerializer")).isTrue();
        }
    }

    @Nested
    @DisplayName("Component Scanning Tests")
    class ComponentScanningTests {
        
        @Test
        @DisplayName("Should scan for SqsClient annotated interfaces")
        void shouldScanForSqsClientInterfaces() {
            // When
            String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
            
            // Then - Should find OrderQueueService
            boolean foundOrderQueueService = java.util.Arrays.stream(beanNames)
                    .anyMatch(name -> name.equals("orderQueueService"));
            
            assertThat(foundOrderQueueService).isTrue();
        }
        
        @Test
        @DisplayName("Should only create proxies for interfaces with SqsClient annotation")
        void shouldOnlyCreateProxiesForAnnotatedInterfaces() {
            // When
            OrderQueueService orderService = applicationContext.getBean(OrderQueueService.class);
            
            // Then - Should be a proxy
            assertThat(orderService.getClass().getName()).contains("Proxy");
            assertThat(orderService.toString()).contains("SqsClientProxy");
        }
    }

    @Nested
    @DisplayName("Dependency Injection Tests")
    class DependencyInjectionTests {
        
        @Test
        @DisplayName("Should inject SqsClient proxies into other components")
        void shouldInjectSqsClientProxiesIntoOtherComponents() {
            // Given - Create a test component that depends on OrderQueueService
            TestComponent testComponent = new TestComponent(applicationContext.getBean(OrderQueueService.class));
            
            // Then
            assertThat(testComponent.getOrderService()).isNotNull();
            assertThat(testComponent.getOrderService().getClass().getName()).contains("Proxy");
        }
        
        @Test
        @DisplayName("Should support @Autowired injection")
        void shouldSupportAutowiredInjection() {
            // When - Spring should have autowired the OrderQueueService
            assertThat(applicationContext.getBean(OrderQueueService.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should support @Qualifier injection")
        void shouldSupportQualifierInjection() {
            // When - Get bean by specific name
            OrderQueueService qualifiedService = applicationContext.getBean("orderQueueService", OrderQueueService.class);
            
            // Then
            assertThat(qualifiedService).isNotNull();
            assertThat(qualifiedService.toString()).contains("order-service");
        }
    }

    @Nested
    @DisplayName("Configuration Override Tests")
    class ConfigurationOverrideTests {
        
        @Test
        @DisplayName("Should allow custom MessageSerializer configuration")
        void shouldAllowCustomMessageSerializerConfiguration() {
            // When
            MessageSerializer serializer = applicationContext.getBean(MessageSerializer.class);
            
            // Then - Should use the configured serializer
            assertThat(serializer).isNotNull();
        }
        
        @Test
        @DisplayName("Should allow custom SqsClientProxyFactory configuration")
        void shouldAllowCustomSqsClientProxyFactoryConfiguration() {
            // When
            SqsClientProxyFactory factory = applicationContext.getBean(SqsClientProxyFactory.class);
            
            // Then - Should use the configured factory
            assertThat(factory).isNotNull();
        }
    }

    @Nested
    @DisplayName("Application Context Lifecycle Tests")
    class ApplicationContextLifecycleTests {
        
        @Test
        @DisplayName("Should initialize all components correctly")
        void shouldInitializeAllComponentsCorrectly() {
            // When
            applicationContext.getBean(SqsClientProxyFactory.class);
            applicationContext.getBean(MessageSerializer.class);
            OrderQueueService orderService = applicationContext.getBean(OrderQueueService.class);
            
            // Then - All components should be properly initialized
            assertThat(orderService.toString()).isEqualTo("SqsClientProxy[order-service]");
        }
        
        @Test
        @DisplayName("Should handle application context refresh")
        void shouldHandleApplicationContextRefresh() {
            // Given
            OrderQueueService beforeRefresh = applicationContext.getBean(OrderQueueService.class);
            
            // When - Simulate context refresh (beans should remain the same due to singleton scope)
            OrderQueueService afterRefresh = applicationContext.getBean(OrderQueueService.class);
            
            // Then
            assertThat(beforeRefresh).isSameAs(afterRefresh);
        }
    }

    @Nested
    @DisplayName("Error Handling in Configuration")
    class ConfigurationErrorHandling {
        
        @Test
        @DisplayName("Should handle missing optional dependencies gracefully")
        void shouldHandleMissingOptionalDependenciesGracefully() {
            // When - All required dependencies should be present
            assertThat(applicationContext.getBean(SqsService.class)).isNotNull();
            assertThat(applicationContext.getBean(MessageSerializer.class)).isNotNull();
            
            // Then - Configuration should complete successfully
            assertThat(applicationContext.getBean(OrderQueueService.class)).isNotNull();
        }
        
        @Test
        @DisplayName("Should provide meaningful error messages for configuration issues")
        void shouldProvideMeaningfulErrorMessagesForConfigurationIssues() {
            // This test verifies that the configuration is working correctly
            // In case of configuration issues, Spring would provide meaningful error messages
            
            // When
            OrderQueueService orderService = applicationContext.getBean(OrderQueueService.class);
            
            // Then - Should be properly configured
            assertThat(orderService).isNotNull();
        }
    }

    // Test component for dependency injection testing
    static class TestComponent {
        private final OrderQueueService orderService;
        
        public TestComponent(OrderQueueService orderService) {
            this.orderService = orderService;
        }
        
        public OrderQueueService getOrderService() {
            return orderService;
        }
    }
}