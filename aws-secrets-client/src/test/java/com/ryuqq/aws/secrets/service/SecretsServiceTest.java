package com.ryuqq.aws.secrets.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import com.ryuqq.aws.secrets.service.SecretsService.SecretsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SecretsService Tests")
class SecretsServiceTest {

    @Mock
    private SecretsManagerAsyncClient secretsManagerClient;

    @Mock
    private SecretsCacheManager cacheManager;

    private ObjectMapper objectMapper;
    private SecretsService secretsService;

    private static final String SECRET_NAME = "test-secret";
    private static final String SECRET_VALUE = "secret-value";
    private static final String VERSION_ID = "version-123";
    private static final String ARN = "arn:aws:secretsmanager:us-east-1:123456789012:secret:test-secret-AbCdEf";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        secretsService = new SecretsService(secretsManagerClient, cacheManager, objectMapper);
    }

    @Test
    @DisplayName("Should get secret string from cache when available")
    void shouldGetSecretStringFromCache() {
        // Given
        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenReturn(CompletableFuture.completedFuture(SECRET_VALUE));

        // When
        CompletableFuture<String> result = secretsService.getSecret(SECRET_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(SECRET_VALUE);
        verify(cacheManager).get(eq(SECRET_NAME), any(Function.class));
        verifyNoInteractions(secretsManagerClient);
    }

    @Test
    @DisplayName("Should fetch secret from AWS when cache miss")
    void shouldFetchSecretFromAwsWhenCacheMiss() {
        // Given
        GetSecretValueResponse mockResponse = GetSecretValueResponse.builder()
                .secretString(SECRET_VALUE)
                .build();

        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(SECRET_NAME);
                });

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = secretsService.getSecret(SECRET_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(SECRET_VALUE);

        ArgumentCaptor<GetSecretValueRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetSecretValueRequest.class);
        verify(secretsManagerClient).getSecretValue(requestCaptor.capture());
        assertThat(requestCaptor.getValue().secretId()).isEqualTo(SECRET_NAME);
    }

    @Test
    @DisplayName("Should get secret with binary data")
    void shouldGetSecretWithBinaryData() {
        // Given
        byte[] binaryData = SECRET_VALUE.getBytes();
        GetSecretValueResponse mockResponse = GetSecretValueResponse.builder()
                .secretBinary(SdkBytes.fromByteArray(binaryData))
                .build();

        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(SECRET_NAME);
                });

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = secretsService.getSecret(SECRET_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(SECRET_VALUE);
    }

    @Test
    @DisplayName("Should get secret with version ID")
    void shouldGetSecretWithVersionId() {
        // Given
        String cacheKey = SECRET_NAME + ":" + VERSION_ID;
        GetSecretValueResponse mockResponse = GetSecretValueResponse.builder()
                .secretString(SECRET_VALUE)
                .build();

        when(cacheManager.get(eq(cacheKey), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(cacheKey);
                });

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = secretsService.getSecret(SECRET_NAME, VERSION_ID);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(SECRET_VALUE);

        ArgumentCaptor<GetSecretValueRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetSecretValueRequest.class);
        verify(secretsManagerClient).getSecretValue(requestCaptor.capture());
        assertThat(requestCaptor.getValue().secretId()).isEqualTo(SECRET_NAME);
        assertThat(requestCaptor.getValue().versionId()).isEqualTo(VERSION_ID);
    }

    @Test
    @DisplayName("Should parse secret as JSON map")
    void shouldParseSecretAsJsonMap() {
        // Given
        Map<String, Object> expectedMap = new HashMap<>();
        expectedMap.put("username", "testuser");
        expectedMap.put("password", "testvalue123");
        String jsonValue = "{\"username\":\"testuser\",\"password\":\"testvalue123\"}";

        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenReturn(CompletableFuture.completedFuture(jsonValue));

        // When
        CompletableFuture<Map<String, Object>> result = secretsService.getSecretAsMap(SECRET_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(expectedMap);
    }

    @Test
    @DisplayName("Should parse secret as specific type")
    void shouldParseSecretAsSpecificType() {
        // Given
        TestCredentials expected = new TestCredentials("testuser", "testvalue123");
        String jsonValue = "{\"username\":\"testuser\",\"password\":\"testvalue123\"}";

        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenReturn(CompletableFuture.completedFuture(jsonValue));

        // When
        CompletableFuture<TestCredentials> result = secretsService.getSecret(SECRET_NAME, TestCredentials.class);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .usingRecursiveComparison()
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("Should create secret successfully")
    void shouldCreateSecretSuccessfully() {
        // Given
        CreateSecretResponse mockResponse = CreateSecretResponse.builder()
                .arn(ARN)
                .build();

        when(secretsManagerClient.createSecret(any(CreateSecretRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = secretsService.createSecret(SECRET_NAME, SECRET_VALUE);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(ARN);

        ArgumentCaptor<CreateSecretRequest> requestCaptor = 
                ArgumentCaptor.forClass(CreateSecretRequest.class);
        verify(secretsManagerClient).createSecret(requestCaptor.capture());
        assertThat(requestCaptor.getValue().name()).isEqualTo(SECRET_NAME);
        assertThat(requestCaptor.getValue().secretString()).isEqualTo(SECRET_VALUE);
        
        verify(cacheManager).invalidate(SECRET_NAME);
    }

    @Test
    @DisplayName("Should create secret from object")
    void shouldCreateSecretFromObject() {
        // Given
        TestCredentials credentials = new TestCredentials("admin", "secret123");
        CreateSecretResponse mockResponse = CreateSecretResponse.builder()
                .arn(ARN)
                .build();

        when(secretsManagerClient.createSecret(any(CreateSecretRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = secretsService.createSecret(SECRET_NAME, credentials);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(ARN);

        ArgumentCaptor<CreateSecretRequest> requestCaptor = 
                ArgumentCaptor.forClass(CreateSecretRequest.class);
        verify(secretsManagerClient).createSecret(requestCaptor.capture());
        assertThat(requestCaptor.getValue().secretString()).contains("admin");
        assertThat(requestCaptor.getValue().secretString()).contains("secret123");
    }

    @Test
    @DisplayName("Should update secret successfully")
    void shouldUpdateSecretSuccessfully() {
        // Given
        String newVersionId = "version-456";
        UpdateSecretResponse mockResponse = UpdateSecretResponse.builder()
                .versionId(newVersionId)
                .build();

        when(secretsManagerClient.updateSecret(any(UpdateSecretRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = secretsService.updateSecret(SECRET_NAME, "new-value");

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(newVersionId);

        ArgumentCaptor<UpdateSecretRequest> requestCaptor = 
                ArgumentCaptor.forClass(UpdateSecretRequest.class);
        verify(secretsManagerClient).updateSecret(requestCaptor.capture());
        assertThat(requestCaptor.getValue().secretId()).isEqualTo(SECRET_NAME);
        assertThat(requestCaptor.getValue().secretString()).isEqualTo("new-value");
        
        verify(cacheManager).invalidate(SECRET_NAME);
    }

    @Test
    @DisplayName("Should delete secret with recovery window")
    void shouldDeleteSecretWithRecoveryWindow() {
        // Given
        DeleteSecretResponse mockResponse = DeleteSecretResponse.builder()
                .deletionDate(java.time.Instant.now().plusSeconds(30 * 24 * 60 * 60))
                .build();

        when(secretsManagerClient.deleteSecret(any(DeleteSecretRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Void> result = secretsService.deleteSecret(SECRET_NAME, false);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));

        ArgumentCaptor<DeleteSecretRequest> requestCaptor = 
                ArgumentCaptor.forClass(DeleteSecretRequest.class);
        verify(secretsManagerClient).deleteSecret(requestCaptor.capture());
        assertThat(requestCaptor.getValue().secretId()).isEqualTo(SECRET_NAME);
        assertThat(requestCaptor.getValue().recoveryWindowInDays()).isEqualTo(30L);
        assertThat(requestCaptor.getValue().forceDeleteWithoutRecovery()).isNull();
        
        verify(cacheManager).invalidate(SECRET_NAME);
    }

    @Test
    @DisplayName("Should force delete secret without recovery")
    void shouldForceDeleteSecretWithoutRecovery() {
        // Given
        DeleteSecretResponse mockResponse = DeleteSecretResponse.builder()
                .deletionDate(java.time.Instant.now())
                .build();

        when(secretsManagerClient.deleteSecret(any(DeleteSecretRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Void> result = secretsService.deleteSecret(SECRET_NAME, true);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));

        ArgumentCaptor<DeleteSecretRequest> requestCaptor = 
                ArgumentCaptor.forClass(DeleteSecretRequest.class);
        verify(secretsManagerClient).deleteSecret(requestCaptor.capture());
        assertThat(requestCaptor.getValue().forceDeleteWithoutRecovery()).isTrue();
    }

    @Test
    @DisplayName("Should rotate secret successfully")
    void shouldRotateSecretSuccessfully() {
        // Given
        String rotationVersionId = "rotation-version-789";
        RotateSecretResponse mockResponse = RotateSecretResponse.builder()
                .versionId(rotationVersionId)
                .build();

        when(secretsManagerClient.rotateSecret(any(RotateSecretRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = secretsService.rotateSecret(SECRET_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(rotationVersionId);

        ArgumentCaptor<RotateSecretRequest> requestCaptor = 
                ArgumentCaptor.forClass(RotateSecretRequest.class);
        verify(secretsManagerClient).rotateSecret(requestCaptor.capture());
        assertThat(requestCaptor.getValue().secretId()).isEqualTo(SECRET_NAME);
        
        verify(cacheManager).invalidate(SECRET_NAME);
    }

    @Test
    @DisplayName("Should list secrets successfully")
    void shouldListSecretsSuccessfully() {
        // Given
        ListSecretsResponse mockResponse = ListSecretsResponse.builder()
                .secretList(
                        SecretListEntry.builder().name("secret1").build(),
                        SecretListEntry.builder().name("secret2").build()
                )
                .build();

        when(secretsManagerClient.listSecrets())
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<ListSecretsResponse> result = secretsService.listSecrets();

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .satisfies(response -> {
                    assertThat(response.secretList()).hasSize(2);
                    assertThat(response.secretList().get(0).name()).isEqualTo("secret1");
                    assertThat(response.secretList().get(1).name()).isEqualTo("secret2");
                });
    }

    @Test
    @DisplayName("Should invalidate cache for specific secret")
    void shouldInvalidateCacheForSpecificSecret() {
        // When
        secretsService.invalidateCache(SECRET_NAME);

        // Then
        verify(cacheManager).invalidate(SECRET_NAME);
    }

    @Test
    @DisplayName("Should invalidate all cache")
    void shouldInvalidateAllCache() {
        // When
        secretsService.invalidateAllCache();

        // Then
        verify(cacheManager).invalidateAll();
    }

    @Test
    @DisplayName("Should handle empty secret value")
    void shouldHandleEmptySecretValue() {
        // Given
        GetSecretValueResponse mockResponse = GetSecretValueResponse.builder().build();

        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(SECRET_NAME);
                });

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When & Then
        assertThatThrownBy(() -> secretsService.getSecret(SECRET_NAME).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(SecretsException.class)
                .hasMessageContaining("Secret value is empty");
    }

    @Test
    @DisplayName("Should handle invalid JSON in secret")
    void shouldHandleInvalidJsonInSecret() {
        // Given
        String invalidJson = "invalid-json";
        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenReturn(CompletableFuture.completedFuture(invalidJson));

        // When & Then
        assertThatThrownBy(() -> secretsService.getSecretAsMap(SECRET_NAME).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(SecretsException.class)
                .hasMessageContaining("Failed to parse secret JSON");
    }

    @Test
    @DisplayName("Should handle AWS API errors")
    void shouldHandleAwsApiErrors() {
        // Given
        ResourceNotFoundException awsException = ResourceNotFoundException.builder()
                .message("Secret not found")
                .build();

        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(SECRET_NAME);
                });

        when(secretsManagerClient.getSecretValue(any(GetSecretValueRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(awsException));

        // When & Then
        assertThatThrownBy(() -> secretsService.getSecret(SECRET_NAME).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(SecretsException.class)
                .hasMessageContaining("Failed to fetch secret");
    }

    @Test
    @DisplayName("Should handle concurrent operations correctly")
    void shouldHandleConcurrentOperationsCorrectly() {
        // Given
        int concurrentRequests = 10;
        AtomicInteger callCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        when(cacheManager.get(eq(SECRET_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    callCount.incrementAndGet();
                    return CompletableFuture.completedFuture(SECRET_VALUE);
                });

        // When
        CompletableFuture<String>[] futures = new CompletableFuture[concurrentRequests];
        for (int i = 0; i < concurrentRequests; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> 
                    secretsService.getSecret(SECRET_NAME).join(), executor);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);

        // Then
        assertThat(allFutures).succeedsWithin(java.time.Duration.ofSeconds(5));
        
        for (CompletableFuture<String> future : futures) {
            assertThat(future.join()).isEqualTo(SECRET_VALUE);
        }

        assertThat(callCount.get()).isEqualTo(concurrentRequests);
        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle JSON serialization errors gracefully")
    void shouldHandleJsonSerializationErrorsGracefully() {
        // Given
        Object unserializableObject = new Object() {
            public String getValue() {
                throw new RuntimeException("Cannot serialize");
            }
        };

        // When & Then
        assertThatThrownBy(() -> secretsService.createSecret(SECRET_NAME, unserializableObject))
                .isInstanceOf(SecretsException.class)
                .hasMessageContaining("Failed to convert to JSON");
    }

    // Test helper classes
    public static class TestCredentials {
        public String username;
        public String password;

        public TestCredentials() {}

        public TestCredentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }
}