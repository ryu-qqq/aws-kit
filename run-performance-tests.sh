#!/bin/bash

# AWS Kit Performance Test Runner
# Comprehensive script to execute performance tests across all modules

set -e

# =============================================================================
# Configuration
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
RESULTS_DIR="$PROJECT_ROOT/performance-results"
CONFIG_FILE="$PROJECT_ROOT/performance-test-config.properties"

# Default values
TEST_TYPE="all"
ENVIRONMENT="dev"
THREADS=50
DURATION=60
HEAP_SIZE="4g"
VERBOSE=false
PROFILE=false
GENERATE_REPORT=true

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# =============================================================================
# Helper Functions
# =============================================================================

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

usage() {
    cat << EOF
AWS Kit Performance Test Runner

Usage: $0 [OPTIONS]

OPTIONS:
    -t, --type TYPE             Test type: sns, cache, all (default: all)
    -e, --env ENV              Environment: dev, ci, prod (default: dev)
    --threads NUM              Number of concurrent threads (default: 50)
    --duration SEC             Test duration in seconds (default: 60)
    --heap-size SIZE           JVM heap size (default: 4g)
    --profile                  Enable JVM profiling
    --no-report                Skip report generation
    -v, --verbose              Verbose output
    -h, --help                 Show this help message

EXAMPLES:
    $0 --type sns --threads 100 --duration 120
    $0 --type cache --env prod --heap-size 8g
    $0 --profile --verbose
    $0 --type all --env ci --no-report

ENVIRONMENT CONFIGURATIONS:
    dev:  Light testing for development (20 threads, 30s duration)
    ci:   CI/CD pipeline testing (10 threads, 60s duration)
    prod: Production load testing (200 threads, 300s duration)
EOF
}

check_requirements() {
    log_info "Checking requirements..."
    
    # Check Java version
    if ! java -version 2>&1 | grep -q "version \"21"; then
        log_error "Java 21 is required"
        exit 1
    fi
    
    # Check Gradle
    if ! command -v ./gradlew &> /dev/null; then
        log_error "Gradle wrapper not found"
        exit 1
    fi
    
    # Create results directory
    mkdir -p "$RESULTS_DIR"
    
    log_success "Requirements check passed"
}

load_environment_config() {
    case "$ENVIRONMENT" in
        "dev")
            THREADS=${THREADS:-20}
            DURATION=${DURATION:-30}
            HEAP_SIZE=${HEAP_SIZE:-2g}
            ;;
        "ci")
            THREADS=${THREADS:-10}
            DURATION=${DURATION:-60}
            HEAP_SIZE=${HEAP_SIZE:-2g}
            ;;
        "prod")
            THREADS=${THREADS:-200}
            DURATION=${DURATION:-300}
            HEAP_SIZE=${HEAP_SIZE:-8g}
            ;;
    esac
    
    log_info "Environment: $ENVIRONMENT"
    log_info "Threads: $THREADS"
    log_info "Duration: ${DURATION}s"
    log_info "Heap Size: $HEAP_SIZE"
}

setup_jvm_args() {
    JVM_ARGS="-Xms${HEAP_SIZE} -Xmx${HEAP_SIZE}"
    
    if [ "$PROFILE" = true ]; then
        JVM_ARGS="$JVM_ARGS -XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints"
        JVM_ARGS="$JVM_ARGS -XX:+FlightRecorder"
        JVM_ARGS="$JVM_ARGS -XX:StartFlightRecording=duration=${DURATION}s,filename=$RESULTS_DIR/perf-profile.jfr"
    fi
    
    # GC tuning for performance testing
    JVM_ARGS="$JVM_ARGS -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
    JVM_ARGS="$JVM_ARGS -XX:+PrintGC -XX:+PrintGCDetails -XX:+PrintGCTimeStamps"
    JVM_ARGS="$JVM_ARGS -Xloggc:$RESULTS_DIR/gc.log"
    
    export GRADLE_OPTS="$JVM_ARGS"
}

run_sns_performance_tests() {
    log_info "Running SNS Performance Tests..."
    
    cd "$PROJECT_ROOT/aws-sns-client"
    
    # Run JUnit performance tests
    ./gradlew test --tests "*SnsPerformanceTest*" \
        -Dperformance.threads=$THREADS \
        -Dperformance.duration=$DURATION \
        -Dperformance.environment=$ENVIRONMENT \
        --info
    
    # Run JMH benchmarks
    log_info "Running SNS JMH Benchmarks..."
    ./gradlew test --tests "*SnsBenchmark*" \
        -Djmh.threads=$((THREADS / 10)) \
        -Djmh.warmup.time=$((DURATION / 4)) \
        -Djmh.measurement.time=$((DURATION / 2)) \
        --info
    
    # Copy results
    if [ -d "build/reports/tests" ]; then
        cp -r build/reports/tests "$RESULTS_DIR/sns-junit-reports"
    fi
    
    if [ -f "sns-benchmark-results.json" ]; then
        mv sns-benchmark-results.json "$RESULTS_DIR/"
    fi
    
    cd "$PROJECT_ROOT"
    log_success "SNS Performance Tests completed"
}

run_cache_performance_tests() {
    log_info "Running Cache Performance Tests..."
    
    cd "$PROJECT_ROOT/aws-secrets-client"
    
    # Run JUnit performance tests
    ./gradlew test --tests "*CachePerformanceTest*" \
        -Dperformance.threads=$THREADS \
        -Dperformance.duration=$DURATION \
        -Dperformance.environment=$ENVIRONMENT \
        --info
    
    # Run JMH benchmarks
    log_info "Running Cache JMH Benchmarks..."
    ./gradlew test --tests "*CacheBenchmark*" \
        -Djmh.threads=$((THREADS / 10)) \
        -Djmh.warmup.time=$((DURATION / 4)) \
        -Djmh.measurement.time=$((DURATION / 2)) \
        --info
    
    # Copy results
    if [ -d "build/reports/tests" ]; then
        cp -r build/reports/tests "$RESULTS_DIR/cache-junit-reports"
    fi
    
    if [ -f "cache-benchmark-results.json" ]; then
        mv cache-benchmark-results.json "$RESULTS_DIR/"
    fi
    
    cd "$PROJECT_ROOT"
    log_success "Cache Performance Tests completed"
}

collect_system_info() {
    log_info "Collecting system information..."
    
    {
        echo "=== System Information ==="
        echo "Date: $(date)"
        echo "Host: $(hostname)"
        echo "OS: $(uname -a)"
        echo "Java Version: $(java -version 2>&1 | head -1)"
        echo "CPU Info: $(lscpu | grep "Model name" || sysctl -n machdep.cpu.brand_string 2>/dev/null || echo "Unknown")"
        echo "Memory: $(free -h 2>/dev/null | grep Mem || vm_stat | head -5)"
        echo "Disk: $(df -h . | tail -1)"
        echo ""
        echo "=== Test Configuration ==="
        echo "Environment: $ENVIRONMENT"
        echo "Test Type: $TEST_TYPE"
        echo "Threads: $THREADS"
        echo "Duration: ${DURATION}s"
        echo "Heap Size: $HEAP_SIZE"
        echo "JVM Args: $JVM_ARGS"
        echo ""
    } > "$RESULTS_DIR/system-info.txt"
}

generate_performance_report() {
    if [ "$GENERATE_REPORT" = false ]; then
        log_info "Skipping report generation"
        return
    fi
    
    log_info "Generating performance report..."
    
    REPORT_FILE="$RESULTS_DIR/performance-report-$(date +%Y%m%d-%H%M%S).html"
    
    cat > "$REPORT_FILE" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>AWS Kit Performance Test Report</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { background-color: #f0f0f0; padding: 10px; border-radius: 5px; }
        .section { margin: 20px 0; }
        .metric { background-color: #f8f9fa; padding: 10px; margin: 5px 0; border-radius: 3px; }
        .success { color: green; }
        .warning { color: orange; }
        .error { color: red; }
        table { border-collapse: collapse; width: 100%; }
        th, td { border: 1px solid #ddd; padding: 8px; text-align: left; }
        th { background-color: #f2f2f2; }
    </style>
</head>
<body>
    <div class="header">
        <h1>AWS Kit Performance Test Report</h1>
        <p>Generated on: $(date)</p>
        <p>Environment: $ENVIRONMENT | Threads: $THREADS | Duration: ${DURATION}s</p>
    </div>
EOF
    
    # Add system information
    if [ -f "$RESULTS_DIR/system-info.txt" ]; then
        echo "<div class='section'><h2>System Information</h2><pre>" >> "$REPORT_FILE"
        cat "$RESULTS_DIR/system-info.txt" >> "$REPORT_FILE"
        echo "</pre></div>" >> "$REPORT_FILE"
    fi
    
    # Add test results links
    echo "<div class='section'><h2>Test Results</h2><ul>" >> "$REPORT_FILE"
    
    if [ -d "$RESULTS_DIR/sns-junit-reports" ]; then
        echo "<li><a href='sns-junit-reports/test/index.html'>SNS JUnit Test Reports</a></li>" >> "$REPORT_FILE"
    fi
    
    if [ -d "$RESULTS_DIR/cache-junit-reports" ]; then
        echo "<li><a href='cache-junit-reports/test/index.html'>Cache JUnit Test Reports</a></li>" >> "$REPORT_FILE"
    fi
    
    if [ -f "$RESULTS_DIR/sns-benchmark-results.json" ]; then
        echo "<li><a href='sns-benchmark-results.json'>SNS JMH Benchmark Results (JSON)</a></li>" >> "$REPORT_FILE"
    fi
    
    if [ -f "$RESULTS_DIR/cache-benchmark-results.json" ]; then
        echo "<li><a href='cache-benchmark-results.json'>Cache JMH Benchmark Results (JSON)</a></li>" >> "$REPORT_FILE"
    fi
    
    if [ -f "$RESULTS_DIR/gc.log" ]; then
        echo "<li><a href='gc.log'>Garbage Collection Log</a></li>" >> "$REPORT_FILE"
    fi
    
    if [ -f "$RESULTS_DIR/perf-profile.jfr" ]; then
        echo "<li>Java Flight Recorder Profile: perf-profile.jfr (use Java Mission Control to view)</li>" >> "$REPORT_FILE"
    fi
    
    echo "</ul></div></body></html>" >> "$REPORT_FILE"
    
    log_success "Performance report generated: $REPORT_FILE"
}

cleanup() {
    log_info "Cleaning up..."
    
    # Kill any remaining Java processes from tests
    pkill -f "gradle.*test" || true
    
    # Clean up temporary files
    find "$PROJECT_ROOT" -name "hs_err_pid*.log" -delete 2>/dev/null || true
    
    log_success "Cleanup completed"
}

# =============================================================================
# Main Script
# =============================================================================

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -t|--type)
            TEST_TYPE="$2"
            shift 2
            ;;
        -e|--env)
            ENVIRONMENT="$2"
            shift 2
            ;;
        --threads)
            THREADS="$2"
            shift 2
            ;;
        --duration)
            DURATION="$2"
            shift 2
            ;;
        --heap-size)
            HEAP_SIZE="$2"
            shift 2
            ;;
        --profile)
            PROFILE=true
            shift
            ;;
        --no-report)
            GENERATE_REPORT=false
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Set verbose output
if [ "$VERBOSE" = true ]; then
    set -x
fi

# Main execution
log_info "Starting AWS Kit Performance Tests"
log_info "======================================"

# Setup cleanup trap
trap cleanup EXIT

check_requirements
load_environment_config
setup_jvm_args
collect_system_info

# Execute tests based on type
case "$TEST_TYPE" in
    "sns")
        run_sns_performance_tests
        ;;
    "cache")
        run_cache_performance_tests
        ;;
    "all")
        run_sns_performance_tests
        run_cache_performance_tests
        ;;
    *)
        log_error "Invalid test type: $TEST_TYPE"
        usage
        exit 1
        ;;
esac

generate_performance_report

log_success "All performance tests completed successfully!"
log_info "Results available in: $RESULTS_DIR"

exit 0