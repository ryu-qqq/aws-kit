package com.ryuqq.aws.sns.types;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * SNS Topic representation
 */
@Getter
@Builder
@ToString
public class SnsTopic {
    
    /**
     * ARN of the topic
     */
    private final String topicArn;
    
    /**
     * Display name of the topic
     */
    private final String displayName;
    
    /**
     * Topic attributes
     */
    private final Map<String, String> attributes;
}