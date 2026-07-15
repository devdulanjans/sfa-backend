package com.sfa.controller;

import com.sfa.dto.distributor.CreateDistributorRequest;
import com.sfa.dto.distributor.DistributorDto;
import com.sfa.service.DistributorService;
import com.sfa.security.UserDetailsImpl;
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
@RequestMapping("/api/distributors")
@RequiredArgsConstructor
public class DistributorController {

    private final DistributorService distributorService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<DistributorDto> list(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size,
            @RequestParam(required = false)     String search) {
        return distributorService.list(search, PageRequest.of(page, size, Sort.by("name")));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public DistributorDto getById(@PathVariable UUID id) {
        return distributorService.getById(id);
    }

    @GetMapping("/by-user/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public List<DistributorDto> getByUser(@PathVariable UUID userId) {
        return distributorService.getByUser(userId);
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public List<DistributorDto> getMy(@AuthenticationPrincipal UserDetailsImpl principal) {
        return distributorService.getByUser(principal.getId());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DistributorDto> create(@Valid @RequestBody CreateDistributorRequest req) {
        DistributorDto dto = distributorService.create(req);
        return ResponseEntity.created(URI.create("/api/distributors/" + dto.id())).body(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public DistributorDto update(@PathVariable UUID id, @Valid @RequestBody CreateDistributorRequest req) {
        return distributorService.update(id, req);
    }

    @PatchMapping("/{id}/toggle-status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public DistributorDto toggleStatus(@PathVariable UUID id) {
        return distributorService.toggleStatus(id);
    }

    @PostMapping("/{id}/users/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> assignUser(@PathVariable UUID id, @PathVariable UUID userId) {
        distributorService.assignUser(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/users/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> unassignUser(@PathVariable UUID id, @PathVariable UUID userId) {
        distributorService.unassignUser(id, userId);
        return ResponseEntity.noContent().build();
    }
}
