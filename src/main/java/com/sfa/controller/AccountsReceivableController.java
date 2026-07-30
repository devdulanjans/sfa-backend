package com.sfa.controller;

import com.sfa.dto.ReceivableCustomerDto;
import com.sfa.dto.ReceivableInvoiceDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.AccountsReceivableService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/receivables")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class AccountsReceivableController {

    private final AccountsReceivableService receivableService;

    @GetMapping("/customers")
    public List<ReceivableCustomerDto> listOutstandingByCustomer() {
        return receivableService.listOutstandingByCustomer();
    }

    @GetMapping("/customers/{customerId}/invoices")
    public List<ReceivableInvoiceDto> listOutstandingForCustomer(@PathVariable UUID customerId) {
        return receivableService.listOutstandingForCustomer(customerId);
    }
}
