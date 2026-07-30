package com.sfa.dto;

import java.util.UUID;

public record CreateChartOfAccountRequest(
        String accountCode,
        String accountName,
        String accountType,
        UUID parentAccountId
) {}
