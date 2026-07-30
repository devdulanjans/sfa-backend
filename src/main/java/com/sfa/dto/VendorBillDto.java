package com.sfa.dto;

import com.sfa.entity.VendorBill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VendorBillDto(
        UUID id,
        String billNumber,
        UUID vendorId,
        String vendorName,
        LocalDate billDate,
        LocalDate dueDate,
        BigDecimal total,
        BigDecimal paidAmount,
        BigDecimal balance,
        UUID expenseAccountId,
        String expenseAccountName,
        String status,
        String description
) {
    public static VendorBillDto from(VendorBill b) {
        return new VendorBillDto(
                b.getId(), b.getBillNumber(), b.getVendor().getId(), b.getVendor().getName(),
                b.getBillDate(), b.getDueDate(), b.getTotal(), b.getPaidAmount(),
                b.getTotal().subtract(b.getPaidAmount()),
                b.getExpenseAccount().getId(), b.getExpenseAccount().getAccountName(),
                b.getStatus().name(), b.getDescription());
    }
}
