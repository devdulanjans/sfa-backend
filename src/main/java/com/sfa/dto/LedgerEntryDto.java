package com.sfa.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryDto(
        Instant    date,
        String     type,          // INCOME | EXPENSE
        String     reference,
        String     description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance
) {}
