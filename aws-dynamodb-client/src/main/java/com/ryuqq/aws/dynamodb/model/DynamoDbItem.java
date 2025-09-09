package com.ryuqq.aws.dynamodb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;
import java.util.Map;

/**
 * DynamoDB 기본 아이템 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class DynamoDbItem {
    
    private String partitionKey;
    private String sortKey;
    private Map<String, Object> attributes;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;

    @DynamoDbPartitionKey
    public String getPartitionKey() {
        return partitionKey;
    }

    @DynamoDbSortKey
    public String getSortKey() {
        return sortKey;
    }
}