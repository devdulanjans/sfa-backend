package com.sfa.controller;

import com.sfa.dto.JournalEntryLineDto;
import com.sfa.dto.RecordTaxPaymentRequest;
import com.sfa.dto.TaxPayableSummaryDto;
import com.sfa.dto.TaxPaymentDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.TaxManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/finance/tax")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class TaxManagementController {

    private final TaxManagementService taxManagementService;

    @GetMapping("/payable")
    public TaxPayableSummaryDto payableSummary() {
        return taxManagementService.getPayableSummary();
    }

    @GetMapping("/ledger")
    public List<JournalEntryLineDto> ledger(
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo) {
        return taxManagementService.getLedger(dateFrom, dateTo);
    }

    @GetMapping("/payments")
    public List<TaxPaymentDto> payments() {
        return taxManagementService.listPayments();
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_USER')")
    public TaxPaymentDto recordPayment(@RequestBody RecordTaxPaymentRequest request,
                                        @AuthenticationPrincipal UserDetailsImpl principal) {
        return taxManagementService.recordPayment(request, principal.getId());
    }
}
