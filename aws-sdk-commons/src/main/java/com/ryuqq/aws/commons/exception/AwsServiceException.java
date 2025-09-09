package com.ryuqq.aws.commons.exception;

import lombok.Getter;

/**
 * AWS 서비스 공통 예외 클래스
 */
@Getter
public class AwsServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    private final String serviceName;
    private final String errorCode;
    private final int statusCode;
    private final String requestId;
    private final boolean retryable;

    public AwsServiceException(String serviceName, String message) {
        super(message);
        this.serviceName = serviceName;
        this.errorCode = "UNKNOWN_ERROR";
        this.statusCode = 500;
        this.requestId = null;
        this.retryable = false;
    }

    public AwsServiceException(String serviceName, String message, Throwable cause) {
        super(message, cause);
        this.serviceName = serviceName;
        this.errorCode = extractErrorCode(cause);
        this.statusCode = extractStatusCode(cause);
        this.requestId = extractRequestId(cause);
        this.retryable = isRetryableError(cause);
    }

    public AwsServiceException(String serviceName, String errorCode, String message, 
                              int statusCode, String requestId, boolean retryable) {
        super(message);
        this.serviceName = serviceName;
        this.errorCode = errorCode;
        this.statusCode = statusCode;
        this.requestId = requestId;
        this.retryable = retryable;
    }

    private String extractErrorCode(Throwable cause) {
        if (cause instanceof software.amazon.awssdk.awscore.exception.AwsServiceException awsException) {
            return awsException.awsErrorDetails().errorCode();
        }
        return "UNKNOWN_ERROR";
    }

    private int extractStatusCode(Throwable cause) {
        if (cause instanceof software.amazon.awssdk.awscore.exception.AwsServiceException awsException) {
            return awsException.statusCode();
        }
        return 500;
    }

    private String extractRequestId(Throwable cause) {
        if (cause instanceof software.amazon.awssdk.awscore.exception.AwsServiceException awsException) {
            return awsException.requestId();
        }
        return null;
    }

    private boolean isRetryableError(Throwable cause) {
        if (cause instanceof software.amazon.awssdk.core.exception.RetryableException) {
            return true;
        }
        if (cause instanceof software.amazon.awssdk.awscore.exception.AwsServiceException awsException) {
            return awsException.isThrottlingException() || 
                   awsException.statusCode() >= 500 ||
                   awsException.statusCode() == 429;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("%s[service=%s, errorCode=%s, statusCode=%d, requestId=%s, retryable=%s]: %s",
                getClass().getSimpleName(), serviceName, errorCode, statusCode, requestId, retryable, getMessage());
    }
}