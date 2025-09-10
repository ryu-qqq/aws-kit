package com.ryuqq.aws.sqs.proxy;

import com.ryuqq.aws.sqs.annotation.SqsClient;
import com.ryuqq.aws.sqs.serialization.MessageSerializer;
import com.ryuqq.aws.sqs.service.SqsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating SQS client proxies.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsClientProxyFactory {

    private final SqsService sqsService;
    private final MessageSerializer messageSerializer;
    private final Map<Class<?>, Object> proxyCache = new ConcurrentHashMap<>();

    /**
     * Create a proxy for the given SQS client interface.
     *
     * @param clientInterface the client interface annotated with @SqsClient
     * @param <T> the interface type
     * @return the proxy instance
     */
    @SuppressWarnings("unchecked")
    public <T> T createProxy(Class<T> clientInterface) {
        return (T) proxyCache.computeIfAbsent(clientInterface, this::doCreateProxy);
    }

    private Object doCreateProxy(Class<?> clientInterface) {
        if (!clientInterface.isInterface()) {
            throw new IllegalArgumentException("SqsClient can only be applied to interfaces");
        }

        SqsClient sqsClientAnnotation = clientInterface.getAnnotation(SqsClient.class);
        if (sqsClientAnnotation == null) {
            throw new IllegalArgumentException("Interface must be annotated with @SqsClient");
        }

        log.info("Creating SQS client proxy for interface: {}", clientInterface.getName());

        SqsClientInvocationHandler handler = new SqsClientInvocationHandler(
                sqsService, 
                messageSerializer, 
                sqsClientAnnotation
        );

        return Proxy.newProxyInstance(
                clientInterface.getClassLoader(),
                new Class<?>[]{clientInterface},
                handler
        );
    }

    /**
     * Clear the proxy cache.
     */
    public void clearCache() {
        proxyCache.clear();
    }
}