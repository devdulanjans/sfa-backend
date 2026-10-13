package com.sfa.dto.lead;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Optional customer details the reviewer fills in while converting. Every field may be
 * null — a blank customerCode is generated from customer_code_seq (CUST-00001).
 */
public record ConvertLeadRequest(
        @Size(max = 30) String customerCode,
        UUID categoryId,
        String taxType,
        BigDecimal creditLimit,
        Integer creditDays
) {
    public static ConvertLeadRequest empty() {
        return new ConvertLeadRequest(null, null, null, null, null);
    }
}
