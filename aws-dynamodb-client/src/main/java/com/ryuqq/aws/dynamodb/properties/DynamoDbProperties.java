package com.ryuqq.aws.dynamodb.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

/**
 * DynamoDB 클라이언트 설정 Properties
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "aws.dynamodb")
public class DynamoDbProperties {

    @Valid
    @NotNull
    private TableConfig tableConfig = new TableConfig();

    @Valid
    @NotNull
    private QueryConfig queryConfig = new QueryConfig();

    @Valid
    @NotNull
    private BatchConfig batchConfig = new BatchConfig();

    private String tablePrefix = "";
    private String tableSuffix = "";
    private boolean enablePointInTimeRecovery = false;
    private String endpoint; // For LocalStack

    @Getter
    @Setter
    public static class TableConfig {
        private String billingMode = "PAY_PER_REQUEST"; // PAY_PER_REQUEST or PROVISIONED
        
        @Min(1)
        private long readCapacityUnits = 5;
        
        @Min(1) 
        private long writeCapacityUnits = 5;
        
        private boolean enableDeletionProtection = false;
        private boolean enableStreams = false;
        private String streamViewType = "NEW_AND_OLD_IMAGES"; // KEYS_ONLY, NEW_IMAGE, OLD_IMAGE, NEW_AND_OLD_IMAGES
    }

    @Getter
    @Setter
    public static class QueryConfig {
        @Min(1)
        private int defaultPageSize = 100;
        
        private Duration scanTimeout = Duration.ofMinutes(5);
        private Duration queryTimeout = Duration.ofSeconds(30);
        
        private boolean consistentRead = false;
        private boolean enableParallelScan = false;
        
        @Min(1)
        private int totalSegments = 1;
    }

    @Getter
    @Setter
    public static class BatchConfig {
        @Min(1)
        private int batchWriteSize = 25; // DynamoDB batch write limit
        
        @Min(1)
        private int batchReadSize = 100; // DynamoDB batch read limit
        
        private Duration batchFlushInterval = Duration.ofSeconds(1);
        private boolean enableBatching = true;
        
        @Min(0)
        private int maxRetryAttempts = 3;
        
        private Duration retryDelay = Duration.ofMillis(100);
    }
}