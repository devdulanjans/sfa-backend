package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProfitLossDto(
        LocalDate  dateFrom,
        LocalDate  dateTo,
        BigDecimal totalIncome,
        long       incomeTransactionCount,
        BigDecimal totalCogs,
        BigDecimal grossProfit,
        BigDecimal totalExpenses,
        List<ExpenseCategoryStat> expensesByCategory,
        BigDecimal netProfit
) {
    public record ExpenseCategoryStat(String category, BigDecimal amount) {}
}
