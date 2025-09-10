package com.ryuqq.aws.sqs.config;

import com.ryuqq.aws.sqs.annotation.SqsClient;
import com.ryuqq.aws.sqs.proxy.SqsClientProxyFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Registers SQS client interfaces as Spring beans by scanning for @SqsClient annotations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqsClientRegistrar implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
    private final MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        try {
            Set<Class<?>> sqsClientInterfaces = findSqsClientInterfaces();
            registerSqsClientBeans(registry, sqsClientInterfaces);
        } catch (Exception e) {
            log.error("Failed to register SQS client beans", e);
            throw new RuntimeException("Failed to register SQS client beans", e);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No additional processing needed
    }

    private Set<Class<?>> findSqsClientInterfaces() throws IOException, ClassNotFoundException {
        Set<Class<?>> sqsClientInterfaces = new HashSet<>();
        
        // Scan for interfaces in common package patterns
        String[] basePackages = determineBasePackages();
        
        for (String basePackage : basePackages) {
            String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                    ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";
            
            Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);
            
            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);
                    AnnotationMetadata annotationMetadata = metadataReader.getAnnotationMetadata();
                    
                    if (annotationMetadata.hasAnnotation(SqsClient.class.getName()) && 
                        annotationMetadata.isInterface()) {
                        
                        String className = metadataReader.getClassMetadata().getClassName();
                        Class<?> clazz = ClassUtils.forName(className, applicationContext.getClassLoader());
                        sqsClientInterfaces.add(clazz);
                        log.info("Found SQS client interface: {}", className);
                    }
                }
            }
        }
        
        return sqsClientInterfaces;
    }

    private String[] determineBasePackages() {
        // Get the main application package from Spring Boot
        String mainPackage = determineMainApplicationPackage();
        
        if (mainPackage != null) {
            return new String[]{mainPackage};
        }
        
        // Fallback to common package patterns
        return new String[]{
                "com.ryuqq",
                "com.example",
                "org.springframework"
        };
    }

    private String determineMainApplicationPackage() {
        try {
            // Try to get the main application class package
            String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
            for (String beanName : beanNames) {
                Object bean = applicationContext.getBean(beanName);
                Class<?> beanClass = bean.getClass();
                
                if (beanClass.getName().contains("Application") || 
                    beanClass.getSimpleName().endsWith("Application")) {
                    return beanClass.getPackage().getName();
                }
            }
        } catch (Exception e) {
            log.debug("Could not determine main application package", e);
        }
        
        return null;
    }

    private void registerSqsClientBeans(BeanDefinitionRegistry registry, Set<Class<?>> sqsClientInterfaces) {
        for (Class<?> clientInterface : sqsClientInterfaces) {
            String beanName = generateBeanName(clientInterface);
            
            GenericBeanDefinition beanDefinition = new GenericBeanDefinition();
            beanDefinition.setBeanClass(SqsClientFactoryBean.class);
            beanDefinition.getConstructorArgumentValues().addGenericArgumentValue(clientInterface);
            beanDefinition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);
            
            registry.registerBeanDefinition(beanName, beanDefinition);
            log.info("Registered SQS client bean: {} for interface: {}", beanName, clientInterface.getName());
        }
    }

    private String generateBeanName(Class<?> clientInterface) {
        String className = clientInterface.getSimpleName();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }
}