package com.ryuqq.aws.dynamodb.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * TableNameResolver 단위 테스트
 */
class TableNameResolverTest {

    @Test
    @DisplayName("prefix와 suffix가 모두 설정된 경우 정상 변환")
    void shouldResolveTableNameWithPrefixAndSuffix() {
        // given
        TableNameResolver resolver = new TableNameResolver("dev-", "-v1");
        
        // when
        String result = resolver.resolve("users");
        
        // then
        assertThat(result).isEqualTo("dev-users-v1");
    }

    @Test
    @DisplayName("prefix만 설정된 경우 정상 변환")
    void shouldResolveTableNameWithPrefixOnly() {
        // given
        TableNameResolver resolver = new TableNameResolver("staging-", "");
        
        // when
        String result = resolver.resolve("products");
        
        // then
        assertThat(result).isEqualTo("staging-products");
    }

    @Test
    @DisplayName("suffix만 설정된 경우 정상 변환")
    void shouldResolveTableNameWithSuffixOnly() {
        // given
        TableNameResolver resolver = new TableNameResolver("", "-backup");
        
        // when
        String result = resolver.resolve("orders");
        
        // then
        assertThat(result).isEqualTo("orders-backup");
    }

    @Test
    @DisplayName("prefix와 suffix가 모두 비어있는 경우 원본 반환")
    void shouldReturnOriginalTableNameWhenNoPrefixAndSuffix() {
        // given
        TableNameResolver resolver = new TableNameResolver("", "");
        
        // when
        String result = resolver.resolve("customers");
        
        // then
        assertThat(result).isEqualTo("customers");
    }

    @Test
    @DisplayName("null 값으로 생성된 resolver는 빈 문자열로 처리")
    void shouldTreatNullValuesAsEmptyStrings() {
        // given
        TableNameResolver resolver = new TableNameResolver(null, null);
        
        // when
        String result = resolver.resolve("inventory");
        
        // then
        assertThat(result).isEqualTo("inventory");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  ", "\t", "\n"})
    @DisplayName("잘못된 테이블명에 대해 예외 발생")
    void shouldThrowExceptionForInvalidTableName(String invalidTableName) {
        // given
        TableNameResolver resolver = new TableNameResolver("test-", "-v1");
        
        // when & then
        assertThatThrownBy(() -> resolver.resolve(invalidTableName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Table name cannot be null or empty");
    }

    @Test
    @DisplayName("복잡한 prefix/suffix 조합 테스트")
    void shouldHandleComplexPrefixSuffixCombinations() {
        // given
        String complexPrefix = "app-prod-region1-";
        String complexSuffix = "-2024-encrypted";
        TableNameResolver resolver = new TableNameResolver(complexPrefix, complexSuffix);
        
        // when
        String result = resolver.resolve("user_sessions");
        
        // then
        assertThat(result).isEqualTo("app-prod-region1-user_sessions-2024-encrypted");
    }

    @Test
    @DisplayName("getTablePrefix() 메소드 테스트")
    void shouldReturnCorrectPrefix() {
        // given
        TableNameResolver resolver = new TableNameResolver("dev-", "-v1");
        
        // when & then
        assertThat(resolver.getTablePrefix()).isEqualTo("dev-");
    }

    @Test
    @DisplayName("getTableSuffix() 메소드 테스트")
    void shouldReturnCorrectSuffix() {
        // given
        TableNameResolver resolver = new TableNameResolver("dev-", "-v1");
        
        // when & then
        assertThat(resolver.getTableSuffix()).isEqualTo("-v1");
    }

    @Test
    @DisplayName("hasNoTransformation() 메소드 테스트 - 변환 없음")
    void shouldReturnTrueWhenNoTransformation() {
        // given
        TableNameResolver resolver = new TableNameResolver("", "");
        
        // when & then
        assertThat(resolver.hasNoTransformation()).isTrue();
    }

    @Test
    @DisplayName("hasNoTransformation() 메소드 테스트 - 변환 있음")
    void shouldReturnFalseWhenTransformationExists() {
        // given
        TableNameResolver resolver = new TableNameResolver("dev-", "");
        
        // when & then
        assertThat(resolver.hasNoTransformation()).isFalse();
    }

    @Test
    @DisplayName("toString() 메소드 테스트 - 변환 없음")
    void shouldReturnCorrectStringWhenNoTransformation() {
        // given
        TableNameResolver resolver = new TableNameResolver("", "");
        
        // when
        String result = resolver.toString();
        
        // then
        assertThat(result).isEqualTo("TableNameResolver{no transformation}");
    }

    @Test
    @DisplayName("toString() 메소드 테스트 - 변환 있음")
    void shouldReturnCorrectStringWhenTransformationExists() {
        // given
        TableNameResolver resolver = new TableNameResolver("dev-", "-v1");
        
        // when
        String result = resolver.toString();
        
        // then
        assertThat(result).isEqualTo("TableNameResolver{prefix='dev-', suffix='-v1'}");
    }

    @Test
    @DisplayName("환경별 테이블 분리 시나리오 테스트")
    void shouldSupportEnvironmentBasedTableSeparation() {
        // given
        String environment = "production";
        TableNameResolver resolver = new TableNameResolver(environment + "-", "");
        
        String[] tableNames = {"users", "orders", "products", "inventory"};
        
        // when & then
        for (String tableName : tableNames) {
            String resolved = resolver.resolve(tableName);
            assertThat(resolved).startsWith("production-");
            assertThat(resolved).endsWith(tableName);
        }
    }

    @Test
    @DisplayName("버전 관리 시나리오 테스트")
    void shouldSupportVersionManagementScenario() {
        // given
        String version = "v2";
        TableNameResolver resolver = new TableNameResolver("", "-" + version);
        
        String[] tableNames = {"user_profiles", "order_history", "product_catalog"};
        
        // when & then
        for (String tableName : tableNames) {
            String resolved = resolver.resolve(tableName);
            assertThat(resolved).startsWith(tableName);
            assertThat(resolved).endsWith("-v2");
        }
    }
}