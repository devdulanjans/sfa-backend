package com.sfa.dto.product;

import java.util.List;

public record ProductImportResultDto(
        int totalRows,
        int successCount,
        int errorCount,
        List<ProductImportRowResult> errors,
        boolean stockTrackingEnabled,
        String note
) {}
