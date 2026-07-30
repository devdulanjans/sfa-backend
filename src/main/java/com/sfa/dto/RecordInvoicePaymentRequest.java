package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordInvoicePaymentRequest(
        UUID bankAccountId,
        BigDecimal amount,
        LocalDate paymentDate,
        String paymentMethod,
        String referenceNumber
) {}
