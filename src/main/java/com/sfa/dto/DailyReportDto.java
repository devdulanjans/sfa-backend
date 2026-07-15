package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DailyReportDto(
        LocalDate date,

        BigDecimal totalOpeningFloat,
        BigDecimal totalExpectedCash,
        BigDecimal totalCountedCash,
        BigDecimal totalVariance,
        boolean    allSessionsClosed,
        BigDecimal depositsTotal,
        BigDecimal withdrawalsTotal,

        BigDecimal grossSalesTotal,
        long       totalTransactionCount,
        List<PaymentMethodStat> paymentBreakdown,
        BigDecimal creditAmountPaidTotal,
        BigDecimal creditBalanceDueTotal,

        List<DrawerSessionDto>    sessions,
        List<DailyTransactionDto> transactions
) {
    public record PaymentMethodStat(String method, BigDecimal revenue, long count) {}
}
