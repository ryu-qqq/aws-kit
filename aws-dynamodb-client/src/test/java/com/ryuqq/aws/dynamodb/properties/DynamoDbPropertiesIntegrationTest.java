package com.ryuqq.aws.dynamodb.properties;

import com.ryuqq.aws.dynamodb.AwsDynamoDbAutoConfiguration;
import com.ryuqq.aws.dynamodb.service.DynamoDbService;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * DynamoDbProperties의 실제 설정 적용을 확인하는 통합 테스트
 */
@SpringBootTest(classes = AwsDynamoDbAutoConfiguration.class)
class DynamoDbPropertiesIntegrationTest {

    @SpringBootTest(classes = AwsDynamoDbAutoConfiguration.class)
    @TestPropertySource(properties = {
            "aws.dynamodb.region=ap-northeast-2",
            "aws.dynamodb.table-prefix=dev-",
            "aws.dynamodb.table-suffix=-v1", 
            "aws.dynamodb.timeout=PT45S",
            "aws.dynamodb.max-retries=5"
    })
    static class WithCustomProperties {
        
        @Autowired
        private DynamoDbProperties properties;
        
        @Autowired
        private TableNameResolver tableNameResolver;
        
        @Autowired
        private DynamoDbAsyncClient dynamoDbAsyncClient;
        
        @Autowired
        private DynamoDbService<?> dynamoDbService;
        
        @Test
        @DisplayName("커스텀 프로퍼티가 정상적으로 로드되어야 함")
        void shouldLoadCustomProperties() {
            // then
            assertThat(properties.getRegion()).isEqualTo("ap-northeast-2");
            assertThat(properties.getTablePrefix()).isEqualTo("dev-");
            assertThat(properties.getTableSuffix()).isEqualTo("-v1");
            assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(45));
            assertThat(properties.getMaxRetries()).isEqualTo(5);
        }
        
        @Test
        @DisplayName("TableNameResolver가 프로퍼티 값으로 정상 생성되어야 함")
        void shouldCreateTableNameResolverWithProperties() {
            // then
            assertThat(tableNameResolver.getTablePrefix()).isEqualTo("dev-");
            assertThat(tableNameResolver.getTableSuffix()).isEqualTo("-v1");
            assertThat(tableNameResolver.hasNoTransformation()).isFalse();
        }
        
        @Test
        @DisplayName("DynamoDbAsyncClient가 정상적으로 빈 등록되어야 함")
        void shouldRegisterDynamoDbAsyncClientBean() {
            // then
            assertThat(dynamoDbAsyncClient).isNotNull();
        }
        
        @Test
        @DisplayName("DynamoDbService가 정상적으로 빈 등록되어야 함")
        void shouldRegisterDynamoDbServiceBean() {
            // then
            assertThat(dynamoDbService).isNotNull();
        }
        
        @Test
        @DisplayName("DynamoDbService에서 TableNameResolver 접근 가능해야 함")
        void shouldAccessTableNameResolverFromService() {
            // when
            TableNameResolver serviceResolver = dynamoDbService.getTableNameResolver();
            
            // then
            assertThat(serviceResolver).isNotNull();
            assertThat(serviceResolver.getTablePrefix()).isEqualTo("dev-");
            assertThat(serviceResolver.getTableSuffix()).isEqualTo("-v1");
        }
    }
    
    @SpringBootTest(classes = AwsDynamoDbAutoConfiguration.class)
    @TestPropertySource(properties = {
            "aws.dynamodb.region=us-west-2",
            "aws.dynamodb.endpoint=http://localhost:4566"
    })
    static class WithLocalStackProperties {
        
        @Autowired
        private DynamoDbProperties properties;
        
        @Autowired
        private TableNameResolver tableNameResolver;
        
        @Test
        @DisplayName("LocalStack 설정이 정상적으로 적용되어야 함")
        void shouldApplyLocalStackConfiguration() {
            // then
            assertThat(properties.getRegion()).isEqualTo("us-west-2");
            assertThat(properties.getEndpoint()).isEqualTo("http://localhost:4566");
        }
        
        @Test
        @DisplayName("기본값이 적용된 TableNameResolver가 생성되어야 함")
        void shouldCreateDefaultTableNameResolver() {
            // then
            assertThat(tableNameResolver.hasNoTransformation()).isTrue();
            assertThat(tableNameResolver.getTablePrefix()).isEmpty();
            assertThat(tableNameResolver.getTableSuffix()).isEmpty();
        }
    }
    
    @SpringBootTest(classes = AwsDynamoDbAutoConfiguration.class)
    static class WithDefaultProperties {
        
        @Autowired
        private DynamoDbProperties properties;
        
        @Autowired
        private TableNameResolver tableNameResolver;
        
        @Test
        @DisplayName("기본 프로퍼티 값들이 정상적으로 적용되어야 함")
        void shouldUseDefaultPropertyValues() {
            // then
            assertThat(properties.getRegion()).isEqualTo("us-west-2");
            assertThat(properties.getEndpoint()).isNull();
            assertThat(properties.getTablePrefix()).isEmpty();
            assertThat(properties.getTableSuffix()).isEmpty();
            assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getMaxRetries()).isEqualTo(3);
        }
        
        @Test
        @DisplayName("기본값으로 변환이 없는 TableNameResolver가 생성되어야 함")
        void shouldCreateNoTransformationResolver() {
            // then
            assertThat(tableNameResolver.hasNoTransformation()).isTrue();
        }
    }
    
    @SpringBootTest(classes = AwsDynamoDbAutoConfiguration.class)
    @TestPropertySource(properties = {
            "aws.dynamodb.table-prefix=production-",
            "aws.dynamodb.table-suffix="  // 빈 문자열 명시적 설정
    })
    static class WithProductionPrefixOnly {
        
        @Autowired
        private TableNameResolver tableNameResolver;
        
        @Test
        @DisplayName("prefix만 설정된 경우 정상 작동해야 함")
        void shouldWorkWithPrefixOnly() {
            // then
            assertThat(tableNameResolver.getTablePrefix()).isEqualTo("production-");
            assertThat(tableNameResolver.getTableSuffix()).isEmpty();
            assertThat(tableNameResolver.hasNoTransformation()).isFalse();
            
            // when
            String resolved = tableNameResolver.resolve("users");
            
            // then
            assertThat(resolved).isEqualTo("production-users");
        }
    }
    
    @SpringBootTest(classes = AwsDynamoDbAutoConfiguration.class)
    @TestPropertySource(properties = {
            "aws.dynamodb.timeout=PT2M",  // 2분
            "aws.dynamodb.max-retries=10"
    })
    static class WithPerformanceSettings {
        
        @Autowired
        private DynamoDbProperties properties;
        
        @Test
        @DisplayName("성능 관련 설정이 정상 적용되어야 함")
        void shouldApplyPerformanceSettings() {
            // then
            assertThat(properties.getTimeout()).isEqualTo(Duration.ofMinutes(2));
            assertThat(properties.getMaxRetries()).isEqualTo(10);
        }
    }
}