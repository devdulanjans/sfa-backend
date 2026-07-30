package com.sfa.dto;

import com.sfa.entity.JournalEntryLine;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalEntryLineDto(
        UUID id,
        UUID accountId,
        String accountCode,
        String accountName,
        BigDecimal debitAmount,
        BigDecimal creditAmount,
        String description
) {
    public static JournalEntryLineDto from(JournalEntryLine l) {
        return new JournalEntryLineDto(
                l.getId(), l.getAccount().getId(), l.getAccount().getAccountCode(), l.getAccount().getAccountName(),
                l.getDebitAmount(), l.getCreditAmount(), l.getDescription());
    }
}
