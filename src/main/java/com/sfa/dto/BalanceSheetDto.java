package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BalanceSheetDto(
        LocalDate asOfDate,
        List<FinancialReportLineDto> assets,
        List<FinancialReportLineDto> liabilities,
        List<FinancialReportLineDto> equity,
        BigDecimal totalAssets,
        BigDecimal totalLiabilities,
        BigDecimal totalEquity,
        boolean balanced
) {}
