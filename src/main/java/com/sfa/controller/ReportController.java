package com.sfa.controller;

import com.sfa.dto.report.CustomerSalesDto;
import com.sfa.dto.report.SalesRepPerformanceDto;
import com.sfa.dto.report.SalesSummaryDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.SFA)
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/sales")
    public SalesSummaryDto salesSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.salesSummary(from, to);
    }

    @GetMapping("/performance")
    public List<SalesRepPerformanceDto> performance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.salesRepPerformance(from, to);
    }

    @GetMapping("/customers")
    public Page<CustomerSalesDto> customerSales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return reportService.customerSales(from, to, PageRequest.of(page, size));
    }
}
