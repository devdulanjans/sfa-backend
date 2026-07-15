package com.sfa.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardDto(
        BigDecimal todayRevenue,
        BigDecimal monthRevenue,
        BigDecimal totalRevenue,
        long       todayOrders,
        double     targetPct,

        List<DailyPoint>  dailyRevenue,
        Map<String, Long> statusBreakdown,
        List<TopCustomer> topCustomers,
        List<RecentOrder> recentOrders
) {
    public record DailyPoint(String date, BigDecimal revenue) {}

    public record TopCustomer(String name, BigDecimal revenue, long orderCount) {}

    public record RecentOrder(
            String     orderNumber,
            String     customerName,
            BigDecimal total,
            String     status,
            String     date
    ) {}
}
