package com.sfa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DailyTransactionDto(
        UUID       id,
        String     saleNumber,
        Instant    createdAt,
        String     cashierName,
        String     customerName,
        String     paymentMethod,
        String     cardLast4,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        String     creditStatus,
        String     status
) {}
