package com.ryuqq.aws.dynamodb.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

/**
 * DynamoDB 쿼리 요청 모델
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {
    
    private String tableName;
    private String indexName;
    private String keyConditionExpression;
    private String filterExpression;
    private Map<String, AttributeValue> expressionAttributeValues;
    private Map<String, String> expressionAttributeNames;
    private boolean consistentRead;
    private boolean scanIndexForward;
    private Integer limit;
    private Map<String, AttributeValue> exclusiveStartKey;
    
    public static class QueryRequestBuilder {
        public QueryRequestBuilder partitionKey(String partitionKeyName, AttributeValue partitionKeyValue) {
            if (this.keyConditionExpression == null) {
                this.keyConditionExpression = partitionKeyName + " = :pk";
            }
            if (this.expressionAttributeValues == null) {
                this.expressionAttributeValues = new HashMap<>();
            }
            this.expressionAttributeValues.put(":pk", partitionKeyValue);
            return this;
        }
        
        public QueryRequestBuilder sortKeyBetween(String sortKeyName, AttributeValue startValue, AttributeValue endValue) {
            if (this.keyConditionExpression != null && !this.keyConditionExpression.contains("AND")) {
                this.keyConditionExpression += " AND " + sortKeyName + " BETWEEN :sk_start AND :sk_end";
            }
            if (this.expressionAttributeValues == null) {
                this.expressionAttributeValues = new HashMap<>();
            }
            this.expressionAttributeValues.put(":sk_start", startValue);
            this.expressionAttributeValues.put(":sk_end", endValue);
            return this;
        }
        
        public QueryRequestBuilder sortKeyEquals(String sortKeyName, AttributeValue sortKeyValue) {
            if (this.keyConditionExpression != null && !this.keyConditionExpression.contains("AND")) {
                this.keyConditionExpression += " AND " + sortKeyName + " = :sk";
            }
            if (this.expressionAttributeValues == null) {
                this.expressionAttributeValues = new HashMap<>();
            }
            this.expressionAttributeValues.put(":sk", sortKeyValue);
            return this;
        }
        
        public QueryRequestBuilder sortKeyBeginsWith(String sortKeyName, AttributeValue prefixValue) {
            if (this.keyConditionExpression != null && !this.keyConditionExpression.contains("AND")) {
                this.keyConditionExpression += " AND begins_with(" + sortKeyName + ", :sk_prefix)";
            }
            if (this.expressionAttributeValues == null) {
                this.expressionAttributeValues = new HashMap<>();
            }
            this.expressionAttributeValues.put(":sk_prefix", prefixValue);
            return this;
        }
    }
}