package com.sfa.dto;

import com.sfa.entity.InvoicePayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoicePaymentDto(
        UUID id,
        UUID invoiceId,
        String invoiceNumber,
        UUID bankAccountId,
        /** The bank account, or "Cash on hand" when none was chosen. */
        String bankAccountName,
        BigDecimal amount,
        LocalDate paymentDate,
        String paymentMethod,
        String referenceNumber,
        String receiptNumber,
        String notes,
        UUID journalEntryId,
        Instant createdAt,
        boolean voided,
        Instant voidedAt,
        String voidReason
) {
    public static InvoicePaymentDto from(InvoicePayment p) {
        return new InvoicePaymentDto(
                p.getId(), p.getInvoice().getId(), p.getInvoice().getInvoiceNumber(),
                p.getBankAccount() != null ? p.getBankAccount().getId() : null,
                p.getBankAccount() != null ? p.getBankAccount().getAccountName() : "Cash on hand",
                p.getAmount(), p.getPaymentDate(), p.getPaymentMethod().name(), p.getReferenceNumber(),
                p.getReceiptNumber(), p.getNotes(),
                p.getJournalEntry() != null ? p.getJournalEntry().getId() : null,
                p.getCreatedAt(), p.isVoided(), p.getVoidedAt(), p.getVoidReason());
    }
}
