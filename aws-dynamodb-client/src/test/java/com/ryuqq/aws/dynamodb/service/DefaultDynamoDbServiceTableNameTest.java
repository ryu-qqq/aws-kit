package com.ryuqq.aws.dynamodb.service;

import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * DefaultDynamoDbService의 테이블명 변환 기능 테스트
 */
@ExtendWith(MockitoExtension.class)
class DefaultDynamoDbServiceTableNameTest {

    @Mock
    private DynamoDbEnhancedAsyncClient enhancedClient;
    
    @Mock
    private DynamoDbAsyncClient rawClient;
    
    @Mock
    private DynamoDbAsyncTable<TestItem> mockTable;

    private DefaultDynamoDbService<TestItem> dynamoDbService;

    @DynamoDbBean
    static class TestItem {
        private String id;
        private String name;
        
        public TestItem() {}
        
        public TestItem(String id, String name) {
            this.id = id;
            this.name = name;
        }
        
        @DynamoDbPartitionKey
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @BeforeEach
    void setUp() {
        // prefix와 suffix를 설정한 TableNameResolver
        TableNameResolver tableNameResolver = new TableNameResolver("dev-", "-v1");
        dynamoDbService = new DefaultDynamoDbService<>(enhancedClient, rawClient, tableNameResolver);
    }

    @Test
    @DisplayName("save 작업 시 테이블명 변환이 적용되어야 함")
    void shouldApplyTableNameTransformationForSaveOperation() {
        // given
        TestItem testItem = new TestItem("123", "test");
        String originalTableName = "users";
        String expectedResolvedTableName = "dev-users-v1";
        
        when(enhancedClient.table(eq(expectedResolvedTableName), any(TableSchema.class)))
                .thenReturn(mockTable);
        when(mockTable.putItem(testItem)).thenReturn(CompletableFuture.completedFuture(null));
        
        // when
        dynamoDbService.save(testItem, originalTableName);
        
        // then
        verify(enhancedClient).table(expectedResolvedTableName, TableSchema.fromBean(TestItem.class));
        verify(mockTable).putItem(testItem);
    }

    @Test
    @DisplayName("load 작업 시 테이블명 변환이 적용되어야 함")
    void shouldApplyTableNameTransformationForLoadOperation() {
        // given
        DynamoKey key = DynamoKey.partitionKey("id", "123");
        String originalTableName = "products";
        String expectedResolvedTableName = "dev-products-v1";
        
        when(enhancedClient.table(eq(expectedResolvedTableName), any(TableSchema.class)))
                .thenReturn(mockTable);
        when(mockTable.getItem(any(software.amazon.awssdk.enhanced.dynamodb.Key.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        
        // when
        dynamoDbService.load(TestItem.class, key, originalTableName);
        
        // then
        verify(enhancedClient).table(expectedResolvedTableName, TableSchema.fromBean(TestItem.class));
    }

    @Test
    @DisplayName("delete 작업 시 테이블명 변환이 적용되어야 함")
    void shouldApplyTableNameTransformationForDeleteOperation() {
        // given
        DynamoKey key = DynamoKey.partitionKey("id", "123");
        String originalTableName = "orders";
        String expectedResolvedTableName = "dev-orders-v1";
        
        when(enhancedClient.table(eq(expectedResolvedTableName), any(TableSchema.class)))
                .thenReturn(mockTable);
        when(mockTable.deleteItem(any(software.amazon.awssdk.enhanced.dynamodb.Key.class)))
                .thenReturn(CompletableFuture.completedFuture(new TestItem()));
        
        // when
        dynamoDbService.delete(key, originalTableName, TestItem.class);
        
        // then
        verify(enhancedClient).table(expectedResolvedTableName, TableSchema.fromBean(TestItem.class));
    }

    @Test
    @DisplayName("scan 작업 시 테이블명 변환이 적용되어야 함")
    void shouldApplyTableNameTransformationForScanOperation() {
        // given
        String originalTableName = "inventory";
        String expectedResolvedTableName = "dev-inventory-v1";
        
        when(enhancedClient.table(eq(expectedResolvedTableName), any(TableSchema.class)))
                .thenReturn(mockTable);
        // scan 메서드는 복잡한 SdkPublisher를 반환하므로 테이블 호출만 검증
        
        // when & then - 예외가 발생하지 않으면 테이블명 변환이 적용됨
        try {
            dynamoDbService.scan(TestItem.class, originalTableName);
        } catch (Exception e) {
            // Mock으로 인한 예외는 무시하고, 테이블명 변환만 검증
        }
        
        // then
        verify(enhancedClient).table(expectedResolvedTableName, TableSchema.fromBean(TestItem.class));
    }

    @Test
    @DisplayName("getTableNameResolver()가 올바른 resolver를 반환해야 함")
    void shouldReturnCorrectTableNameResolver() {
        // when
        TableNameResolver resolver = dynamoDbService.getTableNameResolver();
        
        // then
        assertThat(resolver).isNotNull();
        assertThat(resolver.getTablePrefix()).isEqualTo("dev-");
        assertThat(resolver.getTableSuffix()).isEqualTo("-v1");
        assertThat(resolver.resolve("test")).isEqualTo("dev-test-v1");
    }

    @Test
    @DisplayName("변환이 없는 TableNameResolver를 사용한 경우")
    void shouldWorkWithNoTransformationResolver() {
        // given - 변환이 없는 resolver로 새 서비스 생성
        TableNameResolver noTransformResolver = new TableNameResolver("", "");
        DefaultDynamoDbService<TestItem> serviceWithNoTransform = 
            new DefaultDynamoDbService<>(enhancedClient, rawClient, noTransformResolver);
        
        String tableName = "original_table";
        TestItem testItem = new TestItem("123", "test");
        
        when(enhancedClient.table(eq(tableName), any(TableSchema.class)))
                .thenReturn(mockTable);
        when(mockTable.putItem(testItem)).thenReturn(CompletableFuture.completedFuture(null));
        
        // when
        serviceWithNoTransform.save(testItem, tableName);
        
        // then - 원본 테이블명 그대로 사용되어야 함
        verify(enhancedClient).table(tableName, TableSchema.fromBean(TestItem.class));
        assertThat(noTransformResolver.hasNoTransformation()).isTrue();
    }

    @Test
    @DisplayName("하위 호환성을 위한 생성자가 기본 resolver를 생성해야 함")
    void shouldCreateDefaultResolverForBackwardCompatibility() {
        // given - 구형 생성자 사용 (TableNameResolver 없이)
        DefaultDynamoDbService<TestItem> backwardCompatibleService = 
            new DefaultDynamoDbService<>(enhancedClient, rawClient);
        
        // when
        TableNameResolver resolver = backwardCompatibleService.getTableNameResolver();
        
        // then
        assertThat(resolver).isNotNull();
        assertThat(resolver.hasNoTransformation()).isTrue();
        assertThat(resolver.getTablePrefix()).isEmpty();
        assertThat(resolver.getTableSuffix()).isEmpty();
    }
}