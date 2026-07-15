package com.sfa.controller;

import com.sfa.dto.PosDashboardDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.PosDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/pos/dashboard")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.POS)
public class PosDashboardController {

    private final PosDashboardService posDashboardService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public PosDashboardDto dashboard(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return posDashboardService.getDashboard(from, to);
    }
}
