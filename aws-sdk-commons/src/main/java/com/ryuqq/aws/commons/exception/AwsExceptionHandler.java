package com.ryuqq.aws.commons.exception;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * AWS SDK 예외 처리 핸들러
 */
@Slf4j
@UtilityClass
public class AwsExceptionHandler {

    /**
     * AWS 예외를 표준화된 예외로 변환
     */
    public static AwsServiceException handleException(String serviceName, String operation, Throwable throwable) {
        if (throwable instanceof AwsServiceException) {
            return (AwsServiceException) throwable;
        }

        if (throwable.getCause() instanceof software.amazon.awssdk.awscore.exception.AwsServiceException awsException) {
            AwsErrorDetails errorDetails = awsException.awsErrorDetails();
            
            log.error("AWS Service Exception - Service: {}, Operation: {}, ErrorCode: {}, StatusCode: {}, RequestId: {}",
                    serviceName, operation, errorDetails.errorCode(), 
                    awsException.statusCode(), awsException.requestId());

            return new AwsServiceException(
                    serviceName,
                    errorDetails.errorCode(),
                    errorDetails.errorMessage(),
                    awsException.statusCode(),
                    awsException.requestId(),
                    isRetryableError(awsException)
            );
        }

        if (throwable.getCause() instanceof SdkServiceException sdkException) {
            log.error("SDK Service Exception - Service: {}, Operation: {}, StatusCode: {}",
                    serviceName, operation, sdkException.statusCode());

            return new AwsServiceException(
                    serviceName,
                    "SDK_SERVICE_ERROR",
                    sdkException.getMessage(),
                    sdkException.statusCode(),
                    null,
                    isRetryableError(sdkException)
            );
        }

        if (throwable.getCause() instanceof SdkClientException clientException) {
            log.error("SDK Client Exception - Service: {}, Operation: {}, Message: {}",
                    serviceName, operation, clientException.getMessage());

            return new AwsServiceException(
                    serviceName,
                    "SDK_CLIENT_ERROR",
                    clientException.getMessage(),
                    0,
                    null,
                    isRetryableError(clientException)
            );
        }

        log.error("Unknown Exception - Service: {}, Operation: {}",
                serviceName, operation, throwable);

        return new AwsServiceException(serviceName, 
                "Unknown error during " + operation, throwable);
    }

    /**
     * 재시도 가능한 에러인지 판단
     */
    public static boolean isRetryableError(Throwable throwable) {
        if (throwable instanceof software.amazon.awssdk.core.exception.RetryableException) {
            return true;
        }

        if (throwable instanceof software.amazon.awssdk.awscore.exception.AwsServiceException awsException) {
            // 스로틀링 예외
            if (awsException.isThrottlingException()) {
                return true;
            }

            // 5xx 서버 에러
            if (awsException.statusCode() >= 500) {
                return true;
            }

            // 429 Too Many Requests
            if (awsException.statusCode() == 429) {
                return true;
            }

            // 특정 에러 코드 체크
            String errorCode = awsException.awsErrorDetails().errorCode();
            return isRetryableErrorCode(errorCode);
        }

        return false;
    }

    /**
     * 재시도 가능한 에러 코드 확인
     */
    private static boolean isRetryableErrorCode(String errorCode) {
        return switch (errorCode) {
            case "RequestTimeout",
                 "ServiceUnavailable",
                 "Throttling",
                 "ThrottlingException",
                 "RequestLimitExceeded",
                 "BandwidthLimitExceeded",
                 "ProvisionedThroughputExceededException",
                 "TransactionInProgressException",
                 "RequestThrottled",
                 "SlowDown",
                 "EC2ThrottledException" -> true;
            default -> false;
        };
    }

    /**
     * CompletableFuture에 예외 처리 적용
     */
    public static <T> Function<Throwable, T> handleAsync(String serviceName, String operation) {
        return throwable -> {
            throw handleException(serviceName, operation, throwable);
        };
    }

    /**
     * CompletableFuture 체인에서 예외 처리 및 로깅
     */
    public static <T> CompletableFuture<T> wrapWithExceptionHandling(
            CompletableFuture<T> future,
            String serviceName,
            String operation) {
        
        return future.exceptionally(throwable -> {
            AwsServiceException exception = handleException(serviceName, operation, throwable);
            
            if (exception.isRetryable()) {
                log.warn("Retryable error in {}.{}: {}", 
                        serviceName, operation, exception.getMessage());
            } else {
                log.error("Non-retryable error in {}.{}: {}", 
                        serviceName, operation, exception.getMessage());
            }
            
            throw exception;
        });
    }

    /**
     * 특정 예외 타입에 대한 폴백 처리
     */
    public static <T> CompletableFuture<T> withFallback(
            CompletableFuture<T> future,
            Class<? extends Throwable> exceptionClass,
            Function<Throwable, T> fallbackFunction) {
        
        return future.exceptionally(throwable -> {
            Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
            
            if (exceptionClass.isInstance(cause)) {
                log.debug("Applying fallback for exception type: {}", 
                        exceptionClass.getSimpleName());
                return fallbackFunction.apply(cause);
            }
            
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            throw new RuntimeException(throwable);
        });
    }

    /**
     * 예외 발생 시 기본값 반환
     */
    public static <T> CompletableFuture<T> withDefault(
            CompletableFuture<T> future,
            T defaultValue,
            String serviceName,
            String operation) {
        
        return future.exceptionally(throwable -> {
            log.warn("Returning default value for {}.{} due to error: {}",
                    serviceName, operation, throwable.getMessage());
            return defaultValue;
        });
    }
}