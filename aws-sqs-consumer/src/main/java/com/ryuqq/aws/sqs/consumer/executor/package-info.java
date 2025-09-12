/**
 * Thread pool abstraction and executor service providers for SQS consumers.
 * 
 * <p>This package provides a flexible abstraction for managing thread pools used by 
 * SQS listener containers, supporting:</p>
 * 
 * <ul>
 *   <li>{@link com.ryuqq.aws.sqs.consumer.executor.PlatformThreadExecutorServiceProvider} - 
 *       Traditional platform threads with configurable pool sizing</li>
 *   <li>{@link com.ryuqq.aws.sqs.consumer.executor.VirtualThreadExecutorServiceProvider} - 
 *       Java 21 virtual threads for highly scalable I/O-bound operations</li>
 *   <li>{@link com.ryuqq.aws.sqs.consumer.executor.CustomExecutorServiceProvider} - 
 *       Custom user-provided executor implementations</li>
 * </ul>
 * 
 * <p>The abstraction allows users to:</p>
 * <ul>
 *   <li>Choose between platform threads and virtual threads</li>
 *   <li>Inject custom {@link java.util.concurrent.ExecutorService} implementations</li>
 *   <li>Configure thread pool parameters through Spring Boot properties</li>
 *   <li>Manage executor lifecycle automatically or manually</li>
 * </ul>
 * 
 * <h2>Configuration Examples</h2>
 * 
 * <h3>Platform Threads (Default)</h3>
 * <pre>{@code
 * aws:
 *   sqs:
 *     consumer:
 *       executor:
 *         type: PLATFORM_THREADS
 *       thread-pool-core-size: 10
 *       thread-pool-max-size: 50
 * }</pre>
 * 
 * <h3>Virtual Threads (Java 21+)</h3>
 * <pre>{@code
 * aws:
 *   sqs:
 *     consumer:
 *       executor:
 *         type: VIRTUAL_THREADS
 * }</pre>
 * 
 * <h3>Custom Executor</h3>
 * <pre>{@code
 * @Bean
 * public ExecutorServiceProvider myCustomProvider() {
 *     return CustomExecutorServiceProvider.builder()
 *         .executorFactory(name -> Executors.newCachedThreadPool())
 *         .build();
 * }
 * }</pre>
 * 
 * @since 1.0.0
 * @see com.ryuqq.aws.sqs.consumer.executor.ExecutorServiceProvider
 * @see com.ryuqq.aws.sqs.consumer.properties.SqsConsumerProperties.Executor
 */
package com.ryuqq.aws.sqs.consumer.executor;