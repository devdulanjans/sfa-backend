package com.sfa.controller;

import com.sfa.dto.CreateFixedAssetRequest;
import com.sfa.dto.FixedAssetDepreciationDto;
import com.sfa.dto.FixedAssetDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.FixedAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/fixed-assets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class FixedAssetController {

    private final FixedAssetService fixedAssetService;

    @GetMapping
    public List<FixedAssetDto> list() {
        return fixedAssetService.list();
    }

    @PostMapping
    public FixedAssetDto create(@RequestBody CreateFixedAssetRequest request,
                                 @AuthenticationPrincipal UserDetailsImpl principal) {
        return fixedAssetService.create(request, principal.getId());
    }

    @GetMapping("/{id}/depreciation")
    public List<FixedAssetDepreciationDto> depreciationHistory(@PathVariable UUID id) {
        return fixedAssetService.listDepreciation(id);
    }

    @PostMapping("/depreciation/run")
    public List<FixedAssetDepreciationDto> runDepreciation(@RequestBody Map<String, String> body,
                                                            @AuthenticationPrincipal UserDetailsImpl principal) {
        LocalDate periodDate = LocalDate.parse(body.get("periodDate"));
        return fixedAssetService.runDepreciation(periodDate, principal.getId());
    }
}
