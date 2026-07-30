package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordTaxPaymentRequest(
        UUID bankAccountId,
        BigDecimal amount,
        LocalDate paymentDate,
        String referenceNumber
) {}
