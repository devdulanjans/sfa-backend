package com.sfa.dto;

import com.sfa.entity.ChartOfAccount;

import java.math.BigDecimal;
import java.util.UUID;

public record ChartOfAccountDto(
        UUID id,
        String accountCode,
        String accountName,
        String accountType,
        UUID parentAccountId,
        boolean systemAccount,
        boolean active,
        BigDecimal balance
) {
    public static ChartOfAccountDto from(ChartOfAccount a) {
        return from(a, null);
    }

    public static ChartOfAccountDto from(ChartOfAccount a, BigDecimal balance) {
        return new ChartOfAccountDto(
                a.getId(), a.getAccountCode(), a.getAccountName(), a.getAccountType().name(),
                a.getParentAccount() != null ? a.getParentAccount().getId() : null,
                a.isSystemAccount(), a.isActive(), balance);
    }
}
