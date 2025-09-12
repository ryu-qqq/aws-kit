package com.ryuqq.aws.sns.types;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;

/**
 * Type abstraction for SNS message
 */
@Getter
@Builder
@ToString
public class SnsMessage {
    
    @NonNull
    private final String body;
    
    private final String subject;
    
    @Builder.Default
    private final Map<String, String> attributes = new HashMap<>();
    
    @Builder.Default
    private final MessageStructure structure = MessageStructure.RAW;
    
    private final String phoneNumber;
    
    private final String targetArn;
    
    private final String messageGroupId;
    
    private final String messageDeduplicationId;
    
    public enum MessageStructure {
        RAW("raw"),
        JSON("json");
        
        private final String value;
        
        MessageStructure(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    /**
     * Add an attribute to the message
     */
    public SnsMessage withAttribute(String key, String value) {
        this.attributes.put(key, value);
        return this;
    }
    
    /**
     * Create a simple text message
     */
    public static SnsMessage of(String body) {
        return SnsMessage.builder()
                .body(body)
                .build();
    }
    
    /**
     * Create a message with subject
     */
    public static SnsMessage of(String subject, String body) {
        return SnsMessage.builder()
                .subject(subject)
                .body(body)
                .build();
    }
    
    /**
     * Create a JSON structured message for multiple protocols
     */
    public static SnsMessage jsonMessage(Map<String, String> protocolMessages) {
        return SnsMessage.builder()
                .body(buildJsonStructure(protocolMessages))
                .structure(MessageStructure.JSON)
                .build();
    }
    
    private static String buildJsonStructure(Map<String, String> protocolMessages) {
        StringBuilder json = new StringBuilder("{");
        protocolMessages.forEach((protocol, message) -> {
            if (json.length() > 1) json.append(",");
            json.append("\"").append(protocol).append("\":\"")
                .append(escapeJson(message)).append("\"");
        });
        json.append("}");
        return json.toString();
    }
    
    private static String escapeJson(String value) {
        return value.replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}