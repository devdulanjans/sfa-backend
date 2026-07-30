package com.sfa.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record FinancialReportLineDto(UUID accountId, String accountCode, String accountName, BigDecimal amount) {}
