package com.ryuqq.aws.sqs.example;

import com.ryuqq.aws.sqs.annotation.*;
import com.ryuqq.aws.sqs.model.SqsMessage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Another example SQS client for notification operations.
 */
@SqsClient("notification-service")
public interface NotificationService {

    /**
     * Send email notification.
     */
    @SendMessage("email-queue")
    CompletableFuture<String> sendEmail(@MessageBody EmailDto email);

    /**
     * Send SMS notification.
     */
    @SendMessage("sms-queue")
    CompletableFuture<String> sendSms(@MessageBody SmsDto sms);

    /**
     * Process email notifications.
     */
    @StartPolling("email-queue")
    void processEmails(@MessageProcessor Consumer<SqsMessage> processor);

    /**
     * Process SMS notifications.
     */
    @StartPolling("sms-queue")
    void processSms(@MessageProcessor Consumer<SqsMessage> processor);

    // Nested DTOs
    record EmailDto(String to, String subject, String body) {}
    record SmsDto(String phoneNumber, String message) {}
}