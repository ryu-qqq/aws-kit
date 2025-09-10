package com.ryuqq.aws.sqs.proxy;

import com.ryuqq.aws.sqs.annotation.*;
import com.ryuqq.aws.sqs.model.SqsMessage;
import com.ryuqq.aws.sqs.serialization.MessageSerializer;
import com.ryuqq.aws.sqs.service.SqsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SqsClientProxyFactory.
 * Tests proxy creation, caching, and interface validation.
 */
@ExtendWith(MockitoExtension.class)
class SqsClientProxyFactoryTest {

    @Mock
    private SqsService sqsService;
    
    @Mock
    private MessageSerializer messageSerializer;
    
    private SqsClientProxyFactory proxyFactory;
    
    @BeforeEach
    void setUp() {
        proxyFactory = new SqsClientProxyFactory(sqsService, messageSerializer);
    }

    @Nested
    @DisplayName("Proxy Creation")
    class ProxyCreation {
        
        @Test
        @DisplayName("Should create proxy for valid SQS client interface")
        void shouldCreateProxyForValidInterface() {
            // When
            ValidSqsClient proxy = proxyFactory.createProxy(ValidSqsClient.class);
            
            // Then
            assertThat(proxy).isNotNull();
            assertThat(proxy.getClass()).isNotEqualTo(ValidSqsClient.class);
            assertThat(proxy.getClass().getName()).contains("Proxy");
        }
        
        @Test
        @DisplayName("Should create proxy with correct toString")
        void shouldCreateProxyWithCorrectToString() {
            // When
            ValidSqsClient proxy = proxyFactory.createProxy(ValidSqsClient.class);
            
            // Then
            assertThat(proxy.toString()).isEqualTo("SqsClientProxy[valid-client]");
        }
        
        @Test
        @DisplayName("Should create proxy with working hashCode")
        void shouldCreateProxyWithWorkingHashCode() {
            // When
            ValidSqsClient proxy = proxyFactory.createProxy(ValidSqsClient.class);
            
            // Then
            assertThat(proxy.hashCode()).isNotZero();
        }
        
        @Test
        @DisplayName("Should create proxy with working equals")
        void shouldCreateProxyWithWorkingEquals() {
            // When
            ValidSqsClient proxy1 = proxyFactory.createProxy(ValidSqsClient.class);
            ValidSqsClient proxy2 = proxyFactory.createProxy(ValidSqsClient.class);
            
            // Then
            assertThat(proxy1).isEqualTo(proxy2);
        }
    }

    @Nested
    @DisplayName("Proxy Caching")
    class ProxyCaching {
        
        @Test
        @DisplayName("Should cache and reuse proxy instances")
        void shouldCacheProxyInstances() {
            // When
            ValidSqsClient proxy1 = proxyFactory.createProxy(ValidSqsClient.class);
            ValidSqsClient proxy2 = proxyFactory.createProxy(ValidSqsClient.class);
            
            // Then
            assertThat(proxy1).isSameAs(proxy2);
        }
        
        @Test
        @DisplayName("Should create different proxies for different interfaces")
        void shouldCreateDifferentProxiesForDifferentInterfaces() {
            // When
            ValidSqsClient proxy1 = proxyFactory.createProxy(ValidSqsClient.class);
            AnotherValidSqsClient proxy2 = proxyFactory.createProxy(AnotherValidSqsClient.class);
            
            // Then
            assertThat(proxy1).isNotSameAs(proxy2);
            assertThat(proxy1.getClass()).isNotEqualTo(proxy2.getClass());
        }
        
        @Test
        @DisplayName("Should maintain cache across multiple calls")
        void shouldMaintainCacheAcrossMultipleCalls() {
            // When
            ValidSqsClient proxy1 = proxyFactory.createProxy(ValidSqsClient.class);
            ValidSqsClient proxy2 = proxyFactory.createProxy(ValidSqsClient.class);
            ValidSqsClient proxy3 = proxyFactory.createProxy(ValidSqsClient.class);
            
            // Then
            assertThat(proxy1).isSameAs(proxy2).isSameAs(proxy3);
        }
    }

    @Nested
    @DisplayName("Interface Validation")
    class InterfaceValidation {
        
        @Test
        @DisplayName("Should fail for interface without @SqsClient annotation")
        void shouldFailForInterfaceWithoutAnnotation() {
            // When & Then
            assertThatThrownBy(() -> proxyFactory.createProxy(InterfaceWithoutAnnotation.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be annotated with @SqsClient");
        }
        
        @Test
        @DisplayName("Should fail for non-interface classes")
        void shouldFailForNonInterfaceClasses() {
            // When & Then
            assertThatThrownBy(() -> proxyFactory.createProxy(ConcreteClass.class))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must be an interface");
        }
        
        @Test
        @DisplayName("Should fail for null interface")
        void shouldFailForNullInterface() {
            // When & Then
            assertThatThrownBy(() -> proxyFactory.createProxy(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Interface class cannot be null");
        }
    }

    @Nested
    @DisplayName("Proxy Method Validation")
    class ProxyMethodValidation {
        
        @Test
        @DisplayName("Should validate that proxy implements all interface methods")
        void shouldValidateProxyImplementsAllMethods() {
            // Given
            ValidSqsClient proxy = proxyFactory.createProxy(ValidSqsClient.class);
            
            // When & Then - Should not throw exceptions when calling interface methods
            assertThat(proxy.toString()).isNotNull();
            assertThat(proxy.hashCode()).isNotZero();
            assertThat(proxy.equals(proxy)).isTrue();
        }
        
        @Test
        @DisplayName("Should handle interfaces with default methods")
        void shouldHandleInterfacesWithDefaultMethods() {
            // When
            InterfaceWithDefaultMethod proxy = proxyFactory.createProxy(InterfaceWithDefaultMethod.class);
            
            // Then
            assertThat(proxy).isNotNull();
            // Default methods should work as they're not proxied
            assertThat(proxy.defaultMethod()).isEqualTo("default implementation");
        }
    }

    @Nested
    @DisplayName("Complex Proxy Scenarios")
    class ComplexProxyScenarios {
        
        @Test
        @DisplayName("Should handle interface with multiple annotation types")
        void shouldHandleInterfaceWithMultipleAnnotations() {
            // When
            ComplexSqsClient proxy = proxyFactory.createProxy(ComplexSqsClient.class);
            
            // Then
            assertThat(proxy).isNotNull();
            assertThat(proxy.toString()).isEqualTo("SqsClientProxy[complex-client]");
        }
        
        @Test
        @DisplayName("Should handle interface extending other interfaces")
        void shouldHandleExtendingInterfaces() {
            // When
            ExtendingSqsClient proxy = proxyFactory.createProxy(ExtendingSqsClient.class);
            
            // Then
            assertThat(proxy).isNotNull();
            assertThat(proxy.toString()).isEqualTo("SqsClientProxy[extending-client]");
        }
        
        @Test
        @DisplayName("Should handle interface with generic methods")
        void shouldHandleGenericMethods() {
            // When
            GenericSqsClient proxy = proxyFactory.createProxy(GenericSqsClient.class);
            
            // Then
            assertThat(proxy).isNotNull();
            assertThat(proxy.toString()).isEqualTo("SqsClientProxy[generic-client]");
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {
        
        @Test
        @DisplayName("Should be thread-safe when creating proxies concurrently")
        void shouldBeThreadSafeForConcurrentProxyCreation() throws InterruptedException {
            // Given
            int threadCount = 10;
            Thread[] threads = new Thread[threadCount];
            ValidSqsClient[] proxies = new ValidSqsClient[threadCount];
            
            // When
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    proxies[index] = proxyFactory.createProxy(ValidSqsClient.class);
                });
                threads[i].start();
            }
            
            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }
            
            // Then
            for (int i = 0; i < threadCount; i++) {
                assertThat(proxies[i]).isNotNull();
                if (i > 0) {
                    assertThat(proxies[i]).isSameAs(proxies[0]);
                }
            }
        }
    }

    // Test interfaces and classes
    @SqsClient("valid-client")
    interface ValidSqsClient {
        @SendMessage("test")
        CompletableFuture<String> sendMessage(@MessageBody String message);
    }
    
    @SqsClient("another-valid-client")
    interface AnotherValidSqsClient {
        @SendMessage("test")
        CompletableFuture<String> sendMessage(@MessageBody String message);
    }
    
    // Interface without @SqsClient annotation
    interface InterfaceWithoutAnnotation {
        void someMethod();
    }
    
    // Non-interface class
    @SqsClient("concrete")
    static class ConcreteClass {
        public void someMethod() {}
    }
    
    @SqsClient("default-method-client")
    interface InterfaceWithDefaultMethod {
        @SendMessage("test")
        CompletableFuture<String> sendMessage(@MessageBody String message);
        
        default String defaultMethod() {
            return "default implementation";
        }
    }
    
    @SqsClient("complex-client")
    interface ComplexSqsClient {
        @SendMessage("simple")
        CompletableFuture<String> sendMessage(@MessageBody String message);
        
        @SendBatch("batch")
        CompletableFuture<java.util.List<String>> sendBatch(@MessageBody java.util.List<String> messages);
        
        @ReceiveMessages("receive")
        CompletableFuture<Void> receiveMessages(@MessageProcessor Consumer<SqsMessage> processor);
        
        @StartPolling("polling")
        void startPolling(@MessageProcessor Consumer<SqsMessage> processor);
    }
    
    @SqsClient("extending-client")
    interface ExtendingSqsClient extends ValidSqsClient {
        @SendMessage("additional")
        CompletableFuture<String> sendAdditionalMessage(@MessageBody String message);
    }
    
    @SqsClient("generic-client")
    interface GenericSqsClient {
        @SendMessage("generic")
        <T> CompletableFuture<String> sendGenericMessage(@MessageBody T message);
        
        @ReceiveMessages("receive")
        CompletableFuture<Void> receiveMessages(@MessageProcessor Consumer<SqsMessage> processor);
    }
}