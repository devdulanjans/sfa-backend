package com.sfa.controller;

import com.sfa.dto.ret.CreateReturnRequest;
import com.sfa.entity.Return;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.ReturnService;
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
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class ReturnController {

    private final ReturnService returnService;

    @GetMapping
    public Page<Return> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return returnService.list(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping
    public ResponseEntity<Return> create(@Valid @RequestBody CreateReturnRequest req) {
        Return ret = returnService.create(req);
        return ResponseEntity.created(URI.create("/api/returns/" + ret.getId())).body(ret);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Return updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return returnService.updateStatus(id, status);
    }
}
