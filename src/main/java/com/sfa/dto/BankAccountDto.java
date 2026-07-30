package com.sfa.dto;

import com.sfa.entity.BankAccount;

import java.math.BigDecimal;
import java.util.UUID;

public record BankAccountDto(
        UUID id,
        String accountName,
        String accountNumber,
        String bankName,
        String currency,
        BigDecimal openingBalance,
        UUID glAccountId,
        boolean active,
        BigDecimal balance
) {
    public static BankAccountDto from(BankAccount b, BigDecimal balance) {
        return new BankAccountDto(
                b.getId(), b.getAccountName(), b.getAccountNumber(), b.getBankName(), b.getCurrency(),
                b.getOpeningBalance(), b.getGlAccount().getId(), b.isActive(), balance);
    }
}
