package com.sfa.controller;

import com.sfa.dto.tenant.CreateTenantRequest;
import com.sfa.dto.tenant.TenantDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.MULTI_TENANT)
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Page<TenantDto> list(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(required = false)     String search) {
        return tenantService.list(search, PageRequest.of(page, size, Sort.by("name")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TenantDto getById(@PathVariable UUID id) {
        return tenantService.getById(id);
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<TenantDto> getByUser(@PathVariable UUID userId) {
        return tenantService.getByUser(userId);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<TenantDto> getMy(@AuthenticationPrincipal UserDetailsImpl principal) {
        return tenantService.getByUser(principal.getId());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<TenantDto> create(@Valid @RequestBody CreateTenantRequest req) {
        TenantDto dto = tenantService.create(req);
        return ResponseEntity.created(URI.create("/api/tenants/" + dto.id())).body(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TenantDto update(@PathVariable UUID id, @Valid @RequestBody CreateTenantRequest req) {
        return tenantService.update(id, req);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TenantDto toggleStatus(@PathVariable UUID id) {
        return tenantService.toggleStatus(id);
    }

    @PostMapping("/{id}/users/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> assignUser(
            @PathVariable UUID id, @PathVariable UUID userId,
            @RequestParam(defaultValue = "false") boolean asDefault) {
        tenantService.assignUser(id, userId, asDefault);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/users/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> unassignUser(@PathVariable UUID id, @PathVariable UUID userId) {
        tenantService.unassignUser(id, userId);
        return ResponseEntity.noContent().build();
    }
}
