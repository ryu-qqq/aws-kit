package com.ryuqq.aws.dynamodb.example;

import com.ryuqq.aws.dynamodb.properties.DynamoDbProperties;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DynamoDB 설정 예제 애플리케이션
 * 
 * 이 예제는 새로 추가된 기능들의 사용법을 보여줍니다:
 * 1. tablePrefix와 tableSuffix 설정
 * 2. timeout과 maxRetries 설정
 * 3. TableNameResolver 사용법
 * 4. 설정 검증 방법
 */
@SpringBootApplication
public class ConfigurationExample implements CommandLineRunner {

    @Autowired
    private DynamoDbProperties dynamoDbProperties;
    
    @Autowired
    private TableNameResolver tableNameResolver;

    public static void main(String[] args) {
        SpringApplication.run(ConfigurationExample.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== AWS DynamoDB Kit 설정 확인 ===");
        
        // 프로퍼티 설정 확인
        System.out.println("AWS Region: " + dynamoDbProperties.getRegion());
        System.out.println("Endpoint: " + dynamoDbProperties.getEndpoint());
        System.out.println("Table Prefix: '" + dynamoDbProperties.getTablePrefix() + "'");
        System.out.println("Table Suffix: '" + dynamoDbProperties.getTableSuffix() + "'");
        System.out.println("Timeout: " + dynamoDbProperties.getTimeout());
        System.out.println("Max Retries: " + dynamoDbProperties.getMaxRetries());
        
        System.out.println("\n=== 테이블명 변환 예시 ===");
        
        // TableNameResolver 사용 예시
        System.out.println("TableNameResolver 정보: " + tableNameResolver);
        
        String[] sampleTableNames = {"users", "products", "orders", "inventory"};
        for (String tableName : sampleTableNames) {
            String resolvedName = tableNameResolver.resolve(tableName);
            System.out.println("'" + tableName + "' → '" + resolvedName + "'");
        }
        
        System.out.println("\n=== 설정 가이드 ===");
        System.out.println("application.yml 설정 예시:");
        System.out.println("aws:");
        System.out.println("  dynamodb:");
        System.out.println("    region: ap-northeast-2");
        System.out.println("    table-prefix: \"dev-\"        # 환경별 접두사");
        System.out.println("    table-suffix: \"-v2\"         # 버전 접미사");
        System.out.println("    timeout: PT45S              # 45초 타임아웃");
        System.out.println("    max-retries: 5              # 5회 재시도");
        
        System.out.println("\n=== 환경별 설정 권장사항 ===");
        System.out.println("개발환경: prefix=\"dev-\", timeout=PT30S, maxRetries=3");
        System.out.println("스테이징: prefix=\"staging-\", timeout=PT45S, maxRetries=5");
        System.out.println("프로덕션: prefix=\"prod-\", timeout=PT60S, maxRetries=5");
    }
}