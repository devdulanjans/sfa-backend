package com.sfa.controller;

import com.sfa.dto.ChartOfAccountDto;
import com.sfa.dto.CreateChartOfAccountRequest;
import com.sfa.dto.JournalEntryLineDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.ChartOfAccountService;
import com.sfa.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class ChartOfAccountController {

    private final ChartOfAccountService accountService;
    private final JournalEntryService journalEntryService;

    @GetMapping
    public List<ChartOfAccountDto> list() {
        return accountService.list();
    }

    @PostMapping
    public ChartOfAccountDto create(@RequestBody CreateChartOfAccountRequest request) {
        return accountService.create(request);
    }

    @PutMapping("/{id}")
    public ChartOfAccountDto update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return accountService.update(id, (String) body.get("accountName"),
                Boolean.TRUE.equals(body.getOrDefault("active", true)));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        accountService.delete(id);
    }

    @GetMapping("/{id}/ledger")
    public List<JournalEntryLineDto> ledger(@PathVariable UUID id,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo) {
        return journalEntryService.getAccountLedger(id, dateFrom, dateTo);
    }
}
