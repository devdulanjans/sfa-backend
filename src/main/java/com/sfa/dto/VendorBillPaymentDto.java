package com.sfa.dto;

import com.sfa.entity.VendorBillPayment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VendorBillPaymentDto(
        UUID id,
        UUID vendorBillId,
        UUID bankAccountId,
        String bankAccountName,
        BigDecimal amount,
        LocalDate paymentDate,
        String paymentMethod,
        String referenceNumber,
        UUID journalEntryId
) {
    public static VendorBillPaymentDto from(VendorBillPayment p) {
        return new VendorBillPaymentDto(
                p.getId(), p.getVendorBill().getId(), p.getBankAccount().getId(), p.getBankAccount().getAccountName(),
                p.getAmount(), p.getPaymentDate(), p.getPaymentMethod().name(), p.getReferenceNumber(),
                p.getJournalEntry().getId());
    }
}
