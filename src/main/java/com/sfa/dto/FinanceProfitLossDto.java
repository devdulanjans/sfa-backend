package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ledger-backed P&L (Chart of Accounts / Journal Entries) — distinct from the older,
 *  cosmetic {@link ProfitLossDto} that reads POS sales + expenses directly. */
public record FinanceProfitLossDto(
        LocalDate dateFrom,
        LocalDate dateTo,
        List<FinancialReportLineDto> revenue,
        List<FinancialReportLineDto> expenses,
        BigDecimal totalRevenue,
        BigDecimal totalExpenses,
        BigDecimal netProfit
) {}
