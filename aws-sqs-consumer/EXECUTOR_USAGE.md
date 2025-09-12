# SQS Consumer Thread Pool Abstraction Usage Guide

The aws-sqs-consumer package now provides a flexible thread pool abstraction that supports platform threads, Java 21 virtual threads, and custom executor implementations.

## Quick Start

### Default Configuration (Platform Threads)
```yaml
aws:
  sqs:
    consumer:
      thread-pool-core-size: 10
      thread-pool-max-size: 50
      thread-pool-queue-capacity: 100
      thread-pool-keep-alive-seconds: 60
```

### Enable Virtual Threads (Java 21+)
```yaml
aws:
  sqs:
    consumer:
      executor:
        type: VIRTUAL_THREADS
```

### Auto-Select Virtual Threads When Available
```yaml
aws:
  sqs:
    consumer:
      executor:
        prefer-virtual-threads: true  # Will use virtual threads if Java 21+ is available
```

## Configuration Options

### Executor Types

#### Platform Threads (Default)
Uses traditional Java threads with configurable thread pool sizing.

```yaml
aws:
  sqs:
    consumer:
      executor:
        type: PLATFORM_THREADS
      thread-pool-core-size: 10      # Core pool size
      thread-pool-max-size: 50       # Maximum pool size  
      thread-pool-queue-capacity: 100 # Work queue capacity
      thread-pool-keep-alive-seconds: 60 # Thread keep-alive time
```

#### Virtual Threads (Java 21+)
Uses Java 21 virtual threads for highly scalable message processing.

```yaml
aws:
  sqs:
    consumer:
      executor:
        type: VIRTUAL_THREADS
```

**Benefits:**
- Handle millions of concurrent virtual threads
- Reduced memory footprint compared to platform threads
- Better resource utilization for I/O-bound operations
- Simplified thread management

**Requirements:**
- Java 21 or later
- Virtual threads feature available

#### Custom Executor
Inject your own `ExecutorServiceProvider` implementation.

```yaml
aws:
  sqs:
    consumer:
      executor:
        type: CUSTOM
        custom-provider-bean-name: myCustomExecutorProvider  # Optional: specify bean name
```

## Custom ExecutorServiceProvider Implementation

### Basic Custom Provider

```java
@Configuration
public class MyExecutorConfig {
    
    @Bean
    @Primary  // Make this the default provider
    public ExecutorServiceProvider myCustomExecutorProvider() {
        return CustomExecutorServiceProvider.builder()
            .executorFactory(consumerName -> 
                Executors.newVirtualThreadPerTaskExecutor())  // Java 21
            .withLifecycleManagement()
            .build();
    }
}
```

### Advanced Custom Provider with Spring TaskExecutor Integration

```java
@Configuration
public class SpringTaskExecutorProvider {
    
    @Bean
    public TaskExecutor messageProcessingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("sqs-msg-");
        executor.initialize();
        return executor;
    }
    
    @Bean
    public TaskExecutor pollingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setThreadNamePrefix("sqs-poll-");
        executor.initialize();
        return executor;
    }
    
    @Bean
    @Primary
    public ExecutorServiceProvider springTaskExecutorProvider(
            @Qualifier("messageProcessingTaskExecutor") TaskExecutor messageExecutor,
            @Qualifier("pollingTaskExecutor") TaskExecutor pollingExecutor) {
        
        return CustomExecutorServiceProvider.builder()
            .messageProcessingExecutorFactory(consumerName -> 
                new TaskExecutorAdapter(messageExecutor))
            .pollingExecutorFactory(consumerName -> 
                new TaskExecutorAdapter(pollingExecutor))
            .build(); // Don't manage lifecycle - Spring handles it
    }
}
```

### ForkJoinPool Custom Provider

```java
@Component
public class ForkJoinExecutorProvider implements ExecutorServiceProvider {
    
    private final ForkJoinPool messagePool;
    private final ForkJoinPool pollingPool;
    
    public ForkJoinExecutorProvider() {
        this.messagePool = new ForkJoinPool(
            Runtime.getRuntime().availableProcessors() * 2,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null, 
            true  // async mode for I/O-bound tasks
        );
        
        this.pollingPool = new ForkJoinPool(
            Math.min(4, Runtime.getRuntime().availableProcessors()),
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,
            false // sync mode for polling
        );
    }
    
    @Override
    public ExecutorService createMessageProcessingExecutor(String consumerName) {
        return messagePool;
    }
    
    @Override
    public ExecutorService createPollingExecutor(String consumerName) {
        return pollingPool;
    }
    
    @Override
    public ThreadingModel getThreadingModel() {
        return ThreadingModel.CUSTOM;
    }
    
    @Override
    public void shutdown(long timeoutMillis) {
        messagePool.shutdown();
        pollingPool.shutdown();
        
        try {
            if (!messagePool.awaitTermination(timeoutMillis / 2, TimeUnit.MILLISECONDS)) {
                messagePool.shutdownNow();
            }
            if (!pollingPool.awaitTermination(timeoutMillis / 2, TimeUnit.MILLISECONDS)) {
                pollingPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            messagePool.shutdownNow();
            pollingPool.shutdownNow();
        }
    }
}
```

## Monitoring and Observability

### Enable Executor Monitoring

```yaml
aws:
  sqs:
    consumer:
      executor:
        enable-monitoring: true
```

### Custom Monitoring with Micrometer

```java
@Configuration
public class SqsExecutorMonitoring {
    
    @Bean
    public ExecutorServiceProvider monitoredExecutorProvider(MeterRegistry meterRegistry) {
        return new PlatformThreadExecutorServiceProvider(properties) {
            @Override
            public ExecutorService createMessageProcessingExecutor(String consumerName) {
                ExecutorService executor = super.createMessageProcessingExecutor(consumerName);
                return MoreExecutors.listeningDecorator(
                    TimedExecutorService.wrap(executor, meterRegistry, "sqs.message.processing"));
            }
        };
    }
}
```

## Migration Guide

### From Fixed Thread Pool to Flexible Abstraction

**Before:**
```java
// Hard-coded thread pool in application
ExecutorService executor = Executors.newFixedThreadPool(50);
```

**After:**
```yaml
# Configuration-driven approach
aws:
  sqs:
    consumer:
      executor:
        prefer-virtual-threads: true
      thread-pool-max-size: 50
```

### Upgrading to Virtual Threads

1. **Check Java Version:**
   ```bash
   java -version  # Ensure Java 21+
   ```

2. **Update Configuration:**
   ```yaml
   aws:
     sqs:
       consumer:
         executor:
           type: VIRTUAL_THREADS
   ```

3. **Test Performance:**
   - Monitor memory usage (should decrease)
   - Check throughput (should increase for I/O-bound workloads)
   - Validate error handling remains correct

## Performance Characteristics

### Platform Threads
- **Best for:** CPU-intensive processing
- **Thread limit:** ~4-8k threads (OS dependent)
- **Memory per thread:** ~1MB stack space
- **Context switching:** Higher overhead

### Virtual Threads
- **Best for:** I/O-intensive operations (typical SQS workloads)
- **Thread limit:** Millions of threads
- **Memory per thread:** ~KB in heap
- **Context switching:** Very low overhead
- **Blocking operations:** Automatically yield CPU

### Custom Executors
- **ForkJoinPool:** Work-stealing for CPU-intensive tasks
- **Spring TaskExecutor:** Integration with existing Spring scheduling
- **Custom Implementations:** Domain-specific optimizations

## Troubleshooting

### Virtual Threads Not Available

**Error:** `UnsupportedOperationException: Virtual Threads are not supported`

**Solution:**
1. Verify Java 21+ is being used
2. Check if virtual threads are enabled in your JVM
3. Falls back to platform threads automatically when `prefer-virtual-threads: true`

### Thread Pool Exhaustion

**Symptoms:** RejectedExecutionException, slow processing

**Solutions:**
```yaml
aws:
  sqs:
    consumer:
      thread-pool-max-size: 100        # Increase max threads
      thread-pool-queue-capacity: 500  # Increase queue size
```

Or switch to virtual threads:
```yaml
aws:
  sqs:
    consumer:
      executor:
        type: VIRTUAL_THREADS  # No thread pool limits
```

### Memory Issues

**Platform Threads:**
- Reduce `thread-pool-max-size`
- Increase heap size (`-Xmx`)

**Virtual Threads:**
- Monitor heap usage (virtual threads use heap, not stack)
- Virtual threads should reduce overall memory usage

## Best Practices

### 1. Choose the Right Model
- **I/O-heavy workloads (database, HTTP calls, file operations):** Virtual threads
- **CPU-intensive processing:** Platform threads or ForkJoinPool
- **Mixed workloads:** Virtual threads (handles both well)

### 2. Configuration Guidelines
- Start with virtual threads if on Java 21+
- Use `prefer-virtual-threads: true` for forward compatibility
- Monitor actual resource usage vs. theoretical limits

### 3. Testing Strategy
- Load test with realistic message volumes
- Test failure scenarios and error handling
- Monitor thread dumps during issues
- Validate graceful shutdown behavior

### 4. Production Deployment
- Deploy virtual threads to staging first
- Monitor memory patterns for first few days
- Keep platform thread configuration as fallback
- Use feature flags for quick rollback if needed

## Example Application Configuration

```yaml
# application.yml
aws:
  region: us-west-2
  sqs:
    consumer:
      enabled: true
      executor:
        type: VIRTUAL_THREADS
        enable-monitoring: true
      
      # Fallback platform thread settings
      thread-pool-core-size: 20
      thread-pool-max-size: 100
      thread-pool-queue-capacity: 200
      thread-pool-keep-alive-seconds: 60
      shutdown-timeout-millis: 30000

logging:
  level:
    com.ryuqq.aws.sqs.consumer.executor: DEBUG  # Enable executor logging

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,threaddump
  endpoint:
    threaddump:
      enabled: true
```

This setup provides maximum flexibility while maintaining production-ready defaults and comprehensive monitoring capabilities.