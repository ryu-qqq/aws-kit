package com.ryuqq.aws.sns.types;

import java.util.Map;

/**
 * SNS Topic representation as an immutable record
 */
public record SnsTopic(
    /**
     * ARN of the topic
     */
    String topicArn,

    /**
     * Display name of the topic
     */
    String displayName,

    /**
     * Topic attributes
     */
    Map<String, String> attributes
) {

    /**
     * Create a simple topic with only ARN
     */
    public static SnsTopic of(String topicArn) {
        return new SnsTopic(topicArn, null, Map.of());
    }

    /**
     * Create topic with ARN and display name
     */
    public static SnsTopic of(String topicArn, String displayName) {
        return new SnsTopic(topicArn, displayName, Map.of());
    }

    /**
     * Create topic with all properties
     */
    public static SnsTopic of(String topicArn, String displayName, Map<String, String> attributes) {
        return new SnsTopic(
            topicArn,
            displayName,
            attributes != null ? Map.copyOf(attributes) : Map.of()
        );
    }
}