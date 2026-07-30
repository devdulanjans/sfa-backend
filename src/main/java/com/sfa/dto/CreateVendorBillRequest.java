package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateVendorBillRequest(
        UUID vendorId,
        LocalDate billDate,
        LocalDate dueDate,
        BigDecimal total,
        UUID expenseAccountId,
        String description
) {}
