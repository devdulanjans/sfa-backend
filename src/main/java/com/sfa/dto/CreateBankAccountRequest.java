package com.sfa.dto;

import java.math.BigDecimal;

public record CreateBankAccountRequest(
        String accountName,
        String accountNumber,
        String bankName,
        String currency,
        BigDecimal openingBalance
) {}
