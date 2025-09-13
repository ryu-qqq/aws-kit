package com.ryuqq.aws.sns.types;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Type abstraction for SNS message with immutable design
 */
public final class SnsMessage {

    private final String body;
    private final String subject;
    private final Map<String, String> attributes;
    private final MessageStructure structure;
    private final String phoneNumber;
    private final String targetArn;
    private final String messageGroupId;
    private final String messageDeduplicationId;

    private SnsMessage(
        String body,
        String subject,
        Map<String, String> attributes,
        MessageStructure structure,
        String phoneNumber,
        String targetArn,
        String messageGroupId,
        String messageDeduplicationId
    ) {
        this.body = Objects.requireNonNull(body, "body cannot be null");
        this.subject = subject;
        this.attributes = Map.copyOf(attributes != null ? attributes : Map.of());
        this.structure = structure != null ? structure : MessageStructure.RAW;
        this.phoneNumber = phoneNumber;
        this.targetArn = targetArn;
        this.messageGroupId = messageGroupId;
        this.messageDeduplicationId = messageDeduplicationId;
    }

    // Getters
    public String body() {
        return body;
    }

    public String subject() {
        return subject;
    }

    public Map<String, String> attributes() {
        return attributes;
    }

    public MessageStructure structure() {
        return structure;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public String targetArn() {
        return targetArn;
    }

    public String messageGroupId() {
        return messageGroupId;
    }

    public String messageDeduplicationId() {
        return messageDeduplicationId;
    }

    // Legacy compatibility getters
    public String getBody() {
        return body;
    }

    public String getSubject() {
        return subject;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    public MessageStructure getStructure() {
        return structure;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getTargetArn() {
        return targetArn;
    }

    public String getMessageGroupId() {
        return messageGroupId;
    }

    public String getMessageDeduplicationId() {
        return messageDeduplicationId;
    }

    // Withers pattern for immutable updates
    public SnsMessage withSubject(String subject) {
        return new SnsMessage(
            this.body,
            subject,
            this.attributes,
            this.structure,
            this.phoneNumber,
            this.targetArn,
            this.messageGroupId,
            this.messageDeduplicationId
        );
    }

    public SnsMessage withAttribute(String key, String value) {
        Map<String, String> newAttributes = new HashMap<>(this.attributes);
        newAttributes.put(key, value);
        return new SnsMessage(
            this.body,
            this.subject,
            newAttributes,
            this.structure,
            this.phoneNumber,
            this.targetArn,
            this.messageGroupId,
            this.messageDeduplicationId
        );
    }

    public SnsMessage withAttributes(Map<String, String> attributes) {
        return new SnsMessage(
            this.body,
            this.subject,
            attributes,
            this.structure,
            this.phoneNumber,
            this.targetArn,
            this.messageGroupId,
            this.messageDeduplicationId
        );
    }

    public SnsMessage withStructure(MessageStructure structure) {
        return new SnsMessage(
            this.body,
            this.subject,
            this.attributes,
            structure,
            this.phoneNumber,
            this.targetArn,
            this.messageGroupId,
            this.messageDeduplicationId
        );
    }

    public SnsMessage withPhoneNumber(String phoneNumber) {
        return new SnsMessage(
            this.body,
            this.subject,
            this.attributes,
            this.structure,
            phoneNumber,
            this.targetArn,
            this.messageGroupId,
            this.messageDeduplicationId
        );
    }

    public SnsMessage withTargetArn(String targetArn) {
        return new SnsMessage(
            this.body,
            this.subject,
            this.attributes,
            this.structure,
            this.phoneNumber,
            targetArn,
            this.messageGroupId,
            this.messageDeduplicationId
        );
    }

    public SnsMessage withMessageGroupId(String messageGroupId) {
        return new SnsMessage(
            this.body,
            this.subject,
            this.attributes,
            this.structure,
            this.phoneNumber,
            this.targetArn,
            messageGroupId,
            this.messageDeduplicationId
        );
    }

    public SnsMessage withMessageDeduplicationId(String messageDeduplicationId) {
        return new SnsMessage(
            this.body,
            this.subject,
            this.attributes,
            this.structure,
            this.phoneNumber,
            this.targetArn,
            this.messageGroupId,
            messageDeduplicationId
        );
    }

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

    // Static factory methods

    /**
     * Create a simple text message
     */
    public static SnsMessage of(String body) {
        return new SnsMessage(
            body,
            null,
            Map.of(),
            MessageStructure.RAW,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Create a message with subject
     */
    public static SnsMessage of(String subject, String body) {
        return new SnsMessage(
            body,
            subject,
            Map.of(),
            MessageStructure.RAW,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Create a JSON structured message for multiple protocols
     */
    public static SnsMessage jsonMessage(Map<String, String> protocolMessages) {
        return new SnsMessage(
            buildJsonStructure(protocolMessages),
            null,
            Map.of(),
            MessageStructure.JSON,
            null,
            null,
            null,
            null
        );
    }

    /**
     * Builder for complex message construction
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String body;
        private String subject;
        private Map<String, String> attributes = new HashMap<>();
        private MessageStructure structure = MessageStructure.RAW;
        private String phoneNumber;
        private String targetArn;
        private String messageGroupId;
        private String messageDeduplicationId;

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        public Builder attribute(String key, String value) {
            this.attributes.put(key, value);
            return this;
        }

        public Builder attributes(Map<String, String> attributes) {
            this.attributes = new HashMap<>(attributes);
            return this;
        }

        public Builder structure(MessageStructure structure) {
            this.structure = structure;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder targetArn(String targetArn) {
            this.targetArn = targetArn;
            return this;
        }

        public Builder messageGroupId(String messageGroupId) {
            this.messageGroupId = messageGroupId;
            return this;
        }

        public Builder messageDeduplicationId(String messageDeduplicationId) {
            this.messageDeduplicationId = messageDeduplicationId;
            return this;
        }

        public SnsMessage build() {
            return new SnsMessage(
                body,
                subject,
                attributes,
                structure,
                phoneNumber,
                targetArn,
                messageGroupId,
                messageDeduplicationId
            );
        }
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

    @Override
    public String toString() {
        return "SnsMessage[" +
                "body='" + body + '\'' +
                ", subject='" + subject + '\'' +
                ", attributes=" + attributes +
                ", structure=" + structure +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", targetArn='" + targetArn + '\'' +
                ", messageGroupId='" + messageGroupId + '\'' +
                ", messageDeduplicationId='" + messageDeduplicationId + '\'' +
                ']';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        SnsMessage that = (SnsMessage) obj;
        return Objects.equals(body, that.body) &&
                Objects.equals(subject, that.subject) &&
                Objects.equals(attributes, that.attributes) &&
                structure == that.structure &&
                Objects.equals(phoneNumber, that.phoneNumber) &&
                Objects.equals(targetArn, that.targetArn) &&
                Objects.equals(messageGroupId, that.messageGroupId) &&
                Objects.equals(messageDeduplicationId, that.messageDeduplicationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(body, subject, attributes, structure, phoneNumber,
                           targetArn, messageGroupId, messageDeduplicationId);
    }
}