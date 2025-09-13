package com.ryuqq.aws.dynamodb.service;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.types.DynamoQuery;
import com.ryuqq.aws.dynamodb.types.DynamoTransaction;
import com.ryuqq.aws.dynamodb.adapter.DynamoTypeAdapter;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Simple DynamoDB service implementation using AWS Enhanced DynamoDB client directly
 * 
 * AWS Enhanced DynamoDB 클라이언트를 직접 사용하는 단순한 DynamoDB 서비스 구현체
 * 
 * <p>이 클래스는 DynamoDB 작업을 위한 핵심 서비스 레이어를 제공합니다.
 * 모든 작업은 비동기로 처리되며 CompletableFuture를 반환합니다.</p>
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>단일 항목 저장/로드/삭제</li>
 *   <li>배치 작업 (여러 항목을 한 번에 처리)</li>
 *   <li>쿼리 및 스캔 작업</li>
 *   <li>트랜잭션 지원 (구현 진행 중)</li>
 * </ul>
 * 
 * @param <T> DynamoDB 테이블 항목의 타입
 */
public class DefaultDynamoDbService<T> implements DynamoDbService<T> {

    /** AWS Enhanced DynamoDB 비동기 클라이언트 */
    private final DynamoDbEnhancedAsyncClient enhancedClient;
    
    /** AWS DynamoDB 비동기 클라이언트 (트랜잭션 작업용) */
    private final DynamoDbAsyncClient rawClient;
    
    /** 테이블명 변환을 담당하는 유틸리티 */
    private final TableNameResolver tableNameResolver;

    /**
     * DefaultDynamoDbService 생성자 (TableNameResolver 포함)
     * 
     * @param enhancedClient AWS Enhanced DynamoDB 비동기 클라이언트 인스턴스
     * @param rawClient AWS DynamoDB 비동기 클라이언트 인스턴스 (트랜잭션용)
     * @param tableNameResolver 테이블명 변환 유틸리티
     */
    public DefaultDynamoDbService(DynamoDbEnhancedAsyncClient enhancedClient, 
                                  DynamoDbAsyncClient rawClient, 
                                  TableNameResolver tableNameResolver) {
        this.enhancedClient = enhancedClient;
        this.rawClient = rawClient;
        this.tableNameResolver = tableNameResolver != null ? tableNameResolver : 
            new TableNameResolver("", ""); // 기본값으로 변환 없음
    }
    
    /**
     * DefaultDynamoDbService 생성자 (하위 호환성)
     * 
     * @param enhancedClient AWS Enhanced DynamoDB 비동기 클라이언트 인스턴스
     * @param rawClient AWS DynamoDB 비동기 클라이언트 인스턴스 (트랜잭션용)
     */
    public DefaultDynamoDbService(DynamoDbEnhancedAsyncClient enhancedClient, DynamoDbAsyncClient rawClient) {
        this(enhancedClient, rawClient, new TableNameResolver("", ""));
    }
    
    /**
     * 하위 호환성을 위한 생성자
     * Enhanced Client만 제공된 경우 null로 초기화하고, 트랜잭션 시에는 예외 발생
     * 
     * @param enhancedClient AWS Enhanced DynamoDB 비동기 클라이언트 인스턴스
     */
    public DefaultDynamoDbService(DynamoDbEnhancedAsyncClient enhancedClient) {
        this(enhancedClient, null, new TableNameResolver("", ""));
    }

    /**
     * 단일 항목을 DynamoDB 테이블에 저장합니다.
     * 
     * @param item 저장할 객체 인스턴스
     * @param tableName 대상 테이블명
     * @return 저장 작업의 완료를 나타내는 CompletableFuture
     * 
     * 사용 예시:
     * <pre>
     * User user = new User("123", "홍길동");
     * dynamoDbService.save(user, "users")
     *     .thenRun(() -> System.out.println("저장 완료"));
     * </pre>
     */
    @Override
    public CompletableFuture<Void> save(T item, String tableName) {
        @SuppressWarnings("unchecked")
        Class<T> itemClass = (Class<T>) item.getClass();
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        return table.putItem(item);
    }

    /**
     * 주어진 키로 DynamoDB에서 단일 항목을 로드합니다.
     * 
     * @param itemClass 반환할 객체의 클래스 타입
     * @param key 조회할 항목의 DynamoDB 키
     * @param tableName 대상 테이블명
     * @return 로드된 객체를 포함하는 CompletableFuture (항목이 없으면 null)
     * 
     * 사용 예시:
     * <pre>
     * DynamoKey key = DynamoKey.of("userId", "123");
     * dynamoDbService.load(User.class, key, "users")
     *     .thenAccept(user -> {
     *         if (user != null) {
     *             System.out.println("사용자 찾음: " + user.getName());
     *         }
     *     });
     * </pre>
     */
    @Override
    public CompletableFuture<T> load(Class<T> itemClass, DynamoKey key, String tableName) {
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        return table.getItem(DynamoTypeAdapter.toAwsKey(key));
    }

    /**
     * 주어진 키로 DynamoDB에서 항목을 삭제합니다.
     * 
     * @param key 삭제할 항목의 DynamoDB 키
     * @param tableName 대상 테이블명
     * @param itemClass 항목의 클래스 타입
     * @return 삭제 작업의 완료를 나타내는 CompletableFuture
     * 
     * 사용 예시:
     * <pre>
     * DynamoKey key = DynamoKey.of("userId", "123");
     * dynamoDbService.delete(key, "users", User.class)
     *     .thenRun(() -> System.out.println("삭제 완료"));
     * </pre>
     */
    @Override
    public CompletableFuture<Void> delete(DynamoKey key, String tableName, Class<T> itemClass) {
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        return table.deleteItem(DynamoTypeAdapter.toAwsKey(key)).thenApply(item -> null);
    }

    /**
     * 주어진 쿠리 조건으로 DynamoDB 테이블을 쿠리합니다.
     * 다중 페이지를 지원하며, 모든 결과를 수집하여 반환합니다.
     * 
     * @param itemClass 반환할 객체의 클래스 타입
     * @param dynamoQuery 쿠리 조건을 포함하는 DynamoQuery 객체
     * @param tableName 대상 테이블명
     * @return 쿠리 결과 리스트를 포함하는 CompletableFuture
     * 
     * 사용 예시:
     * <pre>
     * DynamoQuery query = DynamoQuery.builder()
     *     .partitionKey("status", "ACTIVE")
     *     .sortKeyBeginsWith("name", "A")
     *     .build();
     * dynamoDbService.query(User.class, query, "users")
     *     .thenAccept(users -> System.out.println("찾은 사용자 수: " + users.size()));
     * </pre>
     */
    @Override
    public CompletableFuture<List<T>> query(Class<T> itemClass, DynamoQuery dynamoQuery, String tableName) {
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(DynamoTypeAdapter.toAwsQueryConditional(dynamoQuery))
                .build();
        
        // 모든 페이지를 CompletableFuture로 수집
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        List<T> allItems = new ArrayList<>();
        
        table.query(request)
                .subscribe(page -> allItems.addAll(page.items()))
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    } else {
                        result.complete(allItems);
                    }
                });
        
        return result;
    }

    /**
     * DynamoDB 테이블의 모든 항목을 스캔합니다.
     * 대용량 테이블에서는 비용이 많이 들수 있으므로 주의하여 사용하세요.
     * 
     * @param itemClass 반환할 객체의 클래스 타입
     * @param tableName 대상 테이블명
     * @return 전체 스캔 결과 리스트를 포함하는 CompletableFuture
     * 
     * 사용 예시:
     * <pre>
     * // 주의: 대용량 테이블에서는 매우 비용이 많이 듦
     * dynamoDbService.scan(User.class, "users")
     *     .thenAccept(allUsers -> System.out.println("전체 사용자 수: " + allUsers.size()));
     * </pre>
     */
    @Override
    public CompletableFuture<List<T>> scan(Class<T> itemClass, String tableName) {
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        
        // 모든 페이지를 CompletableFuture로 수집
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        List<T> allItems = new ArrayList<>();
        
        table.scan()
                .subscribe(page -> allItems.addAll(page.items()))
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    } else {
                        result.complete(allItems);
                    }
                });
        
        return result;
    }

    /**
     * 여러 항목을 한 번에 배치로 저장합니다.
     * AWS DynamoDB의 배치 쓰기 제한(25개 항목)을 고려하여 사용하세요.
     * 
     * @param items 저장할 항목들의 리스트
     * @param tableName 대상 테이블명
     * @return 배치 저장 작업의 완료를 나타내는 CompletableFuture
     * 
     * 사용 예시:
     * <pre>
     * List<User> users = Arrays.asList(
     *     new User("1", "Alice"),
     *     new User("2", "Bob"),
     *     new User("3", "Charlie")
     * );
     * dynamoDbService.batchSave(users, "users")
     *     .thenRun(() -> System.out.println("배치 저장 완료"));
     * </pre>
     */
    @Override
    public CompletableFuture<Void> batchSave(List<T> items, String tableName) {
        if (items.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("unchecked")
        Class<T> itemClass = (Class<T>) items.getFirst().getClass();
        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);

        WriteBatch.Builder<T> batchBuilder = WriteBatch.builder(itemClass)
                .mappedTableResource(table);
        
        items.forEach(batchBuilder::addPutItem);

        BatchWriteItemEnhancedRequest request = BatchWriteItemEnhancedRequest.builder()
                .writeBatches(batchBuilder.build())
                .build();

        return enhancedClient.batchWriteItem(request).thenApply(response -> null);
    }

    /**
     * 여러 키로 항목들을 한 번에 배치로 로드합니다.
     * AWS DynamoDB의 배치 읽기 제한(100개 항목)을 고려하여 사용하세요.
     * 
     * @param itemClass 반환할 객체의 클래스 타입
     * @param keys 조회할 항목들의 DynamoDB 키 리스트
     * @param tableName 대상 테이블명
     * @return 배치 로드 결과 리스트를 포함하는 CompletableFuture
     * 
     * 사용 예시:
     * <pre>
     * List<DynamoKey> keys = Arrays.asList(
     *     DynamoKey.of("userId", "1"),
     *     DynamoKey.of("userId", "2"),
     *     DynamoKey.of("userId", "3")
     * );
     * dynamoDbService.batchLoad(User.class, keys, "users")
     *     .thenAccept(users -> System.out.println("조회된 사용자 수: " + users.size()));
     * </pre>
     */
    @Override
    public CompletableFuture<List<T>> batchLoad(Class<T> itemClass, List<DynamoKey> keys, String tableName) {
        if (keys.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        DynamoDbAsyncTable<T> table = getTable(itemClass, tableName);
        ReadBatch.Builder<T> batchBuilder = ReadBatch.builder(itemClass)
                .mappedTableResource(table);

        keys.stream()
                .map(DynamoTypeAdapter::toAwsKey)
                .forEach(batchBuilder::addGetItem);

        BatchGetItemEnhancedRequest request = BatchGetItemEnhancedRequest.builder()
                .readBatches(batchBuilder.build())
                .build();

        // CompletableFuture로 결과 수집
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        List<T> allItems = new ArrayList<>();
        
        enhancedClient.batchGetItem(request)
                .resultsForTable(table)
                .subscribe(allItems::add)
                .whenComplete((unused, throwable) -> {
                    if (throwable != null) {
                        result.completeExceptionally(throwable);
                    } else {
                        result.complete(allItems);
                    }
                });
        
        return result;
    }

    /**
     * DynamoDB 트랜잭션 쓰기 작업을 수행합니다.
     * 여러 테이블에 대한 여러 작업을 원자적으로 수행할 수 있습니다.
     * 
     * <p>이 메소드는 AWS DynamoDB의 TransactWriteItems API를 사용하여
     * 최대 100개의 작업을 원자적으로 실행합니다. 모든 작업이 성공하거나
     * 모든 작업이 실패합니다.</p>
     * 
     * <p><strong>제한사항:</strong></p>
     * <ul>
     *   <li>최대 100개 트랜잭션 아이템</li>
     *   <li>최대 4MB 요청 크기</li>
     *   <li>단일 AWS 리전 내에서만 실행</li>
     * </ul>
     * 
     * @param transaction 실행할 DynamoDB 트랜잭션 객체
     * @return 트랜잭션 수행 완료를 나타내는 CompletableFuture
     * @throws IllegalArgumentException 트랜잭션이 null이거나 비어있는 경우
     * 
     * 사용 예시:
     * <pre>
     * DynamoTransaction transaction = DynamoTransaction.builder()
     *     .put(user1, "users")
     *     .update(user2Key, "users", "SET #status = :status", "ACTIVE")
     *     .delete(user3Key, "users")
     *     .build();
     * 
     * dynamoDbService.transactWrite(transaction)
     *     .thenRun(() -> System.out.println("트랜잭션 완료"))
     *     .exceptionally(throwable -> {
     *         System.err.println("트랜잭션 실패: " + throwable.getMessage());
     *         return null;
     *     });
     * </pre>
     */
    @Override
    public CompletableFuture<Void> transactWrite(DynamoTransaction transaction) {
        // 입력 검증
        if (transaction == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("DynamoTransaction cannot be null")
            );
        }
        
        if (transaction.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("DynamoTransaction cannot be empty")
            );
        }
        
        // DynamoTransaction을 AWS SDK TransactWriteItems로 변환
        TransactWriteItemsRequest request;
        try {
            request = TransactWriteItemsRequest.builder()
                    .transactItems(DynamoTypeAdapter.toAwsTransactWriteItems(transaction))
                    .build();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("Failed to convert transaction to AWS SDK format", e)
            );
        }
        
        // Raw client 가용성 확인
        if (rawClient == null) {
            return CompletableFuture.failedFuture(
                new UnsupportedOperationException(
                    "Transaction operations require raw DynamoDB client. " +
                    "Please configure DynamoDbService with both enhanced and raw clients.")
            );
        }
        
        // 비동기로 트랜잭션 실행
        return rawClient.transactWriteItems(request)
                .thenApply(response -> {
                    // TransactWriteItemsResponse는 성공 시 특별한 정보를 담지 않으므로
                    // 응답을 받으면 성공으로 간주하고 null을 반환
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    // DynamoDB 관련 예외를 더 명확한 메시지와 함께 재발생
                    String errorMessage = "Transaction execution failed";
                    if (throwable.getCause() != null) {
                        errorMessage += ": " + throwable.getCause().getMessage();
                    }
                    
                    throw new RuntimeException(errorMessage, throwable);
                });
    }

    /**
     * 주어진 클래스와 테이블명으로 DynamoDbAsyncTable 인스턴스를 생성합니다.
     * prefix와 suffix가 설정된 경우 자동으로 테이블명을 변환합니다.
     * 
     * @param itemClass 테이블 아이템의 클래스 타입
     * @param tableName 원본 DynamoDB 테이블명
     * @return 설정된 DynamoDbAsyncTable 인스턴스
     */
    private DynamoDbAsyncTable<T> getTable(Class<T> itemClass, String tableName) {
        String resolvedTableName = tableNameResolver.resolve(tableName);
        return enhancedClient.table(resolvedTableName, TableSchema.fromBean(itemClass));
    }
    
    /**
     * 현재 사용 중인 TableNameResolver를 반환합니다.
     * 주로 테스트나 디버깅 목적으로 사용됩니다.
     * 
     * @return 현재 설정된 TableNameResolver 인스턴스
     */
    public TableNameResolver getTableNameResolver() {
        return tableNameResolver;
    }
}