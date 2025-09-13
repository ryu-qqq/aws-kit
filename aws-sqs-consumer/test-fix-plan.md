# SQS Consumer 테스트 수정 계획

## 주요 실패 유형

### 1. SqsListenerAnnotationBeanPostProcessorTest (12개 실패)
- **문제**: Exception message 체크 방식 변경 필요
- **원인**: RuntimeException이 IllegalArgumentException을 감싸고 있음
- **해결책**: getCause().getMessage()로 체크하도록 수정

### 2. RefactoredSqsListenerContainerTest (10개 실패)
- **문제**: RefactoredSqsListenerContainer 사용 관련
- **원인**: 현재 SqsListenerContainer가 사용되고 있음
- **해결책**: 테스트를 SqsListenerContainer에 맞게 수정

### 3. AutoConfiguration 테스트 (5개 실패)
- **문제**: ExecutorServiceProvider Bean 누락
- **원인**: AutoConfiguration에서 ExecutorServiceProvider 빈 생성 누락
- **해결책**: AutoConfiguration 클래스 확인 및 수정

### 4. Security 테스트 (4개 실패)
- **문제**: DlqMessage Record 변환 및 JSON 처리
- **원인**: Lombok 제거로 인한 테스트 호환성 문제
- **해결책**: Record 방식에 맞게 테스트 수정

### 5. Metrics 테스트 (6개 실패)
- **문제**: InMemoryMetricsCollector 인터페이스 변경
- **원인**: 메서드 시그니처 변경
- **해결책**: 새로운 인터페이스에 맞게 테스트 수정

## 수정 우선순위

1. **SqsListenerAnnotationBeanPostProcessorTest** (완료 중)
2. **AwsSqsConsumerAutoConfigurationTest**
3. **SecurityVulnerabilityTest**
4. **InMemoryMetricsCollectorTest**
5. **RefactoredSqsListenerContainerTest**

## 현재 진행 상황

✅ ThreadingModel Enum 수정 완료
✅ SqsListenerAnnotationBeanPostProcessorTest 완료 (12/12)
  - Mockito unnecessary stubbing 해결 (lenient() 사용)
  - Exception validation 패턴 수정 완료
✅ Jackson JSR310 문제 해결
  - SecurityVulnerabilityTest에 JavaTimeModule 추가
✅ Spring AutoConfiguration 의존성 해결
  - SqsListenerAnnotationBeanPostProcessor 개선 (circular dependency 방지)
  - 필수 configuration properties 추가
🔄 테스트 실패 건수: 51 → 33 (18건 해결)

## 주요 성과

- **SqsListenerAnnotationBeanPostProcessor**: 12개 모든 테스트 통과
- **Jackson JSR310**: SecurityVulnerabilityTest 일부 통과
- **AutoConfiguration**: 5개 중 3개 테스트 통과
- **전체 개선율**: 35% (18/51)

## 다음 수정 대상 (우선순위)

1. **SqsConsumerProperties** - @PostConstruct validation 실패 (여러 테스트 영향)
2. **SecurityVulnerabilityTest** - 나머지 concurrency 테스트들 (3개 실패)
3. **InMemoryMetricsCollectorTest** - 인터페이스 변경 관련
4. **RefactoredSqsListenerContainerTest** - Container 관련
5. **AwsSqsConsumerAutoConfigurationTest** - 나머지 2개 테스트