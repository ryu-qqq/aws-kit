package com.ryuqq.aws.dynamodb.validation;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/**
 * DynamoDB Client 전체 테스트 스위트
 * 
 * 이 클래스는 DynamoDB Client의 모든 테스트를 실행하는 테스트 스위트입니다.
 * JUnit 5 Platform을 사용하여 패키지별로 테스트를 구성합니다.
 */
@Suite
@SelectPackages({
    "com.ryuqq.aws.dynamodb.types",           // 타입 클래스들 테스트
    "com.ryuqq.aws.dynamodb.service",          // 서비스 계층 테스트
    "com.ryuqq.aws.dynamodb.util",             // 유틸리티 클래스 테스트
    "com.ryuqq.aws.dynamodb.adapter",          // 어댑터 클래스 테스트
    "com.ryuqq.aws.dynamodb.properties",       // Properties 관련 테스트
    "com.ryuqq.aws.dynamodb.integration"       // 통합 테스트
})
public class DynamoDbClientTestSuite {
    // 테스트 스위트는 어노테이션으로 구성되므로 별도 구현 불필요
}