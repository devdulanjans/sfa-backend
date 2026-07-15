package com.sfa.service;

import com.sfa.dto.report.CustomerSalesDto;
import com.sfa.dto.report.SalesRepPerformanceDto;
import com.sfa.dto.report.SalesSummaryDto;
import com.sfa.repository.InvoiceRepository;
import com.sfa.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrderRepository orderRepository;
    private final InvoiceRepository invoiceRepository;

    public SalesSummaryDto salesSummary(LocalDate from, LocalDate to) {
        BigDecimal totalRevenue = orderRepository.totalRevenueBetween(from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        long totalOrders = orderRepository.countBetween(
                from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));

        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        long invoicesCount = invoiceRepository.countIssuedBetween(
                from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));

        List<SalesSummaryDto.DailyRevenueDto> dailyRevenue =
                orderRepository.dailyRevenueBetween(
                        from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
                        to.plusDays(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC));

        return new SalesSummaryDto(totalRevenue, totalOrders, avgOrderValue, invoicesCount, dailyRevenue);
    }

    public Page<CustomerSalesDto> customerSales(LocalDate from, LocalDate to, Pageable pageable) {
        return orderRepository.customerSalesBetween(
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC),
                pageable);
    }

    public List<SalesRepPerformanceDto> salesRepPerformance(LocalDate from, LocalDate to) {
        return orderRepository.topSalesRepsByRevenue(
                from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        ).stream().map(row -> {
            BigDecimal revenue = row[2] instanceof BigDecimal bd ? bd : new BigDecimal(row[2].toString());
            BigDecimal avg     = row[4] instanceof BigDecimal bd ? bd : new BigDecimal(row[4].toString());
            return new SalesRepPerformanceDto(
                    UUID.fromString(row[0].toString()),
                    row[1].toString(),
                    revenue,
                    ((Number) row[3]).longValue(),
                    avg
            );
        }).toList();
    }
}
