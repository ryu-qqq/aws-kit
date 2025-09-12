package com.ryuqq.aws.sns.performance;

import com.ryuqq.aws.sns.adapter.SnsTypeAdapter;
import com.ryuqq.aws.sns.service.SnsService;
import com.ryuqq.aws.sns.types.SnsMessage;
import com.ryuqq.aws.sns.types.SnsPublishResult;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JMH Benchmark for SNS Service Performance
 * Provides detailed performance profiling with warmup and measurement iterations
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx4g"})
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Threads(4)
public class SnsBenchmark {

    private SnsService snsService;
    private SnsAsyncClient snsClient;
    private SnsTypeAdapter typeAdapter;
    
    private static final String TEST_TOPIC_ARN = "arn:aws:sns:us-east-1:123456789012:benchmark-topic";
    private List<SnsMessage> testMessages;
    private List<List<SnsMessage>> batchMessages;
    
    @Setup(Level.Trial)
    public void setupTrial() {
        // Setup mocks
        snsClient = mock(SnsAsyncClient.class);
        typeAdapter = mock(SnsTypeAdapter.class);
        snsService = new SnsService(snsClient, typeAdapter);
        
        setupMockResponses();
        prepareTestData();
    }
    
    @Setup(Level.Iteration)
    public void setupIteration() {
        // Any per-iteration setup
        System.gc(); // Hint for garbage collection before each iteration
    }
    
    /**
     * Benchmark single message publishing
     */
    @Benchmark
    @Group("singlePublish")
    public void benchmarkSingleMessagePublish(Blackhole bh) throws Exception {
        SnsMessage message = getRandomTestMessage();
        CompletableFuture<SnsPublishResult> future = snsService.publish(TEST_TOPIC_ARN, message);
        SnsPublishResult result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark batch message publishing
     */
    @Benchmark
    @Group("batchPublish")
    public void benchmarkBatchMessagePublish(Blackhole bh) throws Exception {
        List<SnsMessage> messages = getRandomBatchMessages();
        CompletableFuture<List<SnsPublishResult>> future = snsService.publishBatch(TEST_TOPIC_ARN, messages);
        List<SnsPublishResult> results = future.get();
        bh.consume(results);
    }
    
    /**
     * Benchmark SMS publishing
     */
    @Benchmark
    @Group("smsPublish")
    public void benchmarkSmsPublish(Blackhole bh) throws Exception {
        String phoneNumber = "+1234567890" + ThreadLocalRandom.current().nextInt(10);
        String message = "Benchmark SMS message " + System.nanoTime();
        CompletableFuture<SnsPublishResult> future = snsService.publishSms(phoneNumber, message);
        SnsPublishResult result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark concurrent message publishing
     */
    @Benchmark
    @Group("concurrentPublish")
    @GroupThreads(4)
    public void benchmarkConcurrentPublish(Blackhole bh) throws Exception {
        SnsMessage message = getRandomTestMessage();
        CompletableFuture<SnsPublishResult> future = snsService.publish(TEST_TOPIC_ARN, message);
        SnsPublishResult result = future.get();
        bh.consume(result);
    }
    
    /**
     * Benchmark message creation and serialization overhead
     */
    @Benchmark
    @Group("messageCreation")
    public void benchmarkMessageCreation(Blackhole bh) {
        SnsMessage message = SnsMessage.builder()
            .body("Benchmark message body " + System.nanoTime())
            .subject("Benchmark Subject")
            .attribute("BenchmarkId", UUID.randomUUID().toString())
            .attribute("Timestamp", Instant.now().toString())
            .attribute("ThreadId", String.valueOf(Thread.currentThread().getId()))
            .build();
        bh.consume(message);
    }
    
    /**
     * Benchmark type adapter conversion
     */
    @Benchmark
    @Group("typeAdapter")
    public void benchmarkTypeAdapterConversion(Blackhole bh) {
        SnsMessage message = getRandomTestMessage();
        PublishRequest request = typeAdapter.toPublishRequest(TEST_TOPIC_ARN, message);
        bh.consume(request);
    }
    
    /**
     * Benchmark error handling path
     */
    @Benchmark
    @Group("errorHandling")
    public void benchmarkErrorHandling(Blackhole bh) {
        try {
            // Create a message that will trigger an error
            SnsMessage errorMessage = SnsMessage.builder()
                .body("error-trigger-message")
                .build();
            
            CompletableFuture<SnsPublishResult> future = snsService.publish(TEST_TOPIC_ARN, errorMessage);
            SnsPublishResult result = future.get();
            bh.consume(result);
        } catch (Exception e) {
            bh.consume(e);
        }
    }
    
    // Setup methods
    
    private void setupMockResponses() {
        // Mock successful publish response with variable latency
        when(snsClient.publish(any(PublishRequest.class)))
            .thenAnswer(invocation -> {
                PublishRequest request = invocation.getArgument(0);
                
                // Simulate variable response times (10-100ms)
                int delay = ThreadLocalRandom.current().nextInt(10, 100);
                
                // Check for error trigger
                if (request.message() != null && request.message().contains("error-trigger")) {
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(delay);
                            throw new RuntimeException("Simulated SNS error");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                    });
                }
                
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(delay);
                        return PublishResponse.builder()
                            .messageId(UUID.randomUUID().toString())
                            .build();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            });
        
        // Mock batch publish response
        when(snsClient.publishBatch(any(PublishBatchRequest.class)))
            .thenAnswer(invocation -> {
                int delay = ThreadLocalRandom.current().nextInt(50, 200); // Batch operations take longer
                
                return CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(delay);
                        
                        List<PublishBatchResultEntry> successful = new ArrayList<>();
                        for (int i = 0; i < 10; i++) { // Assume 10 messages in batch
                            successful.add(PublishBatchResultEntry.builder()
                                .id(String.valueOf(i))
                                .messageId(UUID.randomUUID().toString())
                                .build());
                        }
                        
                        return PublishBatchResponse.builder()
                            .successful(successful)
                            .build();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });
            });
        
        // Mock type adapter responses
        when(typeAdapter.toPublishRequest(any(), any()))
            .thenAnswer(invocation -> {
                String topicArn = invocation.getArgument(0);
                SnsMessage message = invocation.getArgument(1);
                
                return PublishRequest.builder()
                    .topicArn(topicArn)
                    .message(message.getBody())
                    .subject(message.getSubject())
                    .build();
            });
        
        when(typeAdapter.toPublishBatchRequest(any(), any()))
            .thenAnswer(invocation -> {
                String topicArn = invocation.getArgument(0);
                
                return PublishBatchRequest.builder()
                    .topicArn(topicArn)
                    .build();
            });
        
        when(typeAdapter.toPublishResult(any()))
            .thenAnswer(invocation -> {
                PublishResponse response = invocation.getArgument(0);
                
                return SnsPublishResult.builder()
                    .messageId(response.messageId())
                    .build();
            });
        
        when(typeAdapter.toBatchPublishResult(any()))
            .thenAnswer(invocation -> {
                PublishBatchResultEntry entry = invocation.getArgument(0);
                
                return SnsPublishResult.builder()
                    .messageId(entry.messageId())
                    .build();
            });
    }
    
    private void prepareTestData() {
        // Prepare test messages for benchmarks
        testMessages = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            testMessages.add(SnsMessage.builder()
                .body("Benchmark test message " + i + " with content: " + 
                      "Lorem ipsum dolor sit amet, consectetur adipiscing elit.")
                .subject("Benchmark Subject " + i)
                .attribute("MessageId", String.valueOf(i))
                .attribute("Timestamp", Instant.now().toString())
                .attribute("BenchmarkData", generateRandomString(100))
                .build());
        }
        
        // Prepare batch messages
        batchMessages = new ArrayList<>();
        for (int batch = 0; batch < 100; batch++) {
            List<SnsMessage> batchList = new ArrayList<>();
            for (int msg = 0; msg < 10; msg++) {
                batchList.add(SnsMessage.builder()
                    .body("Batch message " + batch + "-" + msg)
                    .subject("Batch Subject")
                    .attribute("BatchId", String.valueOf(batch))
                    .attribute("MessageIndex", String.valueOf(msg))
                    .build());
            }
            batchMessages.add(batchList);
        }
    }
    
    private SnsMessage getRandomTestMessage() {
        int index = ThreadLocalRandom.current().nextInt(testMessages.size());
        return testMessages.get(index);
    }
    
    private List<SnsMessage> getRandomBatchMessages() {
        int index = ThreadLocalRandom.current().nextInt(batchMessages.size());
        return batchMessages.get(index);
    }
    
    private String generateRandomString(int length) {
        StringBuilder sb = new StringBuilder();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        
        for (int i = 0; i < length; i++) {
            int index = ThreadLocalRandom.current().nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        
        return sb.toString();
    }
    
    /**
     * Main method to run benchmarks
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(SnsBenchmark.class.getSimpleName())
            .jvmArgs("-Xms2g", "-Xmx4g")
            .shouldDoGC(true)
            .shouldFailOnError(true)
            .result("sns-benchmark-results.json")
            .resultFormat(org.openjdk.jmh.results.format.ResultFormatType.JSON)
            .build();
        
        new Runner(opt).run();
    }
}