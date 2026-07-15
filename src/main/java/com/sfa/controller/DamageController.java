package com.sfa.controller;

import com.sfa.dto.damage.CreateDamageRequest;
import com.sfa.entity.Damage;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.DamageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/damages")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class DamageController {

    private final DamageService damageService;

    @GetMapping
    public Page<Damage> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return damageService.list(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "damageDate")));
    }

    @PostMapping
    public ResponseEntity<Damage> create(@Valid @RequestBody CreateDamageRequest req) {
        Damage damage = damageService.create(req);
        return ResponseEntity.created(URI.create("/api/damages/" + damage.getId())).body(damage);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Damage updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return damageService.updateStatus(id, status);
    }
}
