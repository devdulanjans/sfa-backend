package com.sfa.dto.customer;

public record CustomerImportRowResult(
        int rowNumber,
        String customerName,
        String message
) {}
