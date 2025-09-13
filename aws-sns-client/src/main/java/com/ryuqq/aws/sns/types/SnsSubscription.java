package com.ryuqq.aws.sns.types;

/**
 * SNS Subscription representation as an immutable record
 */
public record SnsSubscription(
    /**
     * ARN of the subscription
     */
    String subscriptionArn,

    /**
     * ARN of the topic
     */
    String topicArn,

    /**
     * Protocol (email, sms, http, https, sqs, lambda, etc.)
     */
    String protocol,

    /**
     * Endpoint (email address, phone number, URL, etc.)
     */
    String endpoint,

    /**
     * AWS account ID of the subscription owner
     */
    String owner
) {

    /**
     * Create subscription with basic info
     */
    public static SnsSubscription of(String subscriptionArn, String topicArn, String protocol, String endpoint) {
        return new SnsSubscription(subscriptionArn, topicArn, protocol, endpoint, null);
    }

    /**
     * Create subscription with all properties
     */
    public static SnsSubscription of(
        String subscriptionArn,
        String topicArn,
        String protocol,
        String endpoint,
        String owner
    ) {
        return new SnsSubscription(subscriptionArn, topicArn, protocol, endpoint, owner);
    }
}