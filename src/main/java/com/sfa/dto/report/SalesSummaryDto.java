package com.sfa.dto.report;

import java.math.BigDecimal;
import java.util.List;

public record SalesSummaryDto(
        BigDecimal totalRevenue,
        long totalOrders,
        BigDecimal avgOrderValue,
        long invoicesCount,
        List<DailyRevenueDto> dailyRevenue
) {
    public record DailyRevenueDto(String date, BigDecimal revenue) {}
}
