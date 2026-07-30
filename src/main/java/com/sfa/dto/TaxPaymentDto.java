package com.sfa.dto;

import com.sfa.entity.TaxPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TaxPaymentDto(
        UUID id,
        UUID bankAccountId,
        String bankAccountName,
        BigDecimal amount,
        LocalDate paymentDate,
        String referenceNumber,
        UUID journalEntryId
) {
    public static TaxPaymentDto from(TaxPayment p) {
        return new TaxPaymentDto(
                p.getId(), p.getBankAccount().getId(), p.getBankAccount().getAccountName(),
                p.getAmount(), p.getPaymentDate(), p.getReferenceNumber(), p.getJournalEntry().getId());
    }
}
