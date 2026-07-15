package com.sfa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PosDashboardDto(
        BigDecimal todayRevenue,   long todayCount,
        BigDecimal weekRevenue,    long weekCount,
        BigDecimal monthRevenue,   long monthCount,
        BigDecimal outstandingCredit,

        List<RevenuePoint>      dailyRevenue,
        List<ProductStat>       topProducts,
        WalkInStat               walkIn,
        WalkInStat               registered,
        List<CustomerStat>      topCustomers,
        List<PaymentMethodStat> paymentBreakdown,
        List<RecentSale>        recentSales
) {
    public record RevenuePoint(String date, BigDecimal revenue, long saleCount) {}

    public record ProductStat(String productName, BigDecimal quantitySold, BigDecimal revenue) {}

    public record WalkInStat(BigDecimal revenue, long saleCount) {}

    public record CustomerStat(String customerName, BigDecimal revenue, long saleCount) {}

    public record PaymentMethodStat(String method, BigDecimal revenue, long saleCount) {}

    public record RecentSale(
            String     saleNumber,
            String     customerName,
            BigDecimal total,
            String     paymentMethod,
            Instant    createdAt
    ) {}
}
