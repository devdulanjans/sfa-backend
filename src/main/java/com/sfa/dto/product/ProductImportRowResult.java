package com.sfa.dto.product;

public record ProductImportRowResult(
        int rowNumber,
        String productCode,
        String message
) {}
