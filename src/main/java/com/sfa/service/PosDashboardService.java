package com.sfa.service;

import com.sfa.dto.PosDashboardDto;
import com.sfa.entity.PosSale;
import com.sfa.repository.PosSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PosDashboardService {

    private static final int TOP_PRODUCTS_LIMIT  = 10;
    private static final int TOP_CUSTOMERS_LIMIT = 10;

    private final PosSaleRepository posSaleRepo;
    private final PosService        posService;

    public PosDashboardDto getDashboard(Instant from, Instant to) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Instant todayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant weekStart  = today.with(DayOfWeek.MONDAY).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal todayRevenue = posSaleRepo.sumRevenueBetween(todayStart, now);
        long       todayCount   = posSaleRepo.countCompletedBetween(todayStart, now);
        BigDecimal weekRevenue  = posSaleRepo.sumRevenueBetween(weekStart, now);
        long       weekCount    = posSaleRepo.countCompletedBetween(weekStart, now);
        BigDecimal monthRevenue = posSaleRepo.sumRevenueBetween(monthStart, now);
        long       monthCount   = posSaleRepo.countCompletedBetween(monthStart, now);

        BigDecimal outstandingCredit = posService.sumOutstandingCreditBalance(null, null, null, null);

        Instant rangeFrom = from != null ? from : now.minus(30, ChronoUnit.DAYS);
        Instant rangeTo   = to   != null ? to   : now;

        List<PosDashboardDto.RevenuePoint> dailyRevenue = posSaleRepo.dailyRevenueRaw(rangeFrom, rangeTo).stream()
                .map(r -> new PosDashboardDto.RevenuePoint(
                        String.valueOf(r[0]), toBigDecimal(r[1]), toLong(r[2])))
                .toList();

        List<PosDashboardDto.ProductStat> topProducts = posSaleRepo.topProductsRaw(rangeFrom, rangeTo, TOP_PRODUCTS_LIMIT).stream()
                .map(r -> new PosDashboardDto.ProductStat(
                        String.valueOf(r[0]), toBigDecimal(r[1]), toBigDecimal(r[2])))
                .toList();

        PosDashboardDto.WalkInStat walkIn     = new PosDashboardDto.WalkInStat(BigDecimal.ZERO, 0);
        PosDashboardDto.WalkInStat registered = new PosDashboardDto.WalkInStat(BigDecimal.ZERO, 0);
        for (Object[] r : posSaleRepo.walkInVsRegisteredRaw(rangeFrom, rangeTo)) {
            boolean isWalkin = (Boolean) r[0];
            PosDashboardDto.WalkInStat stat = new PosDashboardDto.WalkInStat(toBigDecimal(r[1]), toLong(r[2]));
            if (isWalkin) walkIn = stat; else registered = stat;
        }

        List<PosDashboardDto.CustomerStat> topCustomers = posSaleRepo.topCustomersRaw(rangeFrom, rangeTo, TOP_CUSTOMERS_LIMIT).stream()
                .map(r -> new PosDashboardDto.CustomerStat(
                        String.valueOf(r[0]), toBigDecimal(r[1]), toLong(r[2])))
                .toList();

        List<PosDashboardDto.PaymentMethodStat> paymentBreakdown = posSaleRepo.paymentMethodBreakdownRaw(rangeFrom, rangeTo).stream()
                .map(r -> new PosDashboardDto.PaymentMethodStat(
                        String.valueOf(r[0]), toBigDecimal(r[1]), toLong(r[2])))
                .toList();

        List<PosDashboardDto.RecentSale> recentSales = posSaleRepo.findTop10ByStatusOrderByCreatedAtDesc(PosSale.SaleStatus.COMPLETED)
                .stream()
                .map(s -> new PosDashboardDto.RecentSale(
                        s.getSaleNumber(),
                        s.getCustomerName() != null ? s.getCustomerName() : "Walk-in",
                        s.getTotal(),
                        s.getPaymentMethod().name(),
                        s.getCreatedAt()))
                .toList();

        return new PosDashboardDto(
                nullSafe(todayRevenue), todayCount,
                nullSafe(weekRevenue), weekCount,
                nullSafe(monthRevenue), monthCount,
                nullSafe(outstandingCredit),
                dailyRevenue, topProducts, walkIn, registered, topCustomers, paymentBreakdown, recentSales
        );
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }

    private static long toLong(Object v) {
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
    }
}
