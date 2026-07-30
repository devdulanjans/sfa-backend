package com.sfa.dto;

import com.sfa.entity.InvoicePayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoicePaymentDto(
        UUID id,
        UUID invoiceId,
        UUID bankAccountId,
        String bankAccountName,
        BigDecimal amount,
        LocalDate paymentDate,
        String paymentMethod,
        String referenceNumber,
        UUID journalEntryId
) {
    public static InvoicePaymentDto from(InvoicePayment p) {
        return new InvoicePaymentDto(
                p.getId(), p.getInvoice().getId(), p.getBankAccount().getId(), p.getBankAccount().getAccountName(),
                p.getAmount(), p.getPaymentDate(), p.getPaymentMethod().name(), p.getReferenceNumber(),
                p.getJournalEntry().getId());
    }
}
