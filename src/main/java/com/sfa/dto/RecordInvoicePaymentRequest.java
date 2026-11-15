package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** {@code bankAccountId} is optional (V120) — without it the money is held on Cash on Hand. */
public record RecordInvoicePaymentRequest(
        UUID bankAccountId,
        BigDecimal amount,
        LocalDate paymentDate,
        String paymentMethod,
        String referenceNumber,
        String notes
) {}
