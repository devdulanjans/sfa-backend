package com.sfa.controller;

import com.sfa.dto.JournalEntryDto;
import com.sfa.dto.PostJournalEntryRequest;
import com.sfa.entity.JournalEntry;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.JournalEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/journal-entries")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class JournalEntryController {

    private final JournalEntryService journalEntryService;

    @GetMapping
    public Page<JournalEntryDto> list(
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) JournalEntry.SourceType sourceType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return journalEntryService.list(dateFrom, dateTo, sourceType,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "entryDate")));
    }

    @GetMapping("/{id}")
    public JournalEntryDto get(@PathVariable UUID id) {
        return journalEntryService.get(id);
    }

    @PostMapping
    public JournalEntryDto create(@RequestBody PostJournalEntryRequest request,
                                   @AuthenticationPrincipal UserDetailsImpl principal) {
        JournalEntry entry = journalEntryService.postManualEntry(request, principal.getId());
        return journalEntryService.get(entry.getId());
    }

    @PostMapping("/{id}/void")
    public JournalEntryDto voidEntry(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl principal) {
        journalEntryService.voidEntry(id, principal.getId());
        return journalEntryService.get(id);
    }
}
