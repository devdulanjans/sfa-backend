package com.sfa.controller;

import com.sfa.dto.BalanceSheetDto;
import com.sfa.dto.FinanceProfitLossDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.FinancialReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/finance/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class FinancialReportController {

    private final FinancialReportService reportService;

    @GetMapping("/profit-loss")
    public FinanceProfitLossDto profitAndLoss(
            @RequestParam LocalDate dateFrom,
            @RequestParam LocalDate dateTo) {
        return reportService.getProfitAndLoss(dateFrom, dateTo);
    }

    @GetMapping("/balance-sheet")
    public BalanceSheetDto balanceSheet(@RequestParam(required = false) LocalDate asOfDate) {
        return reportService.getBalanceSheet(asOfDate);
    }
}
