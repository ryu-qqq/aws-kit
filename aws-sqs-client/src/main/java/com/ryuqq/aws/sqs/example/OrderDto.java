package com.ryuqq.aws.sqs.example;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Example DTO for demonstrating annotation-based SQS operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {
    
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
    
    public static OrderDto create(String orderId, String customerId, BigDecimal amount) {
        return OrderDto.builder()
                .orderId(orderId)
                .customerId(customerId)
                .amount(amount)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
    }
}