package com.sfa.controller;

import com.sfa.dto.DashboardDto;
import com.sfa.entity.Role;
import com.sfa.entity.User;
import com.sfa.repository.OrderRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final OrderRepository orderRepo;
    private final UserRepository  userRepository;

    @GetMapping
    public DashboardDto dashboard(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID salesRepId,
            @RequestParam(required = false) UUID distributorId) {

        Instant todayStart    = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant tomorrowStart = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant monthStart    = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Instant rangeFrom = from != null
                ? from.atStartOfDay().toInstant(ZoneOffset.UTC) : monthStart;
        Instant rangeTo   = to != null
                ? to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : tomorrowStart;

        BigDecimal todayRevenue;
        BigDecimal monthRevenue;
        BigDecimal totalRevenue;
        long todayOrders;

        // Determine effective rep for chart queries
        UUID effectiveRepId;

        if (Role.SALES_REP.equals(user.getRoleName())) {
            effectiveRepId = user.getId();
            UUID repId = effectiveRepId;
            if (distributorId != null) {
                todayRevenue = nullSafe(orderRepo.sumRevenueByRepAndDistributor(repId, distributorId, todayStart, tomorrowStart));
                monthRevenue = nullSafe(orderRepo.sumRevenueByRepAndDistributor(repId, distributorId, monthStart, tomorrowStart));
                totalRevenue = nullSafe(orderRepo.sumRevenueByRepAndDistributor(repId, distributorId, rangeFrom, rangeTo));
                todayOrders  = orderRepo.countBetweenByRepAndDistributor(repId, distributorId, todayStart, tomorrowStart);
            } else {
                todayRevenue = nullSafe(orderRepo.sumRevenueByRep(repId, todayStart, tomorrowStart));
                monthRevenue = nullSafe(orderRepo.sumRevenueByRep(repId, monthStart, tomorrowStart));
                totalRevenue = nullSafe(orderRepo.sumRevenueByRep(repId, rangeFrom, rangeTo));
                todayOrders  = orderRepo.countBetweenByRep(repId, todayStart, tomorrowStart);
            }
        } else if (salesRepId != null) {
            effectiveRepId = salesRepId;
            if (distributorId != null) {
                todayRevenue = nullSafe(orderRepo.sumRevenueByRepAndDistributor(salesRepId, distributorId, todayStart, tomorrowStart));
                monthRevenue = nullSafe(orderRepo.sumRevenueByRepAndDistributor(salesRepId, distributorId, monthStart, tomorrowStart));
                totalRevenue = nullSafe(orderRepo.sumRevenueByRepAndDistributor(salesRepId, distributorId, rangeFrom, rangeTo));
                todayOrders  = orderRepo.countBetweenByRepAndDistributor(salesRepId, distributorId, todayStart, tomorrowStart);
            } else {
                todayRevenue = nullSafe(orderRepo.sumRevenueByRep(salesRepId, todayStart, tomorrowStart));
                monthRevenue = nullSafe(orderRepo.sumRevenueByRep(salesRepId, monthStart, tomorrowStart));
                totalRevenue = nullSafe(orderRepo.sumRevenueByRep(salesRepId, rangeFrom, rangeTo));
                todayOrders  = orderRepo.countBetweenByRep(salesRepId, todayStart, tomorrowStart);
            }
        } else {
            effectiveRepId = null;
            if (distributorId != null) {
                todayRevenue = nullSafe(orderRepo.totalRevenueBetweenAndDistributor(distributorId, todayStart, tomorrowStart));
                monthRevenue = nullSafe(orderRepo.totalRevenueBetweenAndDistributor(distributorId, monthStart, tomorrowStart));
                totalRevenue = nullSafe(orderRepo.totalRevenueBetweenAndDistributor(distributorId, rangeFrom, rangeTo));
                todayOrders  = orderRepo.countBetweenAndDistributor(distributorId, todayStart, tomorrowStart);
            } else {
                todayRevenue = nullSafe(orderRepo.totalRevenueBetween(todayStart, tomorrowStart));
                monthRevenue = nullSafe(orderRepo.totalRevenueBetween(monthStart, tomorrowStart));
                totalRevenue = nullSafe(orderRepo.totalRevenueBetween(rangeFrom, rangeTo));
                todayOrders  = orderRepo.countBetween(todayStart, tomorrowStart);
            }
        }

        return new DashboardDto(
                todayRevenue, monthRevenue, totalRevenue, todayOrders, 0.0,
                buildDailyRevenue(effectiveRepId, rangeFrom, rangeTo),
                buildStatusBreakdown(effectiveRepId, rangeFrom, rangeTo),
                buildTopCustomers(effectiveRepId, rangeFrom, rangeTo),
                buildRecentOrders(effectiveRepId)
        );
    }

    // ── Customer dashboard ────────────────────────────────────────────────────

    @GetMapping("/customer")
    public CustomerDashboardDto customerDashboard(
            @AuthenticationPrincipal UserDetailsImpl user) {

        UUID customerId = user.getLinkedCustomerId();
        if (customerId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No linked customer account");
        }

        long total = orderRepo.countByCustomer(customerId);
        BigDecimal spent = nullSafe(orderRepo.sumTotalByCustomer(customerId));

        long pending    = 0;
        long approved   = 0;
        for (Object[] row : orderRepo.statusBreakdownByCustomer(customerId)) {
            String status = row[0].toString();
            long   count  = ((Number) row[1]).longValue();
            if ("SUBMITTED".equals(status)) pending  = count;
            if ("APPROVED".equals(status))  approved = count;
        }

        return new CustomerDashboardDto(total, pending, pending + approved, spent);
    }

    // ── Sales reps list ───────────────────────────────────────────────────────

    @GetMapping("/sales-reps")
    public List<SalesRepInfo> salesReps() {
        return userRepository.findActiveByRoleName(Role.SALES_REP, User.UserStatus.ACTIVE)
                .stream()
                .map(u -> new SalesRepInfo(u.getId(), u.getFullName(), u.getUsername()))
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<DashboardDto.DailyPoint> buildDailyRevenue(UUID repId, Instant from, Instant to) {
        List<Object[]> raw = repId != null
                ? orderRepo.dailyRevenueByRepRaw(repId, from, to)
                : orderRepo.dailyRevenueRaw(from, to);
        return raw.stream().map(r -> new DashboardDto.DailyPoint(
                String.valueOf(r[0]),
                r[1] instanceof BigDecimal bd ? bd
                        : BigDecimal.valueOf(((Number) r[1]).doubleValue())
        )).toList();
    }

    private Map<String, Long> buildStatusBreakdown(UUID repId, Instant from, Instant to) {
        List<Object[]> raw = repId != null
                ? orderRepo.statusBreakdownByRepBetween(repId, from, to)
                : orderRepo.statusBreakdownBetween(from, to);
        LinkedHashMap<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : raw) {
            map.put(row[0].toString(), ((Number) row[1]).longValue());
        }
        return map;
    }

    private List<DashboardDto.TopCustomer> buildTopCustomers(UUID repId, Instant from, Instant to) {
        List<Object[]> raw = repId != null
                ? orderRepo.topCustomersByRepRaw(repId, from, to)
                : orderRepo.topCustomersAllRaw(from, to);
        return raw.stream().map(r -> new DashboardDto.TopCustomer(
                String.valueOf(r[0]),
                r[1] instanceof BigDecimal bd ? bd
                        : BigDecimal.valueOf(((Number) r[1]).doubleValue()),
                ((Number) r[2]).longValue()
        )).toList();
    }

    private List<DashboardDto.RecentOrder> buildRecentOrders(UUID repId) {
        List<Object[]> raw = repId != null
                ? orderRepo.recentOrdersByRepRaw(repId)
                : orderRepo.recentOrdersAllRaw();
        return raw.stream().map(r -> new DashboardDto.RecentOrder(
                String.valueOf(r[0]),
                String.valueOf(r[1]),
                r[2] instanceof BigDecimal bd ? bd
                        : BigDecimal.valueOf(((Number) r[2]).doubleValue()),
                String.valueOf(r[3]),
                String.valueOf(r[4])
        )).toList();
    }

    // ── DTO records ───────────────────────────────────────────────────────────

    record SalesRepInfo(UUID id, String fullName, String username) {}

    record CustomerDashboardDto(
        long       totalOrders,
        long       pendingOrders,
        long       inProgressOrders,
        BigDecimal totalSpent
    ) {}

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
