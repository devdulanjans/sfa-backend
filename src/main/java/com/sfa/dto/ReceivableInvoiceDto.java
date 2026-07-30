package com.sfa.dto;

import com.sfa.entity.Invoice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReceivableInvoiceDto(
        UUID id,
        String invoiceNumber,
        LocalDate issuedDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance,
        String status
) {
    public static ReceivableInvoiceDto from(Invoice i) {
        return new ReceivableInvoiceDto(
                i.getId(), i.getInvoiceNumber(), i.getIssuedDate(), i.getDueDate(),
                i.getTotal(), i.getPaidAmount(), i.getTotal().subtract(i.getPaidAmount()),
                i.getStatus().name());
    }
}
