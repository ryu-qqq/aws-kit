#!/bin/bash

# AWS Kit 포괄적 테스트 실행 스크립트
# SQS Client와 Consumer의 모든 테스트를 단계별로 실행하고 결과를 수집합니다.

set -e

echo "🚀 AWS Kit 포괄적 테스트 실행 시작"
echo "=========================================="

# 색상 정의
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# 현재 디렉토리 확인
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"

# 테스트 결과 디렉토리 생성
TEST_RESULTS_DIR="$PROJECT_ROOT/test-results"
mkdir -p "$TEST_RESULTS_DIR"

# 테스트 시작 시간
START_TIME=$(date +%s)
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")

echo -e "${PURPLE}테스트 결과는 $TEST_RESULTS_DIR 에 저장됩니다.${NC}"
echo ""

# 환경 준비
echo -e "${BLUE}Java 버전 확인...${NC}"
java -version
echo ""

# Docker 상태 확인
if command -v docker &> /dev/null && docker info &> /dev/null; then
    echo -e "${GREEN}✅ Docker 실행 중 - LocalStack 테스트 사용 가능${NC}"
else
    echo -e "${RED}❌ Docker가 실행되지 않음 - LocalStack 테스트 건너뛰기${NC}"
fi

echo ""

# 1단계: SQS Client 유틸리티 테스트
echo -e "${GREEN}=== 1단계: SQS Client 유틸리티 테스트 ===${NC}"
echo "BatchValidationUtils, BatchEntryFactory, QueueAttributeUtils 테스트"

cd "$PROJECT_ROOT/aws-sqs-client"

echo -e "${YELLOW}BatchValidationUtils 테스트 실행...${NC}"
./gradlew test --tests "*BatchValidationUtilsTest*" --info > "$TEST_RESULTS_DIR/batch_validation_utils_test_${TIMESTAMP}.log" 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ BatchValidationUtils 테스트 통과${NC}"
else
    echo -e "${RED}❌ BatchValidationUtils 테스트 실패${NC}"
fi

echo -e "${YELLOW}BatchEntryFactory 테스트 실행...${NC}"
./gradlew test --tests "*BatchEntryFactoryTest*" --info > "$TEST_RESULTS_DIR/batch_entry_factory_test_${TIMESTAMP}.log" 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ BatchEntryFactory 테스트 통과${NC}"
else
    echo -e "${RED}❌ BatchEntryFactory 테스트 실패${NC}"
fi

echo -e "${YELLOW}QueueAttributeUtils 테스트 실행...${NC}"
./gradlew test --tests "*QueueAttributeUtilsTest*" --info > "$TEST_RESULTS_DIR/queue_attribute_utils_test_${TIMESTAMP}.log" 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ QueueAttributeUtils 테스트 통과${NC}"
else
    echo -e "${RED}❌ QueueAttributeUtils 테스트 실패${NC}"
fi

echo ""

# 2단계: SQS Consumer 인프라 테스트
echo -e "${GREEN}=== 2단계: SQS Consumer 인프라 테스트 ===${NC}"
echo "ExecutorServiceProvider, MetricsCollector 테스트"

cd "$PROJECT_ROOT/aws-sqs-consumer"

echo -e "${YELLOW}ExecutorServiceProvider 통합 테스트 실행...${NC}"
./gradlew test --tests "*ExecutorServiceProviderIntegrationTest*" --info > "$TEST_RESULTS_DIR/executor_service_provider_test_${TIMESTAMP}.log" 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ ExecutorServiceProvider 테스트 통과${NC}"
else
    echo -e "${RED}❌ ExecutorServiceProvider 테스트 실패${NC}"
fi

echo -e "${YELLOW}InMemoryMetricsCollector 테스트 실행...${NC}"
./gradlew test --tests "*InMemoryMetricsCollectorTest*" --info > "$TEST_RESULTS_DIR/metrics_collector_test_${TIMESTAMP}.log" 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ InMemoryMetricsCollector 테스트 통과${NC}"
else
    echo -e "${RED}❌ InMemoryMetricsCollector 테스트 실패${NC}"
fi

echo ""

# 3단계: LocalStack 통합 테스트
echo -e "${GREEN}=== 3단계: LocalStack 통합 테스트 ===${NC}"
echo "실제 SQS 환경과 유사한 LocalStack 환경에서 테스트"

cd "$PROJECT_ROOT/aws-sqs-client"

if command -v docker &> /dev/null; then
    echo -e "${YELLOW}LocalStack SQS 통합 테스트 실행...${NC}"
    ./gradlew test --tests "*LocalStackSqsIntegrationTest*" --info > "$TEST_RESULTS_DIR/localstack_integration_test_${TIMESTAMP}.log" 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ LocalStack 통합 테스트 통과${NC}"
    else
        echo -e "${RED}❌ LocalStack 통합 테스트 실패${NC}"
    fi
else
    echo -e "${RED}❌ Docker가 설치되지 않았습니다. LocalStack 테스트를 건너뜁니다.${NC}"
fi

echo ""

# 4단계: 성능 테스트 (Java 21 이상에서만)
echo -e "${GREEN}=== 4단계: 성능 테스트 ===${NC}"
echo "Virtual Thread vs Platform Thread 성능 비교"

cd "$PROJECT_ROOT/aws-sqs-consumer"

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1-2 | tr -d '.')
if [ "$JAVA_VERSION" -ge 21 ]; then
    echo -e "${YELLOW}Virtual Thread 성능 테스트 실행... (Java 21+ 감지됨)${NC}"
    ./gradlew test --tests "*VirtualThreadPerformanceTest*" --info > "$TEST_RESULTS_DIR/virtual_thread_performance_test_${TIMESTAMP}.log" 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ Virtual Thread 성능 테스트 통과${NC}"
    else
        echo -e "${RED}❌ Virtual Thread 성능 테스트 실패${NC}"
    fi
else
    echo -e "${YELLOW}Java 21 미만 버전 감지됨. Virtual Thread 테스트를 건너뜁니다.${NC}"
fi

echo ""

# 5단계: 전체 테스트 커버리지 생성
echo -e "${GREEN}=== 5단계: 테스트 커버리지 보고서 생성 ===${NC}"

cd "$PROJECT_ROOT"

echo -e "${YELLOW}SQS Client 테스트 커버리지 생성...${NC}"
cd aws-sqs-client
./gradlew test jacocoTestReport > "$TEST_RESULTS_DIR/sqs_client_coverage_${TIMESTAMP}.log" 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ SQS Client 커버리지 보고서 생성 완료${NC}"
    if [ -f "build/reports/jacoco/test/html/index.html" ]; then
        echo -e "${BLUE}커버리지 보고서: aws-sqs-client/build/reports/jacoco/test/html/index.html${NC}"
    fi
else
    echo -e "${RED}❌ SQS Client 커버리지 보고서 생성 실패${NC}"
fi

cd "$PROJECT_ROOT"
echo -e "${YELLOW}SQS Consumer 테스트 커버리지 생성...${NC}"
cd aws-sqs-consumer
./gradlew test jacocoTestReport > "$TEST_RESULTS_DIR/sqs_consumer_coverage_${TIMESTAMP}.log" 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ SQS Consumer 커버리지 보고서 생성 완료${NC}"
    if [ -f "build/reports/jacoco/test/html/index.html" ]; then
        echo -e "${BLUE}커버리지 보고서: aws-sqs-consumer/build/reports/jacoco/test/html/index.html${NC}"
    fi
else
    echo -e "${RED}❌ SQS Consumer 커버리지 보고서 생성 실패${NC}"
fi

echo ""

# 6단계: 전체 프로젝트 테스트 실행
echo -e "${GREEN}=== 6단계: 전체 프로젝트 테스트 실행 ===${NC}"

cd "$PROJECT_ROOT"

echo -e "${YELLOW}전체 프로젝트 테스트 실행...${NC}"
./gradlew test --continue > "$TEST_RESULTS_DIR/full_project_test_${TIMESTAMP}.log" 2>&1
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ 전체 프로젝트 테스트 통과${NC}"
else
    echo -e "${RED}❌ 전체 프로젝트 테스트에서 일부 실패 발생${NC}"
fi

echo ""

# 테스트 종료 시간 및 요약
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}           테스트 실행 완료${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""
echo -e "${BLUE}총 실행 시간: ${MINUTES}분 ${SECONDS}초${NC}"
echo -e "${BLUE}테스트 결과 위치: $TEST_RESULTS_DIR${NC}"
echo ""

# 테스트 요약 보고서 생성
SUMMARY_FILE="$TEST_RESULTS_DIR/test_summary_${TIMESTAMP}.md"

cat > "$SUMMARY_FILE" << EOF
# AWS Kit 테스트 실행 요약

**실행 시간**: $(date)
**총 소요 시간**: ${MINUTES}분 ${SECONDS}초
**Java 버전**: $(java -version 2>&1 | head -n 1)

## 실행된 테스트

### 1. SQS Client 유틸리티 테스트
- BatchValidationUtils 테스트
- BatchEntryFactory 테스트  
- QueueAttributeUtils 테스트

### 2. SQS Consumer 인프라 테스트
- ExecutorServiceProvider 통합 테스트
- InMemoryMetricsCollector 테스트

### 3. LocalStack 통합 테스트
- 실제 SQS 환경 시뮬레이션 테스트

### 4. 성능 테스트
- Virtual Thread vs Platform Thread 성능 비교 (Java 21+)

### 5. 테스트 커버리지
- SQS Client 커버리지 보고서
- SQS Consumer 커버리지 보고서

## 테스트 결과 파일

EOF

# 테스트 로그 파일 목록 추가
echo "### 상세 로그 파일" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"
for log_file in "$TEST_RESULTS_DIR"/*.log; do
    if [ -f "$log_file" ]; then
        filename=$(basename "$log_file")
        echo "- \`$filename\`" >> "$SUMMARY_FILE"
    fi
done

echo "" >> "$SUMMARY_FILE"
echo "## 권장사항" >> "$SUMMARY_FILE"
echo "" >> "$SUMMARY_FILE"
echo "1. **커버리지 확인**: 각 모듈의 HTML 커버리지 보고서를 확인하세요" >> "$SUMMARY_FILE"
echo "2. **실패한 테스트**: 실패한 테스트가 있다면 해당 로그 파일을 확인하세요" >> "$SUMMARY_FILE"
echo "3. **성능 분석**: Virtual Thread 성능 테스트 결과를 분석하여 최적의 설정을 결정하세요" >> "$SUMMARY_FILE"
echo "4. **통합 테스트**: LocalStack 테스트 결과를 통해 실제 AWS 환경과의 호환성을 확인하세요" >> "$SUMMARY_FILE"

echo -e "${GREEN}📋 테스트 요약 보고서 생성: $SUMMARY_FILE${NC}"
echo ""

# 커버리지 요약 (가능한 경우)
echo -e "${PURPLE}=== 테스트 커버리지 요약 ===${NC}"
if [ -f "aws-sqs-client/build/reports/jacoco/test/html/index.html" ]; then
    echo -e "${BLUE}SQS Client 커버리지 보고서 생성됨${NC}"
fi
if [ -f "aws-sqs-consumer/build/reports/jacoco/test/html/index.html" ]; then
    echo -e "${BLUE}SQS Consumer 커버리지 보고서 생성됨${NC}"
fi

echo ""
echo -e "${CYAN}모든 테스트 실행이 완료되었습니다!${NC}"
echo -e "${YELLOW}상세 결과는 $TEST_RESULTS_DIR 디렉토리를 확인하세요.${NC}"