# AWS Kit Performance Testing Guide

This guide provides comprehensive information about performance testing for AWS Kit modules, including setup, execution, and analysis of results.

## Overview

The AWS Kit performance testing suite includes:

1. **SNS Performance Tests** - Concurrent publishing, batch operations, error recovery
2. **Cache Performance Tests** - Hit/miss ratios, memory management, eviction performance
3. **JMH Benchmarks** - Detailed micro-benchmarks for both modules
4. **Comprehensive Reporting** - HTML reports with system information and metrics

## Quick Start

### Basic Usage

```bash
# Run all performance tests with default settings
./run-performance-tests.sh

# Run only SNS performance tests
./run-performance-tests.sh --type sns

# Run cache performance tests with high concurrency
./run-performance-tests.sh --type cache --threads 100 --duration 120
```

### Environment-Specific Testing

```bash
# Development environment (lighter load)
./run-performance-tests.sh --env dev

# CI/CD pipeline testing
./run-performance-tests.sh --env ci

# Production load testing
./run-performance-tests.sh --env prod --heap-size 8g
```

## Test Modules

### 1. SNS Performance Tests (`SnsPerformanceTest.java`)

**Features Tested:**
- Concurrent message publishing (100 threads × 50 messages)
- Batch publishing with different batch sizes (1-50 messages)
- Error recovery and timeout handling (20% failure rate simulation)
- Memory usage and leak detection (5 test cycles)
- Async operation coordination (1000 coordinated operations)

**Key Metrics:**
- Average latency: < 100ms
- 95th percentile latency: < 500ms
- Throughput: > 1000 ops/sec
- Memory increase: < 100MB
- Success rate: > 95%

**Sample Usage:**
```bash
# Run SNS tests with custom parameters
./gradlew :aws-sns-client:test --tests "*SnsPerformanceTest*" \
  -Dperformance.threads=50 \
  -Dperformance.duration=60
```

### 2. Cache Performance Tests (`CachePerformanceTest.java`)

**Features Tested:**
- Cache hit/miss performance with different access patterns
- Concurrent cache access under load
- Cache eviction and memory management
- Cache invalidation performance
- Memory pressure testing

**Key Metrics:**
- Cache hit latency: < 10ms
- Cache miss latency: < 500ms
- Cache hit ratio: > 75%
- Throughput: > 2000 ops/sec
- Memory increase: < 200MB

**Access Patterns Tested:**
- High Locality (80% ops on 20% keys)
- Medium Locality (60% ops on 40% keys)
- Low Locality (uniform distribution)
- Hot Keys (50% ops on 5% keys)

### 3. JMH Benchmarks

**SNS Benchmark (`SnsBenchmark.java`):**
- Single message publishing
- Batch message publishing
- SMS publishing
- Concurrent publishing
- Message creation overhead
- Type adapter performance
- Error handling paths

**Cache Benchmark (`CacheBenchmark.java`):**
- Cache hit performance
- Cache miss performance
- Mixed access patterns (Pareto distribution)
- Concurrent cache access
- JSON parsing performance
- Typed secret retrieval
- Cache operations (invalidation, creation, updates)

## Performance Thresholds

All thresholds are configurable in `performance-test-config.properties`:

### SNS Thresholds
```properties
sns.latency.average.max.ms=100
sns.latency.p95.max.ms=500
sns.throughput.min.ops.per.sec=1000
sns.success.rate.min=0.95
```

### Cache Thresholds
```properties
cache.hit.latency.max.ms=10
cache.miss.latency.max.ms=500
cache.hit.ratio.min=0.75
cache.throughput.min.ops.per.sec=2000
```

### Memory Thresholds
```properties
memory.heap.max.usage.percent=0.85
memory.leak.growth.max.percent=0.5
```

## Test Scenarios

### 1. Baseline Performance
- 10 threads, 60s duration, 100 ops/thread
- Establishes performance baseline

### 2. High Concurrency
- 100 threads, 120s duration, 50 ops/thread
- Tests system under high concurrent load

### 3. Memory Pressure
- 50 threads, 180s duration, 1GB heap pressure
- Validates performance under memory constraints

### 4. Error Recovery
- 25 threads, 90s duration, 30% error rate
- Tests resilience and recovery mechanisms

### 5. Cache Performance
- 75 threads, 150s duration, 2000 cache entries
- Optimized for cache behavior analysis

## Configuration

### Environment Variables
```bash
export PERFORMANCE_THREADS=50
export PERFORMANCE_DURATION=60
export PERFORMANCE_HEAP_SIZE=4g
export PERFORMANCE_PROFILE=true
```

### JVM Tuning
The test runner automatically applies performance-optimized JVM settings:
```bash
-Xms4g -Xmx4g                    # Heap sizing
-XX:+UseG1GC                     # G1 garbage collector
-XX:MaxGCPauseMillis=100         # GC pause target
-XX:+PrintGC -XX:+PrintGCDetails # GC logging
```

### Profiling
Enable detailed profiling:
```bash
./run-performance-tests.sh --profile
```

Generates:
- Java Flight Recorder profiles (`*.jfr`)
- GC logs (`gc.log`)
- Memory allocation tracking

## Output and Reports

### Results Directory Structure
```
performance-results/
├── system-info.txt                    # System and configuration info
├── sns-junit-reports/                 # SNS JUnit test reports
├── cache-junit-reports/               # Cache JUnit test reports
├── sns-benchmark-results.json         # SNS JMH benchmark results
├── cache-benchmark-results.json       # Cache JMH benchmark results
├── gc.log                            # Garbage collection log
├── perf-profile.jfr                  # Java Flight Recorder profile
└── performance-report-YYYYMMDD-HHMMSS.html  # Consolidated HTML report
```

### Report Contents
- **System Information**: Hardware, OS, Java version
- **Test Configuration**: Parameters, thresholds, scenarios
- **Performance Metrics**: Latency, throughput, memory usage
- **Regression Analysis**: Comparison with previous runs
- **Recommendations**: Performance optimization suggestions

## Running Individual Tests

### JUnit Performance Tests
```bash
# SNS performance tests
./gradlew :aws-sns-client:test --tests "*SnsPerformanceTest*"

# Cache performance tests  
./gradlew :aws-secrets-client:test --tests "*CachePerformanceTest*"

# Run with custom properties
./gradlew test -Dperformance.threads=100 -Dperformance.duration=120
```

### JMH Benchmarks
```bash
# Run SNS benchmarks
./gradlew :aws-sns-client:test --tests "*SnsBenchmark*"

# Run cache benchmarks
./gradlew :aws-secrets-client:test --tests "*CacheBenchmark*"

# Generate JSON results
./gradlew test -Djmh.result.format=JSON -Djmh.result=results.json
```

## Performance Analysis

### Interpreting Results

**Latency Metrics:**
- Mean latency: Average response time
- P95 latency: 95th percentile response time
- P99 latency: 99th percentile response time

**Throughput Metrics:**
- Ops/sec: Operations per second
- Concurrent throughput: Throughput under concurrent load

**Memory Metrics:**
- Heap usage: JVM heap memory consumption
- GC overhead: Time spent in garbage collection
- Memory growth: Memory increase during test

**Cache Metrics:**
- Hit ratio: Percentage of cache hits vs misses
- Hit latency: Response time for cached entries
- Miss latency: Response time for cache misses

### Regression Detection

The test suite includes regression detection:
- **Latency Regression**: > 20% increase in response time
- **Throughput Regression**: > 15% decrease in operations/sec
- **Memory Regression**: > 30% increase in memory usage

### Performance Optimization

Based on test results, consider:

**For SNS:**
- Adjust batch sizes based on batch performance tests
- Optimize concurrent thread pool sizing
- Implement connection pooling for high-throughput scenarios

**For Cache:**
- Tune cache size based on hit ratio analysis
- Adjust TTL based on access patterns
- Implement cache warming for frequently accessed secrets

## CI/CD Integration

### GitHub Actions Example
```yaml
- name: Run Performance Tests
  run: |
    ./run-performance-tests.sh --env ci --no-report
    
- name: Check Performance Thresholds  
  run: |
    # Custom script to validate results against thresholds
    ./check-performance-thresholds.sh
    
- name: Upload Performance Results
  uses: actions/upload-artifact@v3
  with:
    name: performance-results
    path: performance-results/
```

### Jenkins Pipeline Example
```groovy
stage('Performance Tests') {
    steps {
        sh './run-performance-tests.sh --env ci'
        publishHTML([
            allowMissing: false,
            alwaysLinkToLastBuild: true,
            keepAll: true,
            reportDir: 'performance-results',
            reportFiles: 'performance-report-*.html',
            reportName: 'Performance Test Report'
        ])
    }
}
```

## Troubleshooting

### Common Issues

**Out of Memory Errors:**
```bash
# Increase heap size
./run-performance-tests.sh --heap-size 8g
```

**Test Timeouts:**
```bash
# Reduce concurrent threads or duration
./run-performance-tests.sh --threads 25 --duration 30
```

**Mock Service Errors:**
- Check mock response delays in configuration
- Verify error rates are reasonable for test scenario

### Performance Debugging

**Enable Detailed Logging:**
```bash
./run-performance-tests.sh --verbose --profile
```

**Analyze GC Performance:**
```bash
# View GC log
less performance-results/gc.log

# Analyze with GC tools
jstat -gc -t <pid> 1s
```

**Memory Analysis:**
```bash
# Generate heap dump on OOM
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=./performance-results/
```

## Best Practices

### Test Design
1. Use realistic data sizes and access patterns
2. Include both success and failure scenarios  
3. Test across different hardware configurations
4. Validate performance under different load patterns

### Execution
1. Run tests in isolated environments
2. Ensure consistent hardware and OS configuration
3. Perform multiple runs to establish confidence intervals
4. Monitor system resources during tests

### Analysis
1. Compare results against established baselines
2. Look for performance trends over time
3. Correlate performance changes with code changes
4. Document performance characteristics and trade-offs

## Dependencies

The performance tests require:
- Java 21+
- JMH 1.37+
- Mockito for mocking AWS services
- AssertJ for test assertions
- Spring Boot Test framework

All dependencies are managed through Gradle and automatically included.

## Contributing

When adding new performance tests:

1. Follow existing naming conventions
2. Include comprehensive assertions with meaningful thresholds
3. Add proper logging and metrics collection
4. Document any new configuration options
5. Update this guide with new test descriptions

For questions or issues, please refer to the main AWS Kit documentation or open an issue in the repository.