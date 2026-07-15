package com.sfa.dto.customer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CustomerAnalyticsDto(
        long totalOrders,
        BigDecimal totalRevenue,
        BigDecimal avgOrderValue,
        Instant lastOrderDate,
        BigDecimal creditLimit,
        BigDecimal currentBalance,
        List<ProductStat> topProducts,
        List<MonthlyRevenue> monthlyTrend,
        Map<String, Long> statusBreakdown
) {
    public record ProductStat(
            String productCode,
            String productName,
            String unit,
            BigDecimal totalQty,
            BigDecimal totalRevenue
    ) {}

    public record MonthlyRevenue(
            String month,
            BigDecimal revenue,
            long orderCount
    ) {}
}
