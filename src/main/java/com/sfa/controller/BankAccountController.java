package com.sfa.controller;

import com.sfa.dto.BankAccountDto;
import com.sfa.dto.CreateBankAccountRequest;
import com.sfa.dto.JournalEntryLineDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/bank-accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class BankAccountController {

    private final BankAccountService bankAccountService;

    @GetMapping
    public List<BankAccountDto> list() {
        return bankAccountService.list();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_USER')")
    public BankAccountDto create(@RequestBody CreateBankAccountRequest request,
                                  @AuthenticationPrincipal UserDetailsImpl principal) {
        return bankAccountService.create(request, principal.getId());
    }

    @GetMapping("/{id}/balance")
    public BigDecimal balance(@PathVariable UUID id) {
        return bankAccountService.getBalance(id);
    }

    @GetMapping("/{id}/transactions")
    public List<JournalEntryLineDto> transactions(@PathVariable UUID id,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo) {
        return bankAccountService.getTransactions(id, dateFrom, dateTo);
    }
}
