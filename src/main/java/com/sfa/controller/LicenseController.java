package com.sfa.controller;

import com.sfa.dto.LicenseSettingsDto;
import com.sfa.dto.LicenseSettingsUpdateRequest;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.LicenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Deliberately outside the /api/customers, /api/orders, /api/pos, etc. path space, and
 * carries no @RequiresLicense itself — this must always be reachable by PLATFORM_OWNER
 * regardless of what's currently toggled off.
 */
@RestController
@RequestMapping("/api/platform/license")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_OWNER')")
    public LicenseSettingsDto get() {
        return licenseService.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('PLATFORM_OWNER')")
    public LicenseSettingsDto update(@Valid @RequestBody LicenseSettingsUpdateRequest req,
                                      @AuthenticationPrincipal UserDetailsImpl principal) {
        return licenseService.update(req, principal.getId());
    }
}
