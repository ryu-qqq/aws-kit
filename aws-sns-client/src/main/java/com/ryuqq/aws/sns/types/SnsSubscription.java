package com.ryuqq.aws.sns.types;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * SNS Subscription representation
 */
@Getter
@Builder
@ToString
public class SnsSubscription {
    
    /**
     * ARN of the subscription
     */
    private final String subscriptionArn;
    
    /**
     * ARN of the topic
     */
    private final String topicArn;
    
    /**
     * Protocol (email, sms, http, https, sqs, lambda, etc.)
     */
    private final String protocol;
    
    /**
     * Endpoint (email address, phone number, URL, etc.)
     */
    private final String endpoint;
    
    /**
     * AWS account ID of the subscription owner
     */
    private final String owner;
}