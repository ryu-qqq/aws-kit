package com.ryuqq.aws.secrets.service;

import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import com.ryuqq.aws.secrets.service.ParameterStoreService.ParameterStoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.ssm.SsmAsyncClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.time.Instant;
import java.util.*;
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
@DisplayName("ParameterStoreService Tests")
class ParameterStoreServiceTest {

    @Mock
    private SsmAsyncClient ssmClient;

    @Mock
    private SecretsCacheManager cacheManager;

    private ParameterStoreService parameterStoreService;

    private static final String PARAMETER_NAME = "/app/database/host";
    private static final String PARAMETER_VALUE = "localhost";
    private static final String PARAMETER_PATH = "/app/database/";
    private static final String CACHE_KEY_PREFIX = "param:";

    @BeforeEach
    void setUp() {
        parameterStoreService = new ParameterStoreService(ssmClient, cacheManager);
    }

    @Test
    @DisplayName("Should get parameter from cache when available")
    void shouldGetParameterFromCache() {
        // Given
        when(cacheManager.get(eq(CACHE_KEY_PREFIX + PARAMETER_NAME), any(Function.class)))
                .thenReturn(CompletableFuture.completedFuture(PARAMETER_VALUE));

        // When
        CompletableFuture<String> result = parameterStoreService.getParameter(PARAMETER_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(PARAMETER_VALUE);
        verify(cacheManager).get(eq(CACHE_KEY_PREFIX + PARAMETER_NAME), any(Function.class));
        verifyNoInteractions(ssmClient);
    }

    @Test
    @DisplayName("Should fetch parameter from SSM when cache miss")
    void shouldFetchParameterFromSsmWhenCacheMiss() {
        // Given
        Parameter parameter = Parameter.builder()
                .name(PARAMETER_NAME)
                .value(PARAMETER_VALUE)
                .type(ParameterType.STRING)
                .build();
        
        GetParameterResponse mockResponse = GetParameterResponse.builder()
                .parameter(parameter)
                .build();

        when(cacheManager.get(eq(CACHE_KEY_PREFIX + PARAMETER_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(CACHE_KEY_PREFIX + PARAMETER_NAME);
                });

        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = parameterStoreService.getParameter(PARAMETER_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(PARAMETER_VALUE);

        ArgumentCaptor<GetParameterRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetParameterRequest.class);
        verify(ssmClient).getParameter(requestCaptor.capture());
        assertThat(requestCaptor.getValue().name()).isEqualTo(PARAMETER_NAME);
        assertThat(requestCaptor.getValue().withDecryption()).isFalse();
    }

    @Test
    @DisplayName("Should get parameter with decryption")
    void shouldGetParameterWithDecryption() {
        // Given
        Parameter parameter = Parameter.builder()
                .name(PARAMETER_NAME)
                .value(PARAMETER_VALUE)
                .type(ParameterType.SECURE_STRING)
                .build();
        
        GetParameterResponse mockResponse = GetParameterResponse.builder()
                .parameter(parameter)
                .build();

        when(cacheManager.get(eq(CACHE_KEY_PREFIX + PARAMETER_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(CACHE_KEY_PREFIX + PARAMETER_NAME);
                });

        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<String> result = parameterStoreService.getParameter(PARAMETER_NAME, true);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(PARAMETER_VALUE);

        ArgumentCaptor<GetParameterRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetParameterRequest.class);
        verify(ssmClient).getParameter(requestCaptor.capture());
        assertThat(requestCaptor.getValue().withDecryption()).isTrue();
    }

    @Test
    @DisplayName("Should get multiple parameters")
    void shouldGetMultipleParameters() {
        // Given
        List<String> parameterNames = Arrays.asList("/app/db/host", "/app/db/port", "/app/db/name");
        List<Parameter> parameters = Arrays.asList(
                Parameter.builder().name("/app/db/host").value("localhost").build(),
                Parameter.builder().name("/app/db/port").value("5432").build(),
                Parameter.builder().name("/app/db/name").value("mydb").build()
        );

        GetParametersResponse mockResponse = GetParametersResponse.builder()
                .parameters(parameters)
                .build();

        when(ssmClient.getParameters(any(GetParametersRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Map<String, String>> result = parameterStoreService.getParameters(parameterNames);

        // Then
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("/app/db/host", "localhost");
        expectedMap.put("/app/db/port", "5432");
        expectedMap.put("/app/db/name", "mydb");

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(expectedMap);

        ArgumentCaptor<GetParametersRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetParametersRequest.class);
        verify(ssmClient).getParameters(requestCaptor.capture());
        assertThat(requestCaptor.getValue().names()).containsExactlyInAnyOrderElementsOf(parameterNames);
        assertThat(requestCaptor.getValue().withDecryption()).isFalse();
    }

    @Test
    @DisplayName("Should get multiple parameters with decryption")
    void shouldGetMultipleParametersWithDecryption() {
        // Given
        List<String> parameterNames = Arrays.asList("/app/secret/key", "/app/secret/token");
        List<Parameter> parameters = Arrays.asList(
                Parameter.builder().name("/app/secret/key").value("secret123").type(ParameterType.SECURE_STRING).build(),
                Parameter.builder().name("/app/secret/token").value("token456").type(ParameterType.SECURE_STRING).build()
        );

        GetParametersResponse mockResponse = GetParametersResponse.builder()
                .parameters(parameters)
                .build();

        when(ssmClient.getParameters(any(GetParametersRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Map<String, String>> result = parameterStoreService.getParameters(parameterNames, true);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        assertThat(result.join())
                .hasSize(2)
                .containsEntry("/app/secret/key", "secret123")
                .containsEntry("/app/secret/token", "token456");

        ArgumentCaptor<GetParametersRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetParametersRequest.class);
        verify(ssmClient).getParameters(requestCaptor.capture());
        assertThat(requestCaptor.getValue().withDecryption()).isTrue();
    }

    @Test
    @DisplayName("Should get parameters by path")
    void shouldGetParametersByPath() {
        // Given
        List<Parameter> parameters = Arrays.asList(
                Parameter.builder().name("/app/database/host").value("localhost").build(),
                Parameter.builder().name("/app/database/port").value("5432").build(),
                Parameter.builder().name("/app/database/name").value("mydb").build()
        );

        GetParametersByPathResponse mockResponse = GetParametersByPathResponse.builder()
                .parameters(parameters)
                .build();

        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Map<String, String>> result = parameterStoreService.getParametersByPath(PARAMETER_PATH);

        // Then
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put("/app/database/host", "localhost");
        expectedMap.put("/app/database/port", "5432");
        expectedMap.put("/app/database/name", "mydb");

        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(expectedMap);

        ArgumentCaptor<GetParametersByPathRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetParametersByPathRequest.class);
        verify(ssmClient).getParametersByPath(requestCaptor.capture());
        assertThat(requestCaptor.getValue().path()).isEqualTo(PARAMETER_PATH);
        assertThat(requestCaptor.getValue().recursive()).isFalse();
        assertThat(requestCaptor.getValue().withDecryption()).isFalse();
    }

    @Test
    @DisplayName("Should get parameters by path with recursive and decryption")
    void shouldGetParametersByPathWithRecursiveAndDecryption() {
        // Given
        List<Parameter> parameters = Arrays.asList(
                Parameter.builder().name("/app/database/config/host").value("localhost").build(),
                Parameter.builder().name("/app/database/secrets/password").value("secret123").build()
        );

        GetParametersByPathResponse mockResponse = GetParametersByPathResponse.builder()
                .parameters(parameters)
                .build();

        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Map<String, String>> result = 
                parameterStoreService.getParametersByPath(PARAMETER_PATH, true, true);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));
        assertThat(result.join())
                .hasSize(2)
                .containsEntry("/app/database/config/host", "localhost")
                .containsEntry("/app/database/secrets/password", "secret123");

        ArgumentCaptor<GetParametersByPathRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetParametersByPathRequest.class);
        verify(ssmClient).getParametersByPath(requestCaptor.capture());
        assertThat(requestCaptor.getValue().recursive()).isTrue();
        assertThat(requestCaptor.getValue().withDecryption()).isTrue();
    }

    @Test
    @DisplayName("Should put parameter successfully")
    void shouldPutParameterSuccessfully() {
        // Given
        Long version = 1L;
        PutParameterResponse mockResponse = PutParameterResponse.builder()
                .version(version)
                .build();

        when(ssmClient.putParameter(any(PutParameterRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Long> result = parameterStoreService.putParameter(
                PARAMETER_NAME, PARAMETER_VALUE, ParameterType.STRING);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(version);

        ArgumentCaptor<PutParameterRequest> requestCaptor = 
                ArgumentCaptor.forClass(PutParameterRequest.class);
        verify(ssmClient).putParameter(requestCaptor.capture());
        assertThat(requestCaptor.getValue().name()).isEqualTo(PARAMETER_NAME);
        assertThat(requestCaptor.getValue().value()).isEqualTo(PARAMETER_VALUE);
        assertThat(requestCaptor.getValue().type()).isEqualTo(ParameterType.STRING);
        assertThat(requestCaptor.getValue().overwrite()).isFalse();

        verify(cacheManager).invalidate(CACHE_KEY_PREFIX + PARAMETER_NAME);
    }

    @Test
    @DisplayName("Should put parameter with overwrite")
    void shouldPutParameterWithOverwrite() {
        // Given
        Long version = 2L;
        PutParameterResponse mockResponse = PutParameterResponse.builder()
                .version(version)
                .build();

        when(ssmClient.putParameter(any(PutParameterRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Long> result = parameterStoreService.putParameter(
                PARAMETER_NAME, PARAMETER_VALUE, ParameterType.STRING, true);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(version);

        ArgumentCaptor<PutParameterRequest> requestCaptor = 
                ArgumentCaptor.forClass(PutParameterRequest.class);
        verify(ssmClient).putParameter(requestCaptor.capture());
        assertThat(requestCaptor.getValue().overwrite()).isTrue();
    }

    @Test
    @DisplayName("Should put secure parameter")
    void shouldPutSecureParameter() {
        // Given
        Long version = 1L;
        PutParameterResponse mockResponse = PutParameterResponse.builder()
                .version(version)
                .build();

        when(ssmClient.putParameter(any(PutParameterRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Long> result = parameterStoreService.putSecureParameter(PARAMETER_NAME, "secret-value");

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .isEqualTo(version);

        ArgumentCaptor<PutParameterRequest> requestCaptor = 
                ArgumentCaptor.forClass(PutParameterRequest.class);
        verify(ssmClient).putParameter(requestCaptor.capture());
        assertThat(requestCaptor.getValue().type()).isEqualTo(ParameterType.SECURE_STRING);
        assertThat(requestCaptor.getValue().overwrite()).isTrue();
    }

    @Test
    @DisplayName("Should delete parameter successfully")
    void shouldDeleteParameterSuccessfully() {
        // Given
        DeleteParameterResponse mockResponse = DeleteParameterResponse.builder().build();

        when(ssmClient.deleteParameter(any(DeleteParameterRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Void> result = parameterStoreService.deleteParameter(PARAMETER_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));

        ArgumentCaptor<DeleteParameterRequest> requestCaptor = 
                ArgumentCaptor.forClass(DeleteParameterRequest.class);
        verify(ssmClient).deleteParameter(requestCaptor.capture());
        assertThat(requestCaptor.getValue().name()).isEqualTo(PARAMETER_NAME);

        verify(cacheManager).invalidate(CACHE_KEY_PREFIX + PARAMETER_NAME);
    }

    @Test
    @DisplayName("Should delete multiple parameters")
    void shouldDeleteMultipleParameters() {
        // Given
        List<String> parameterNames = Arrays.asList("/app/param1", "/app/param2", "/app/param3");
        List<String> deletedParameters = Arrays.asList("/app/param1", "/app/param2");
        List<String> invalidParameters = Arrays.asList("/app/param3");

        DeleteParametersResponse mockResponse = DeleteParametersResponse.builder()
                .deletedParameters(deletedParameters)
                .invalidParameters(invalidParameters)
                .build();

        when(ssmClient.deleteParameters(any(DeleteParametersRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<DeleteParametersResponse> result = parameterStoreService.deleteParameters(parameterNames);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .satisfies(response -> {
                    assertThat(response.deletedParameters()).containsExactlyElementsOf(deletedParameters);
                    assertThat(response.invalidParameters()).containsExactlyElementsOf(invalidParameters);
                });

        ArgumentCaptor<DeleteParametersRequest> requestCaptor = 
                ArgumentCaptor.forClass(DeleteParametersRequest.class);
        verify(ssmClient).deleteParameters(requestCaptor.capture());
        assertThat(requestCaptor.getValue().names()).containsExactlyInAnyOrderElementsOf(parameterNames);

        // Verify cache invalidation for all parameters
        parameterNames.forEach(name -> verify(cacheManager).invalidate(CACHE_KEY_PREFIX + name));
    }

    @Test
    @DisplayName("Should get parameter history")
    void shouldGetParameterHistory() {
        // Given
        List<ParameterHistory> histories = Arrays.asList(
                ParameterHistory.builder()
                        .name(PARAMETER_NAME)
                        .value("old-value")
                        .version(1L)
                        .lastModifiedDate(Instant.now().minusSeconds(3600))
                        .build(),
                ParameterHistory.builder()
                        .name(PARAMETER_NAME)
                        .value(PARAMETER_VALUE)
                        .version(2L)
                        .lastModifiedDate(Instant.now())
                        .build()
        );

        GetParameterHistoryResponse mockResponse = GetParameterHistoryResponse.builder()
                .parameters(histories)
                .build();

        when(ssmClient.getParameterHistory(any(GetParameterHistoryRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<List<ParameterHistory>> result = 
                parameterStoreService.getParameterHistory(PARAMETER_NAME);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .satisfies(historyList -> {
                    assertThat(historyList).hasSize(2);
                    assertThat(historyList.get(0).version()).isEqualTo(1L);
                    assertThat(historyList.get(0).value()).isEqualTo("old-value");
                    assertThat(historyList.get(1).version()).isEqualTo(2L);
                    assertThat(historyList.get(1).value()).isEqualTo(PARAMETER_VALUE);
                });

        ArgumentCaptor<GetParameterHistoryRequest> requestCaptor = 
                ArgumentCaptor.forClass(GetParameterHistoryRequest.class);
        verify(ssmClient).getParameterHistory(requestCaptor.capture());
        assertThat(requestCaptor.getValue().name()).isEqualTo(PARAMETER_NAME);
    }

    @Test
    @DisplayName("Should add tags to parameter")
    void shouldAddTagsToParameter() {
        // Given
        Map<String, String> tags = new HashMap<>();
        tags.put("Environment", "production");
        tags.put("Team", "backend");

        AddTagsToResourceResponse mockResponse = AddTagsToResourceResponse.builder().build();

        when(ssmClient.addTagsToResource(any(AddTagsToResourceRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Void> result = parameterStoreService.addTagsToParameter(PARAMETER_NAME, tags);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1));

        ArgumentCaptor<AddTagsToResourceRequest> requestCaptor = 
                ArgumentCaptor.forClass(AddTagsToResourceRequest.class);
        verify(ssmClient).addTagsToResource(requestCaptor.capture());
        
        AddTagsToResourceRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.resourceType()).isEqualTo(ResourceTypeForTagging.PARAMETER);
        assertThat(capturedRequest.resourceId()).isEqualTo(PARAMETER_NAME);
        assertThat(capturedRequest.tags()).hasSize(2);
        
        Map<String, String> capturedTags = capturedRequest.tags().stream()
                .collect(java.util.stream.Collectors.toMap(Tag::key, Tag::value));
        assertThat(capturedTags).isEqualTo(tags);
    }

    @Test
    @DisplayName("Should invalidate cache for parameter")
    void shouldInvalidateCacheForParameter() {
        // When
        parameterStoreService.invalidateCache(PARAMETER_NAME);

        // Then
        verify(cacheManager).invalidate(CACHE_KEY_PREFIX + PARAMETER_NAME);
    }

    @Test
    @DisplayName("Should handle parameter not found error")
    void shouldHandleParameterNotFoundError() {
        // Given
        ParameterNotFoundException notFoundException = ParameterNotFoundException.builder()
                .message("Parameter not found")
                .build();

        when(cacheManager.get(eq(CACHE_KEY_PREFIX + PARAMETER_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(CACHE_KEY_PREFIX + PARAMETER_NAME);
                });

        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(notFoundException));

        // When & Then
        assertThatThrownBy(() -> parameterStoreService.getParameter(PARAMETER_NAME).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ParameterStoreException.class)
                .hasMessageContaining("Failed to fetch parameter");
    }

    @Test
    @DisplayName("Should handle invalid parameter error when getting multiple")
    void shouldHandleInvalidParameterErrorWhenGettingMultiple() {
        // Given
        List<String> parameterNames = Arrays.asList("/valid/param", "/invalid/param");
        InvalidKeyIdException invalidKeyException = InvalidKeyIdException.builder()
                .message("Invalid parameter")
                .build();

        when(ssmClient.getParameters(any(GetParametersRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(invalidKeyException));

        // When & Then
        assertThatThrownBy(() -> parameterStoreService.getParameters(parameterNames).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ParameterStoreException.class)
                .hasMessageContaining("Failed to get parameters");
    }

    @Test
    @DisplayName("Should handle access denied error when putting parameter")
    void shouldHandleAccessDeniedErrorWhenPuttingParameter() {
        // Given
        ParameterAlreadyExistsException alreadyExistsException = ParameterAlreadyExistsException.builder()
                .message("Parameter already exists")
                .build();

        when(ssmClient.putParameter(any(PutParameterRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(alreadyExistsException));

        // When & Then
        assertThatThrownBy(() -> parameterStoreService.putParameter(
                PARAMETER_NAME, PARAMETER_VALUE, ParameterType.STRING).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(ParameterStoreException.class)
                .hasMessageContaining("Failed to put parameter");
    }

    @Test
    @DisplayName("Should handle concurrent parameter operations")
    void shouldHandleConcurrentParameterOperations() {
        // Given
        int concurrentRequests = 10;
        AtomicInteger callCount = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        when(cacheManager.get(eq(CACHE_KEY_PREFIX + PARAMETER_NAME), any(Function.class)))
                .thenAnswer(invocation -> {
                    callCount.incrementAndGet();
                    return CompletableFuture.completedFuture(PARAMETER_VALUE);
                });

        // When
        CompletableFuture<String>[] futures = new CompletableFuture[concurrentRequests];
        for (int i = 0; i < concurrentRequests; i++) {
            futures[i] = CompletableFuture.supplyAsync(() -> 
                    parameterStoreService.getParameter(PARAMETER_NAME).join(), executor);
        }

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(futures);

        // Then
        assertThat(allFutures).succeedsWithin(java.time.Duration.ofSeconds(5));
        
        for (CompletableFuture<String> future : futures) {
            assertThat(future.join()).isEqualTo(PARAMETER_VALUE);
        }

        assertThat(callCount.get()).isEqualTo(concurrentRequests);
        executor.shutdown();
    }

    @Test
    @DisplayName("Should handle empty parameter list gracefully")
    void shouldHandleEmptyParameterListGracefully() {
        // Given
        List<String> emptyList = Collections.emptyList();
        GetParametersResponse mockResponse = GetParametersResponse.builder()
                .parameters(Collections.emptyList())
                .build();

        when(ssmClient.getParameters(any(GetParametersRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Map<String, String>> result = parameterStoreService.getParameters(emptyList);

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .satisfies(map -> assertThat(map).isEmpty());
    }

    @Test
    @DisplayName("Should handle parameters by path with no results")
    void shouldHandleParametersByPathWithNoResults() {
        // Given
        GetParametersByPathResponse mockResponse = GetParametersByPathResponse.builder()
                .parameters(Collections.emptyList())
                .build();

        when(ssmClient.getParametersByPath(any(GetParametersByPathRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        // When
        CompletableFuture<Map<String, String>> result = 
                parameterStoreService.getParametersByPath("/nonexistent/path/");

        // Then
        assertThat(result).succeedsWithin(java.time.Duration.ofSeconds(1))
                .satisfies(map -> assertThat(map).isEmpty());
    }

    @Test
    @DisplayName("Should handle batch parameter operations")
    void shouldHandleBatchParameterOperations() {
        // Given
        List<String> parameterNames = Arrays.asList("/batch/param1", "/batch/param2", "/batch/param3");
        
        // Mock cache misses for all parameters
        when(cacheManager.get(anyString(), any(Function.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0);
                    Function<String, CompletableFuture<String>> loader = invocation.getArgument(1);
                    return loader.apply(key);
                });

        // Mock individual parameter fetches
        when(ssmClient.getParameter(any(GetParameterRequest.class)))
                .thenAnswer(invocation -> {
                    GetParameterRequest request = invocation.getArgument(0);
                    Parameter parameter = Parameter.builder()
                            .name(request.name())
                            .value("value-for-" + request.name())
                            .build();
                    return CompletableFuture.completedFuture(
                            GetParameterResponse.builder().parameter(parameter).build());
                });

        // When - Execute batch operations
        List<CompletableFuture<String>> futures = parameterNames.stream()
                .map(name -> parameterStoreService.getParameter(name))
                .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        // Then
        assertThat(allFutures).succeedsWithin(java.time.Duration.ofSeconds(5));
        
        for (int i = 0; i < parameterNames.size(); i++) {
            String expectedValue = "value-for-" + parameterNames.get(i);
            assertThat(futures.get(i).join()).isEqualTo(expectedValue);
        }
    }
}