package com.ryuqq.aws.dynamodb.example;

import com.ryuqq.aws.dynamodb.service.DynamoDbService;
import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.util.TableNameResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.concurrent.CompletableFuture;

/**
 * 비즈니스 서비스에서 새로 추가된 기능을 사용하는 예제
 * 
 * 이 예제는 다음을 보여줍니다:
 * 1. 자동 테이블명 변환 적용
 * 2. 환경별 테이블 분리
 * 3. TableNameResolver 정보 조회
 * 4. 실제 비즈니스 로직에서의 활용
 */
@Service
public class BusinessServiceExample {

    private final DynamoDbService<User> userService;
    
    public BusinessServiceExample(DynamoDbService<User> userService) {
        this.userService = userService;
    }

    /**
     * 사용자 생성
     * 
     * 설정이 prefix="dev-", suffix="-v1"인 경우:
     * "users" 테이블 → "dev-users-v1" 테이블에 저장됨
     */
    public CompletableFuture<Void> createUser(String userId, String email, String name) {
        User user = new User(userId, email, name);
        
        // 테이블명 "users"를 전달하면 자동으로 prefix/suffix 적용됨
        return userService.save(user, "users")
                .thenRun(() -> System.out.println("사용자가 저장되었습니다: " + userId));
    }

    /**
     * 사용자 조회
     * 
     * 저장 시와 동일한 테이블명 변환이 자동 적용됨
     */
    public CompletableFuture<User> getUser(String userId, String email) {
        DynamoKey key = DynamoKey.sortKey("userId", userId, "email", email);
        
        return userService.load(User.class, key, "users")
                .thenApply(user -> {
                    if (user != null) {
                        System.out.println("사용자를 찾았습니다: " + user.getName());
                    } else {
                        System.out.println("사용자를 찾을 수 없습니다: " + userId);
                    }
                    return user;
                });
    }

    /**
     * 사용자 삭제
     */
    public CompletableFuture<Void> deleteUser(String userId, String email) {
        DynamoKey key = DynamoKey.sortKey("userId", userId, "email", email);
        
        return userService.delete(key, "users", User.class)
                .thenRun(() -> System.out.println("사용자가 삭제되었습니다: " + userId));
    }

    /**
     * 현재 테이블명 변환 설정 확인
     * 
     * 디버깅이나 로깅 목적으로 사용
     */
    public void checkTableNameConfiguration() {
        TableNameResolver resolver = userService.getTableNameResolver();
        
        System.out.println("=== 테이블명 변환 설정 ===");
        System.out.println("Resolver: " + resolver);
        System.out.println("'users' 테이블 실제 이름: " + resolver.resolve("users"));
        System.out.println("'products' 테이블 실제 이름: " + resolver.resolve("products"));
        
        if (resolver.hasNoTransformation()) {
            System.out.println("테이블명 변환이 설정되지 않았습니다.");
        } else {
            System.out.println("접두사: '" + resolver.getTablePrefix() + "'");
            System.out.println("접미사: '" + resolver.getTableSuffix() + "'");
        }
    }

    /**
     * 환경별 동작 확인
     * 
     * 각 환경에서 다른 테이블명이 사용되는지 확인
     */
    public void demonstrateEnvironmentSeparation() {
        TableNameResolver resolver = userService.getTableNameResolver();
        
        System.out.println("=== 환경별 테이블 분리 시연 ===");
        System.out.println("현재 설정으로 변환된 테이블명들:");
        
        String[] tableNames = {
            "users", "products", "orders", "inventory", 
            "sessions", "audit_logs", "configurations"
        };
        
        for (String tableName : tableNames) {
            String resolvedName = resolver.resolve(tableName);
            System.out.println("  " + tableName + " → " + resolvedName);
        }
        
        System.out.println("\n이 설정으로 각 환경의 테이블이 분리되어 운영됩니다:");
        System.out.println("- 개발환경: dev-users-v1, dev-products-v1, ...");
        System.out.println("- 프로덕션: prod-users-v1, prod-products-v1, ...");
    }

    @DynamoDbBean
    public static class User {
        private String userId;
        private String email;
        private String name;

        public User() {}

        public User(String userId, String email, String name) {
            this.userId = userId;
            this.email = email;
            this.name = name;
        }

        @DynamoDbPartitionKey
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        @DynamoDbSortKey
        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "User{userId='" + userId + "', email='" + email + "', name='" + name + "'}";
        }
    }
}