package com.sfa.controller;

import com.sfa.dto.CreateVendorRequest;
import com.sfa.dto.VendorDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.VendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/finance/vendors")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    public Page<VendorDto> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return vendorService.list(search, PageRequest.of(page, size, Sort.by("name")));
    }

    @PostMapping
    public VendorDto create(@RequestBody CreateVendorRequest request) {
        return vendorService.create(request);
    }

    @PutMapping("/{id}")
    public VendorDto update(@PathVariable UUID id, @RequestBody CreateVendorRequest request) {
        return vendorService.update(id, request);
    }

    @PutMapping("/{id}/active")
    public void setActive(@PathVariable UUID id, @RequestBody java.util.Map<String, Boolean> body) {
        vendorService.setActive(id, Boolean.TRUE.equals(body.get("active")));
    }
}
