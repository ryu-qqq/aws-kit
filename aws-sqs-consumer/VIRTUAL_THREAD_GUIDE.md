# Virtual Thread Support Guide for AWS SQS Consumer

## Overview

AWS SQS Consumer now provides full support for Java 21 Virtual Threads, allowing you to handle thousands of concurrent messages with minimal resource overhead.

## Configuration Options

### 1. Auto-Detection (Recommended)

```yaml
# application.yml
aws:
  sqs:
    consumer:
      executor:
        prefer-virtual-threads: true  # Auto-use virtual threads on Java 21+
```

### 2. Explicit Virtual Thread Configuration

```yaml
aws:
  sqs:
    consumer:
      executor:
        type: VIRTUAL_THREADS  # Force virtual threads (fails on Java < 21)
```

### 3. Custom Virtual Thread Executor

```java
@Configuration
public class VirtualThreadConfig {
    
    @Bean
    @ConditionalOnJava(JavaVersion.TWENTY_ONE)
    public ExecutorServiceProvider virtualThreadProvider() {
        return CustomExecutorServiceProvider.builder()
            .executorFactory(name -> {
                ThreadFactory factory = Thread.ofVirtual()
                    .name("sqs-virtual-", 0)
                    .factory();
                return Executors.newThreadPerTaskExecutor(factory);
            })
            .withLifecycleManagement()
            .build();
    }
}
```

## Examples

### Example 1: High-Throughput Consumer with Virtual Threads

```java
@Configuration
@EnableSqs
public class HighThroughputConsumerConfig {
    
    @Bean
    public ExecutorServiceProvider executorServiceProvider() {
        // Virtual threads for unlimited concurrency
        return new VirtualThreadExecutorServiceProvider();
    }
    
    @Component
    public static class OrderProcessor {
        
        @SqsListener(
            value = "high-volume-orders",
            maxConcurrentMessages = 1000,  // Virtual threads handle this easily
            batchSize = 25
        )
        public void processOrders(List<SqsMessage> orders) {
            // Process orders in parallel with virtual threads
            orders.parallelStream().forEach(this::processOrder);
        }
        
        private void processOrder(SqsMessage order) {
            // I/O-bound operations work great with virtual threads
            callExternalApi(order);
            saveToDatabase(order);
            sendNotification(order);
        }
    }
}
```

### Example 2: Mixed Thread Pool Strategy

```java
@Configuration
public class MixedThreadPoolConfig {
    
    @Bean
    @Primary
    public ExecutorServiceProvider hybridProvider() {
        return new ExecutorServiceProvider() {
            @Override
            public ExecutorService createMessageProcessingExecutor(String containerName) {
                // Virtual threads for I/O-bound message processing
                if (isJava21Available()) {
                    return Executors.newVirtualThreadPerTaskExecutor();
                }
                // Fallback to platform threads
                return Executors.newFixedThreadPool(50);
            }
            
            @Override
            public ScheduledExecutorService createPollingExecutor(String containerName) {
                // Platform threads for polling (scheduled tasks)
                return Executors.newScheduledThreadPool(2);
            }
        };
    }
}
```

### Example 3: ECS Task with Virtual Threads

```dockerfile
# Dockerfile
FROM openjdk:21-slim
COPY target/sqs-consumer.jar app.jar

# Enable virtual threads
ENV JAVA_OPTS="-XX:+UseVirtualThreads"

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```yaml
# application-ecs.yml
spring:
  profiles: ecs

aws:
  sqs:
    consumer:
      executor:
        type: VIRTUAL_THREADS
      containers:
        - name: order-processor
          queue-url: ${ORDER_QUEUE_URL}
          max-concurrent-messages: 5000  # Virtual threads handle this
        - name: notification-sender
          queue-url: ${NOTIFICATION_QUEUE_URL}
          max-concurrent-messages: 2000
```

### Example 4: Performance Comparison

```java
@Component
@Slf4j
public class PerformanceBenchmark {
    
    @EventListener(ApplicationReadyEvent.class)
    public void benchmark() {
        // Platform threads: ~200 messages/sec with 50 threads
        // Virtual threads: ~2000 messages/sec with unlimited threads
        
        log.info("Thread type: {}", 
            Thread.currentThread().isVirtual() ? "Virtual" : "Platform");
    }
    
    @SqsListener("benchmark-queue")
    public void processBenchmark(SqsMessage message) {
        // Simulate I/O operation
        try {
            Thread.sleep(100);  // Virtual threads don't block OS threads
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
```

## Migration Guide

### From Platform Threads to Virtual Threads

1. **Update Java Version**
   ```xml
   <java.version>21</java.version>
   ```

2. **Update Configuration**
   ```yaml
   # Before (Platform Threads)
   aws.sqs.consumer.thread-pool-max-size: 50
   
   # After (Virtual Threads)
   aws.sqs.consumer.executor.prefer-virtual-threads: true
   ```

3. **Adjust Concurrency Limits**
   ```java
   // Before: Limited by thread pool size
   @SqsListener(value = "queue", maxConcurrentMessages = 50)
   
   // After: Can handle much higher concurrency
   @SqsListener(value = "queue", maxConcurrentMessages = 1000)
   ```

## Performance Considerations

### When Virtual Threads Excel

✅ **Best for:**
- I/O-bound operations (API calls, database queries)
- High concurrency requirements (>100 concurrent messages)
- Variable processing times
- Microservices with many blocking calls

### When to Use Platform Threads

⚠️ **Consider platform threads for:**
- CPU-intensive processing
- Predictable, low-latency requirements
- Legacy code with ThreadLocal usage
- Scheduled/periodic tasks

## Monitoring

### Virtual Thread Metrics

```java
@Component
public class VirtualThreadMetrics {
    
    @Autowired
    private MeterRegistry meterRegistry;
    
    @EventListener
    public void onMessageProcessed(MessageProcessedEvent event) {
        meterRegistry.counter("sqs.messages.processed",
            "thread.type", Thread.currentThread().isVirtual() ? "virtual" : "platform",
            "queue", event.getQueueName()
        ).increment();
    }
}
```

### JVM Flags for Monitoring

```bash
# Enable virtual thread monitoring
-XX:+UseVirtualThreads
-Djdk.virtualThreadScheduler.parallelism=1024
-Djdk.virtualThreadScheduler.maxPoolSize=2048

# JFR for virtual thread profiling
-XX:StartFlightRecording=filename=virtual-threads.jfr,settings=profile
```

## Troubleshooting

### Common Issues

1. **ClassNotFoundException on Java < 21**
   ```yaml
   # Use auto-detection instead of explicit type
   aws.sqs.consumer.executor.prefer-virtual-threads: true
   ```

2. **ThreadLocal Issues**
   - Virtual threads don't work well with ThreadLocal
   - Use ScopedValue (Java 21+) or pass context explicitly

3. **Synchronized Blocks**
   - Replace with ReentrantLock for better virtual thread performance
   ```java
   // Before
   synchronized(lock) { ... }
   
   // After
   reentrantLock.lock();
   try { ... } finally { reentrantLock.unlock(); }
   ```

## Production Checklist

- [ ] Java 21+ installed
- [ ] Virtual thread monitoring enabled
- [ ] ThreadLocal usage reviewed
- [ ] Synchronized blocks replaced with locks
- [ ] Concurrency limits adjusted for virtual threads
- [ ] Fallback strategy for Java < 21 environments
- [ ] JFR profiling configured
- [ ] Load testing completed with virtual threads

## References

- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Virtual Threads Best Practices](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)
- [AWS SQS Consumer Documentation](../README.md)