package com.ryuqq.aws.secrets.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerAsyncClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for AWS Secrets Manager with caching support
 */
@Slf4j
public class SecretsService {
    
    private final SecretsManagerAsyncClient secretsManagerClient;
    private final SecretsCacheManager cacheManager;
    private final ObjectMapper objectMapper;
    
    public SecretsService(SecretsManagerAsyncClient secretsManagerClient,
                         SecretsCacheManager cacheManager,
                         ObjectMapper objectMapper) {
        this.secretsManagerClient = secretsManagerClient;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Get secret value as string
     */
    public CompletableFuture<String> getSecret(String secretName) {
        return cacheManager.get(secretName, key -> 
            fetchSecret(key).thenApply(this::extractSecretString)
        );
    }
    
    /**
     * Get secret value as JSON map
     */
    public CompletableFuture<Map<String, Object>> getSecretAsMap(String secretName) {
        return getSecret(secretName)
                .thenApply(this::parseJsonToMap);
    }
    
    /**
     * Get secret value as specific type
     */
    public <T> CompletableFuture<T> getSecret(String secretName, Class<T> valueType) {
        return getSecret(secretName)
                .thenApply(json -> parseJson(json, valueType));
    }
    
    /**
     * Get secret value with version
     */
    public CompletableFuture<String> getSecret(String secretName, String versionId) {
        String cacheKey = secretName + ":" + versionId;
        return cacheManager.get(cacheKey, key -> 
            fetchSecret(secretName, versionId).thenApply(this::extractSecretString)
        );
    }
    
    /**
     * Create a new secret
     */
    public CompletableFuture<String> createSecret(String secretName, String secretValue) {
        CreateSecretRequest request = CreateSecretRequest.builder()
                .name(secretName)
                .secretString(secretValue)
                .build();
        
        return secretsManagerClient.createSecret(request)
                .thenApply(response -> {
                    log.info("Created secret: {} with ARN: {}", secretName, response.arn());
                    cacheManager.invalidate(secretName);
                    return response.arn();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to create secret: {}", secretName, throwable);
                    throw new SecretsException("Failed to create secret", throwable);
                });
    }
    
    /**
     * Create a secret from object (JSON serialization)
     */
    public CompletableFuture<String> createSecret(String secretName, Object secretObject) {
        String secretValue = toJson(secretObject);
        return createSecret(secretName, secretValue);
    }
    
    /**
     * Update an existing secret
     */
    public CompletableFuture<String> updateSecret(String secretName, String secretValue) {
        UpdateSecretRequest request = UpdateSecretRequest.builder()
                .secretId(secretName)
                .secretString(secretValue)
                .build();
        
        return secretsManagerClient.updateSecret(request)
                .thenApply(response -> {
                    log.info("Updated secret: {} with version: {}", secretName, response.versionId());
                    cacheManager.invalidate(secretName);
                    return response.versionId();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to update secret: {}", secretName, throwable);
                    throw new SecretsException("Failed to update secret", throwable);
                });
    }
    
    /**
     * Update secret from object
     */
    public CompletableFuture<String> updateSecret(String secretName, Object secretObject) {
        String secretValue = toJson(secretObject);
        return updateSecret(secretName, secretValue);
    }
    
    /**
     * Delete a secret
     */
    public CompletableFuture<Void> deleteSecret(String secretName, boolean forceDelete) {
        DeleteSecretRequest.Builder requestBuilder = DeleteSecretRequest.builder()
                .secretId(secretName);
        
        if (forceDelete) {
            requestBuilder.forceDeleteWithoutRecovery(true);
        } else {
            requestBuilder.recoveryWindowInDays(30L);
        }
        
        return secretsManagerClient.deleteSecret(requestBuilder.build())
                .thenRun(() -> {
                    log.info("Deleted secret: {}", secretName);
                    cacheManager.invalidate(secretName);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to delete secret: {}", secretName, throwable);
                    throw new SecretsException("Failed to delete secret", throwable);
                });
    }
    
    /**
     * Rotate a secret
     */
    public CompletableFuture<String> rotateSecret(String secretName) {
        RotateSecretRequest request = RotateSecretRequest.builder()
                .secretId(secretName)
                .build();
        
        return secretsManagerClient.rotateSecret(request)
                .thenApply(response -> {
                    log.info("Initiated rotation for secret: {} with version: {}", 
                            secretName, response.versionId());
                    cacheManager.invalidate(secretName);
                    return response.versionId();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to rotate secret: {}", secretName, throwable);
                    throw new SecretsException("Failed to rotate secret", throwable);
                });
    }
    
    /**
     * List all secrets
     */
    public CompletableFuture<ListSecretsResponse> listSecrets() {
        return secretsManagerClient.listSecrets()
                .exceptionally(throwable -> {
                    log.error("Failed to list secrets", throwable);
                    throw new SecretsException("Failed to list secrets", throwable);
                });
    }
    
    /**
     * Invalidate cache for a secret
     */
    public void invalidateCache(String secretName) {
        cacheManager.invalidate(secretName);
        log.debug("Invalidated cache for secret: {}", secretName);
    }
    
    /**
     * Invalidate all cached secrets
     */
    public void invalidateAllCache() {
        cacheManager.invalidateAll();
        log.info("Invalidated all cached secrets");
    }
    
    // Private helper methods
    
    private CompletableFuture<GetSecretValueResponse> fetchSecret(String secretName) {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();
        
        return secretsManagerClient.getSecretValue(request)
                .exceptionally(throwable -> {
                    log.error("Failed to fetch secret: {}", secretName, throwable);
                    throw new SecretsException("Failed to fetch secret", throwable);
                });
    }
    
    private CompletableFuture<GetSecretValueResponse> fetchSecret(String secretName, String versionId) {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .versionId(versionId)
                .build();
        
        return secretsManagerClient.getSecretValue(request)
                .exceptionally(throwable -> {
                    log.error("Failed to fetch secret: {} with version: {}", 
                            secretName, versionId, throwable);
                    throw new SecretsException("Failed to fetch secret", throwable);
                });
    }
    
    private String extractSecretString(GetSecretValueResponse response) {
        if (response.secretString() != null) {
            return response.secretString();
        } else if (response.secretBinary() != null) {
            return response.secretBinary().asUtf8String();
        } else {
            throw new SecretsException("Secret value is empty");
        }
    }
    
    private Map<String, Object> parseJsonToMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Failed to parse JSON to map: {}", json, e);
            throw new SecretsException("Failed to parse secret JSON", e);
        }
    }
    
    private <T> T parseJson(String json, Class<T> valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (Exception e) {
            log.error("Failed to parse JSON to type {}: {}", valueType.getName(), json, e);
            throw new SecretsException("Failed to parse secret JSON", e);
        }
    }
    
    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            log.error("Failed to convert object to JSON", e);
            throw new SecretsException("Failed to convert to JSON", e);
        }
    }
    
    /**
     * Custom exception for Secrets operations
     */
    public static class SecretsException extends RuntimeException {
        public SecretsException(String message) {
            super(message);
        }
        
        public SecretsException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}