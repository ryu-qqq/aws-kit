package com.ryuqq.aws.dynamodb.util;

/**
 * DynamoDB 테이블명 변환을 담당하는 유틸리티 클래스
 * 
 * <p>이 클래스는 설정된 prefix와 suffix를 기반으로 테이블명을 자동으로 변환합니다.
 * 주로 환경별 테이블 분리나 네이밍 규칙 적용에 사용됩니다.</p>
 * 
 * <p>사용 예시:</p>
 * <pre>
 * // prefix: "dev-", suffix: "-v1" 로 설정된 경우
 * TableNameResolver resolver = new TableNameResolver("dev-", "-v1");
 * String resolvedName = resolver.resolve("users"); // "dev-users-v1"
 * </pre>
 * 
 * <p>환경별 설정 예시:</p>
 * <pre>
 * # application.yml
 * aws:
 *   dynamodb:
 *     table-prefix: "${ENVIRONMENT}-"  # dev-, staging-, prod-
 *     table-suffix: ""
 * </pre>
 */
public class TableNameResolver {
    
    /** 테이블명 앞에 추가할 접두사 */
    private final String tablePrefix;
    
    /** 테이블명 뒤에 추가할 접미사 */
    private final String tableSuffix;
    
    /**
     * TableNameResolver 생성자
     * 
     * @param tablePrefix 테이블명 접두사 (null이면 빈 문자열로 처리)
     * @param tableSuffix 테이블명 접미사 (null이면 빈 문자열로 처리)
     */
    public TableNameResolver(String tablePrefix, String tableSuffix) {
        this.tablePrefix = tablePrefix != null ? tablePrefix : "";
        this.tableSuffix = tableSuffix != null ? tableSuffix : "";
    }
    
    /**
     * 주어진 테이블명에 prefix와 suffix를 적용하여 실제 DynamoDB 테이블명을 생성합니다.
     * 
     * @param tableName 원본 테이블명
     * @return prefix와 suffix가 적용된 최종 테이블명
     * @throws IllegalArgumentException 테이블명이 null이거나 빈 문자열인 경우
     * 
     * 변환 예시:
     * <ul>
     *   <li>prefix="dev-", suffix="-v1", tableName="users" → "dev-users-v1"</li>
     *   <li>prefix="", suffix="", tableName="products" → "products" (변환 없음)</li>
     *   <li>prefix="staging-", suffix="", tableName="orders" → "staging-orders"</li>
     * </ul>
     */
    public String resolve(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        // prefix와 suffix가 모두 비어있으면 원본 테이블명 그대로 반환
        if (tablePrefix.isEmpty() && tableSuffix.isEmpty()) {
            return tableName;
        }
        
        return tablePrefix + tableName + tableSuffix;
    }
    
    /**
     * 현재 설정된 테이블 접두사를 반환합니다.
     * 
     * @return 테이블 접두사 (설정되지 않았으면 빈 문자열)
     */
    public String getTablePrefix() {
        return tablePrefix;
    }
    
    /**
     * 현재 설정된 테이블 접미사를 반환합니다.
     * 
     * @return 테이블 접미사 (설정되지 않았으면 빈 문자열)
     */
    public String getTableSuffix() {
        return tableSuffix;
    }
    
    /**
     * prefix와 suffix가 모두 설정되지 않았는지 확인합니다.
     * 
     * @return prefix와 suffix가 모두 비어있으면 true, 그렇지 않으면 false
     */
    public boolean hasNoTransformation() {
        return tablePrefix.isEmpty() && tableSuffix.isEmpty();
    }
    
    /**
     * 현재 설정된 변환 규칙을 문자열로 표현합니다.
     * 주로 디버깅이나 로깅 목적으로 사용됩니다.
     * 
     * @return 변환 규칙을 나타내는 문자열
     */
    @Override
    public String toString() {
        if (hasNoTransformation()) {
            return "TableNameResolver{no transformation}";
        }
        return String.format("TableNameResolver{prefix='%s', suffix='%s'}", 
                           tablePrefix, tableSuffix);
    }
}