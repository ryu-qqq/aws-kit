package com.ryuqq.aws.secrets.service;

import com.ryuqq.aws.secrets.cache.SecretsCacheManager;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ssm.SsmAsyncClient;
import software.amazon.awssdk.services.ssm.model.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for AWS Systems Manager Parameter Store
 */
@Slf4j
public class ParameterStoreService {
    
    private final SsmAsyncClient ssmClient;
    private final SecretsCacheManager cacheManager;
    
    public ParameterStoreService(SsmAsyncClient ssmClient, SecretsCacheManager cacheManager) {
        this.ssmClient = ssmClient;
        this.cacheManager = cacheManager;
    }
    
    /**
     * Get parameter value
     */
    public CompletableFuture<String> getParameter(String parameterName) {
        return getParameter(parameterName, false);
    }
    
    /**
     * Get parameter value with decryption option
     */
    public CompletableFuture<String> getParameter(String parameterName, boolean withDecryption) {
        String cacheKey = "param:" + parameterName;
        
        return cacheManager.get(cacheKey, key -> 
            fetchParameter(parameterName, withDecryption)
                .thenApply(response -> response.parameter().value())
        );
    }
    
    /**
     * Get multiple parameters
     */
    public CompletableFuture<Map<String, String>> getParameters(List<String> parameterNames) {
        return getParameters(parameterNames, false);
    }
    
    /**
     * Get multiple parameters with decryption
     */
    public CompletableFuture<Map<String, String>> getParameters(
            List<String> parameterNames, boolean withDecryption) {
        
        GetParametersRequest request = GetParametersRequest.builder()
                .names(parameterNames)
                .withDecryption(withDecryption)
                .build();
        
        return ssmClient.getParameters(request)
                .thenApply(response -> 
                    response.parameters().stream()
                            .collect(Collectors.toMap(
                                    Parameter::name,
                                    Parameter::value
                            ))
                )
                .exceptionally(throwable -> {
                    log.error("Failed to get parameters: {}", parameterNames, throwable);
                    throw new ParameterStoreException("Failed to get parameters", throwable);
                });
    }
    
    /**
     * Get parameters by path
     */
    public CompletableFuture<Map<String, String>> getParametersByPath(String path) {
        return getParametersByPath(path, false, false);
    }
    
    /**
     * Get parameters by path with options
     */
    public CompletableFuture<Map<String, String>> getParametersByPath(
            String path, boolean recursive, boolean withDecryption) {
        
        GetParametersByPathRequest request = GetParametersByPathRequest.builder()
                .path(path)
                .recursive(recursive)
                .withDecryption(withDecryption)
                .build();
        
        return ssmClient.getParametersByPath(request)
                .thenApply(response -> 
                    response.parameters().stream()
                            .collect(Collectors.toMap(
                                    Parameter::name,
                                    Parameter::value
                            ))
                )
                .exceptionally(throwable -> {
                    log.error("Failed to get parameters by path: {}", path, throwable);
                    throw new ParameterStoreException("Failed to get parameters by path", throwable);
                });
    }
    
    /**
     * Put parameter (create or update)
     */
    public CompletableFuture<Long> putParameter(String parameterName, String value, ParameterType type) {
        return putParameter(parameterName, value, type, false);
    }
    
    /**
     * Put parameter with overwrite option
     */
    public CompletableFuture<Long> putParameter(
            String parameterName, String value, ParameterType type, boolean overwrite) {
        
        PutParameterRequest request = PutParameterRequest.builder()
                .name(parameterName)
                .value(value)
                .type(type)
                .overwrite(overwrite)
                .build();
        
        return ssmClient.putParameter(request)
                .thenApply(response -> {
                    log.info("Put parameter: {} with version: {}", parameterName, response.version());
                    cacheManager.invalidate("param:" + parameterName);
                    return response.version();
                })
                .exceptionally(throwable -> {
                    log.error("Failed to put parameter: {}", parameterName, throwable);
                    throw new ParameterStoreException("Failed to put parameter", throwable);
                });
    }
    
    /**
     * Put secure string parameter
     */
    public CompletableFuture<Long> putSecureParameter(String parameterName, String value) {
        return putParameter(parameterName, value, ParameterType.SECURE_STRING, true);
    }
    
    /**
     * Delete parameter
     */
    public CompletableFuture<Void> deleteParameter(String parameterName) {
        DeleteParameterRequest request = DeleteParameterRequest.builder()
                .name(parameterName)
                .build();
        
        return ssmClient.deleteParameter(request)
                .thenRun(() -> {
                    log.info("Deleted parameter: {}", parameterName);
                    cacheManager.invalidate("param:" + parameterName);
                })
                .exceptionally(throwable -> {
                    log.error("Failed to delete parameter: {}", parameterName, throwable);
                    throw new ParameterStoreException("Failed to delete parameter", throwable);
                });
    }
    
    /**
     * Delete multiple parameters
     */
    public CompletableFuture<DeleteParametersResponse> deleteParameters(List<String> parameterNames) {
        DeleteParametersRequest request = DeleteParametersRequest.builder()
                .names(parameterNames)
                .build();
        
        return ssmClient.deleteParameters(request)
                .thenApply(response -> {
                    log.info("Deleted {} parameters", response.deletedParameters().size());
                    parameterNames.forEach(name -> cacheManager.invalidate("param:" + name));
                    return response;
                })
                .exceptionally(throwable -> {
                    log.error("Failed to delete parameters: {}", parameterNames, throwable);
                    throw new ParameterStoreException("Failed to delete parameters", throwable);
                });
    }
    
    /**
     * Get parameter history
     */
    public CompletableFuture<List<ParameterHistory>> getParameterHistory(String parameterName) {
        GetParameterHistoryRequest request = GetParameterHistoryRequest.builder()
                .name(parameterName)
                .build();
        
        return ssmClient.getParameterHistory(request)
                .thenApply(GetParameterHistoryResponse::parameters)
                .exceptionally(throwable -> {
                    log.error("Failed to get parameter history: {}", parameterName, throwable);
                    throw new ParameterStoreException("Failed to get parameter history", throwable);
                });
    }
    
    /**
     * Add tags to parameter
     */
    public CompletableFuture<Void> addTagsToParameter(String parameterName, Map<String, String> tags) {
        List<Tag> tagList = tags.entrySet().stream()
                .map(entry -> Tag.builder()
                        .key(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .collect(Collectors.toList());
        
        AddTagsToResourceRequest request = AddTagsToResourceRequest.builder()
                .resourceType(ResourceTypeForTagging.PARAMETER)
                .resourceId(parameterName)
                .tags(tagList)
                .build();
        
        return ssmClient.addTagsToResource(request)
                .thenRun(() -> log.debug("Added tags to parameter: {}", parameterName))
                .exceptionally(throwable -> {
                    log.error("Failed to add tags to parameter: {}", parameterName, throwable);
                    throw new ParameterStoreException("Failed to add tags", throwable);
                });
    }
    
    /**
     * Invalidate cache for a parameter
     */
    public void invalidateCache(String parameterName) {
        cacheManager.invalidate("param:" + parameterName);
        log.debug("Invalidated cache for parameter: {}", parameterName);
    }
    
    // Private helper methods
    
    private CompletableFuture<GetParameterResponse> fetchParameter(
            String parameterName, boolean withDecryption) {
        
        GetParameterRequest request = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(withDecryption)
                .build();
        
        return ssmClient.getParameter(request)
                .exceptionally(throwable -> {
                    log.error("Failed to fetch parameter: {}", parameterName, throwable);
                    throw new ParameterStoreException("Failed to fetch parameter", throwable);
                });
    }
    
    /**
     * Custom exception for Parameter Store operations
     */
    public static class ParameterStoreException extends RuntimeException {
        public ParameterStoreException(String message) {
            super(message);
        }
        
        public ParameterStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}