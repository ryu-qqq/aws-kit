package com.ryuqq.aws.dynamodb.integration;

import com.ryuqq.aws.dynamodb.service.DefaultDynamoDbService;
import com.ryuqq.aws.dynamodb.service.DynamoDbService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.model.CreateTableEnhancedRequest;
import com.ryuqq.aws.dynamodb.types.DynamoKey;
import com.ryuqq.aws.dynamodb.types.DynamoQuery;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DynamoDB service integration tests using LocalStack
 */
@Testcontainers
class DynamoDbServiceIntegrationTest {

    @Container
    static LocalStackContainer localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:2.0"))
            .withServices(Service.DYNAMODB);

    private DynamoDbAsyncClient dynamoDbAsyncClient;
    private DynamoDbEnhancedAsyncClient enhancedClient;
    private DynamoDbService<User> dynamoDbService;

    private static final String TABLE_NAME = "test-users";

    @BeforeEach
    void setUp() throws Exception {
        // Setup DynamoDB client
        dynamoDbAsyncClient = DynamoDbAsyncClient.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test", "test")))
                .endpointOverride(localstack.getEndpoint())
                .overrideConfiguration(ClientOverrideConfiguration.builder().build())
                .build();

        // Setup Enhanced client
        enhancedClient = DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(dynamoDbAsyncClient)
                .build();

        // Setup service
        dynamoDbService = new DefaultDynamoDbService<>(enhancedClient);

        // Create test table
        createTestTable();
    }

    @AfterEach
    void tearDown() {
        // Clean up test data
        try {
            var table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(User.class));
            
            // Scan and delete all items
            List<User> allItems = dynamoDbService.scan(User.class, TABLE_NAME).join();
            for (User item : allItems) {
                DynamoKey key = DynamoKey.sortKey("userId", item.getUserId(), "profileType", item.getProfileType());
                dynamoDbService.delete(key, TABLE_NAME, User.class).join();
            }
        } catch (Exception e) {
            // Ignore cleanup errors
        }
        
        if (dynamoDbAsyncClient != null) {
            dynamoDbAsyncClient.close();
        }
    }

    @Test
    void saveAndLoad_ShouldWorkCorrectly() {
        // Given
        User user = new User("user1", "profile", "John Doe", "john@example.com");

        // When - Save
        CompletableFuture<Void> saveResult = dynamoDbService.save(user, TABLE_NAME);
        saveResult.join();

        // When - Load
        DynamoKey key = DynamoKey.sortKey("userId", "user1", "profileType", "profile");
        CompletableFuture<User> loadResult = dynamoDbService.load(User.class, key, TABLE_NAME);
        User loadedUser = loadResult.join();

        // Then
        assertThat(loadedUser).isNotNull();
        assertThat(loadedUser.getUserId()).isEqualTo("user1");
        assertThat(loadedUser.getProfileType()).isEqualTo("profile");
        assertThat(loadedUser.getName()).isEqualTo("John Doe");
        assertThat(loadedUser.getEmail()).isEqualTo("john@example.com");
    }

    @Test
    void query_ShouldReturnMatchingItems() {
        // Given
        User user1 = new User("user1", "profile", "John Doe", "john@example.com");
        User user2 = new User("user1", "settings", "John Settings", "john@example.com");
        User user3 = new User("user2", "profile", "Jane Doe", "jane@example.com");

        // Save users
        dynamoDbService.save(user1, TABLE_NAME).join();
        dynamoDbService.save(user2, TABLE_NAME).join();
        dynamoDbService.save(user3, TABLE_NAME).join();

        // When
        DynamoQuery dynamoQuery = DynamoQuery.keyEqual("userId", "user1");
        CompletableFuture<List<User>> queryResult = dynamoDbService.query(User.class, dynamoQuery, TABLE_NAME);
        List<User> users = queryResult.join();

        // Then
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getUserId).containsOnly("user1");
        assertThat(users).extracting(User::getProfileType).containsExactlyInAnyOrder("profile", "settings");
    }

    @Test
    void scan_ShouldReturnAllItems() {
        // Given
        User user1 = new User("user1", "profile", "John Doe", "john@example.com");
        User user2 = new User("user2", "profile", "Jane Doe", "jane@example.com");

        // Save users
        dynamoDbService.save(user1, TABLE_NAME).join();
        dynamoDbService.save(user2, TABLE_NAME).join();

        // When
        CompletableFuture<List<User>> scanResult = dynamoDbService.scan(User.class, TABLE_NAME);
        List<User> users = scanResult.join();

        // Then
        assertThat(users).hasSize(2);
        assertThat(users).extracting(User::getUserId).containsExactlyInAnyOrder("user1", "user2");
    }

    @Test
    void delete_ShouldRemoveItem() {
        // Given
        User user = new User("user1", "profile", "John Doe", "john@example.com");
        dynamoDbService.save(user, TABLE_NAME).join();

        // Verify exists
        DynamoKey key = DynamoKey.sortKey("userId", "user1", "profileType", "profile");
        User existingUser = dynamoDbService.load(User.class, key, TABLE_NAME).join();
        assertThat(existingUser).isNotNull();

        // When
        CompletableFuture<Void> deleteResult = dynamoDbService.delete(key, TABLE_NAME, User.class);
        deleteResult.join();

        // Then
        User deletedUser = dynamoDbService.load(User.class, key, TABLE_NAME).join();
        assertThat(deletedUser).isNull();
    }

    @Test
    void batchSave_ShouldSaveMultipleItems() {
        // Given
        List<User> users = List.of(
                new User("batch1", "profile", "Batch User 1", "batch1@example.com"),
                new User("batch2", "profile", "Batch User 2", "batch2@example.com"),
                new User("batch3", "profile", "Batch User 3", "batch3@example.com")
        );

        // When
        CompletableFuture<Void> batchResult = dynamoDbService.batchSave(users, TABLE_NAME);
        batchResult.join();

        // Then - Verify all users were saved
        for (User user : users) {
            DynamoKey key = DynamoKey.sortKey("userId", user.getUserId(), "profileType", user.getProfileType());
            User savedUser = dynamoDbService.load(User.class, key, TABLE_NAME).join();
            assertThat(savedUser).isNotNull();
            assertThat(savedUser.getName()).isEqualTo(user.getName());
        }
    }

    @Test
    void batchLoad_ShouldLoadMultipleItems() {
        // Given
        List<User> users = List.of(
                new User("load1", "profile", "Load User 1", "load1@example.com"),
                new User("load2", "profile", "Load User 2", "load2@example.com")
        );

        // Save users first
        dynamoDbService.batchSave(users, TABLE_NAME).join();

        List<DynamoKey> keys = List.of(
                DynamoKey.sortKey("userId", "load1", "profileType", "profile"),
                DynamoKey.sortKey("userId", "load2", "profileType", "profile")
        );

        // When
        CompletableFuture<List<User>> batchLoadResult = dynamoDbService.batchLoad(User.class, keys, TABLE_NAME);
        List<User> loadedUsers = batchLoadResult.join();

        // Then
        assertThat(loadedUsers).hasSize(2);
        assertThat(loadedUsers).extracting(User::getUserId).containsExactlyInAnyOrder("load1", "load2");
    }

    private void createTestTable() {
        var table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(User.class));
        
        try {
            CreateTableEnhancedRequest request = CreateTableEnhancedRequest.builder()
                    .build();
            
            table.createTable(request).join();
        } catch (Exception e) {
            // Table might already exist, ignore
            if (!e.getMessage().contains("Table already exists")) {
                throw e;
            }
        }
    }

    @DynamoDbBean
    public static class User {
        private String userId;
        private String profileType;
        private String name;
        private String email;

        public User() {}

        public User(String userId, String profileType, String name, String email) {
            this.userId = userId;
            this.profileType = profileType;
            this.name = name;
            this.email = email;
        }

        @DynamoDbPartitionKey
        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        @DynamoDbSortKey
        public String getProfileType() {
            return profileType;
        }

        public void setProfileType(String profileType) {
            this.profileType = profileType;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}