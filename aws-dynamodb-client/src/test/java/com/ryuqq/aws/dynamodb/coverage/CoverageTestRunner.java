package com.ryuqq.aws.dynamodb.coverage;

import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 테스트 커버리지와 실행 결과를 종합적으로 분석하는 유틸리티 클래스
 */
public class CoverageTestRunner {
    
    public static void main(String[] args) {
        System.out.println("DynamoDB Client 포괄적 테스트 실행 시작...");
        System.out.println("=" .repeat(60));
        
        // 테스트 실행을 위한 Launcher 생성
        Launcher launcher = LauncherFactory.create();
        
        // 결과 수집을 위한 리스너 생성
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        
        // 모든 테스트 클래스 검색
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        DiscoverySelectors.selectPackage("com.ryuqq.aws.dynamodb.integration"),
                        DiscoverySelectors.selectPackage("com.ryuqq.aws.dynamodb.service"),
                        DiscoverySelectors.selectPackage("com.ryuqq.aws.dynamodb.types"),
                        DiscoverySelectors.selectPackage("com.ryuqq.aws.dynamodb.properties"),
                        DiscoverySelectors.selectPackage("com.ryuqq.aws.dynamodb.util"),
                        DiscoverySelectors.selectPackage("com.ryuqq.aws.dynamodb.adapter")
                )
                .build();
        
        // 테스트 실행
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        
        // 결과 분석 및 출력
        analyzeResults(listener.getSummary());
    }
    
    private static void analyzeResults(TestExecutionSummary summary) {
        System.out.println("\nテス트 실행 결과 분석");
        System.out.println("=" .repeat(60));
        
        // 기본 통계
        System.out.printf("총 테스트 수: %d\n", summary.getTestsFoundCount());
        System.out.printf("성공한 테스트: %d\n", summary.getTestsSucceededCount());
        System.out.printf("실패한 테스트: %d\n", summary.getTestsFailedCount());
        System.out.printf("건너뛴 테스트: %d\n", summary.getTestsSkippedCount());
        System.out.printf("중단된 테스트: %d\n", summary.getTestsAbortedCount());
        
        // 실행 시간
        System.out.printf("총 실행 시간: %d ms\n", summary.getTimeFinished() - summary.getTimeStarted());
        
        // 성공률 계산
        double successRate = (double) summary.getTestsSucceededCount() / summary.getTestsFoundCount() * 100;
        System.out.printf("테스트 성공률: %.2f%%\n", successRate);
        
        // 실패한 테스트 상세 정보
        if (summary.getTestsFailedCount() > 0) {
            System.out.println("\n실패한 테스트 상세:");
            System.out.println("-" .repeat(40));
            summary.getFailures().forEach(failure -> {
                System.out.printf("클래스: %s\n", failure.getTestIdentifier().getDisplayName());
                System.out.printf("오류: %s\n", failure.getException().getMessage());
                System.out.println();
            });
        }
        
        // 커버리지 권장 사항
        System.out.println("\n코드 커버리지 분석 권장 사항:");
        System.out.println("-" .repeat(40));
        System.out.println("1. 새로 구현된 Transaction 기능 커버리지 확인");
        System.out.println("2. Properties 설정별 동작 테스트 커버리지 확인");
        System.out.println("3. 에러 시나리오 처리 커버리지 확인");
        System.out.println("4. 성능 테스트 시나리오 커버리지 확인");
        
        // 품질 기준 평가
        evaluateQualityStandards(summary);
    }
    
    private static void evaluateQualityStandards(TestExecutionSummary summary) {
        System.out.println("\n품질 기준 평가:");
        System.out.println("-" .repeat(40));
        
        double successRate = (double) summary.getTestsSucceededCount() / summary.getTestsFoundCount() * 100;
        
        // 성공률 평가
        if (successRate >= 95) {
            System.out.println("✅ 테스트 성공률: 우수 (95% 이상)");
        } else if (successRate >= 90) {
            System.out.println("⚠️ 테스트 성공률: 양호 (90% 이상)");
        } else {
            System.out.println("❌ 테스트 성공률: 개선 필요 (90% 미만)");
        }
        
        // 테스트 수 평가
        long totalTests = summary.getTestsFoundCount();
        if (totalTests >= 50) {
            System.out.println("✅ 테스트 수: 충분 (50개 이상)");
        } else if (totalTests >= 30) {
            System.out.println("⚠️ 테스트 수: 보통 (30개 이상)");
        } else {
            System.out.println("❌ 테스트 수: 부족 (30개 미만)");
        }
        
        // 실패 테스트 평가
        if (summary.getTestsFailedCount() == 0) {
            System.out.println("✅ 테스트 실패: 없음");
        } else if (summary.getTestsFailedCount() <= 2) {
            System.out.println("⚠️ 테스트 실패: 소수 (2개 이하)");
        } else {
            System.out.println("❌ 테스트 실패: 다수 (3개 이상)");
        }
        
        // 전체 평가
        System.out.println("\n전체 평가:");
        if (successRate >= 95 && totalTests >= 50 && summary.getTestsFailedCount() == 0) {
            System.out.println("🎉 종합 평가: 매우 우수");
            System.out.println("   - 프로덕션 배포 준비 완료");
        } else if (successRate >= 90 && totalTests >= 30 && summary.getTestsFailedCount() <= 2) {
            System.out.println("👍 종합 평가: 양호");
            System.out.println("   - 추가 개선 후 배포 권장");
        } else {
            System.out.println("🔧 종합 평가: 개선 필요");
            System.out.println("   - 테스트 보완 후 재평가 필요");
        }
    }
}