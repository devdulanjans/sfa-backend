package com.sfa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PosReportRowDto(
        UUID       id,
        String     saleNumber,
        Instant    createdAt,
        String     cashierName,
        String     customerName,
        String     paymentMethod,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal total,
        String     creditStatus,
        String     status
) {}
