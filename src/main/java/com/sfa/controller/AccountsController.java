package com.sfa.controller;

import com.sfa.dto.LedgerEntryDto;
import com.sfa.dto.ProfitLossDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.AccountsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.POS)
public class AccountsController {

    private final AccountsService accountsService;

    @GetMapping("/ledger")
    public List<LedgerEntryDto> ledger(@RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return accountsService.getLedger(dateFrom, dateTo);
    }

    @GetMapping("/profit-loss")
    public ProfitLossDto profitLoss(@RequestParam LocalDate dateFrom, @RequestParam LocalDate dateTo) {
        return accountsService.getProfitLoss(dateFrom, dateTo);
    }
}
