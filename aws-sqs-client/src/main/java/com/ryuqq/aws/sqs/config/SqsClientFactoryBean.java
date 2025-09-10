package com.ryuqq.aws.sqs.config;

import com.ryuqq.aws.sqs.proxy.SqsClientProxyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FactoryBean for creating SQS client proxy instances.
 */
@Slf4j
@RequiredArgsConstructor
public class SqsClientFactoryBean<T> implements FactoryBean<T> {

    private final Class<T> clientInterface;
    
    @Autowired
    private SqsClientProxyFactory proxyFactory;

    @Override
    public T getObject() throws Exception {
        log.debug("Creating SQS client proxy for interface: {}", clientInterface.getName());
        return proxyFactory.createProxy(clientInterface);
    }

    @Override
    public Class<?> getObjectType() {
        return clientInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}