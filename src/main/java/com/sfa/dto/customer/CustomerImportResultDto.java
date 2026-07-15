package com.sfa.dto.customer;

import java.util.List;

public record CustomerImportResultDto(
        int totalRows,
        int successCount,
        int errorCount,
        List<CustomerImportRowResult> errors
) {}
