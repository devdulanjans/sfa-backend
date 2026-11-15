package com.sfa.controller;

import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.CollectionService;
import com.sfa.service.CollectionService.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Amounts due and payments collected on a visit (V122). Sales reps see what their customers
 * owe and record what they collect, in full or in part; owners and managers review the
 * collections. ADMIN inherits SALES_MANAGER.
 */
@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
@Tag(name = "Collections")
@SecurityRequirement(name = "bearerAuth")
@RequiresLicense(LicensedPackage.SFA)
public class CollectionController {

    public record ReviewRequest(boolean reviewed) {}

    private final CollectionService service;

    @GetMapping("/balances")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP','FINANCE_USER')")
    @Operation(summary = "Amount due per customer the caller can see")
    public List<CustomerDue> balances(@AuthenticationPrincipal UserDetailsImpl user) {
        return service.balances(user);
    }

    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP','FINANCE_USER')")
    @Operation(summary = "A customer's open invoices and latest collections")
    public CustomerDueDetail customer(@PathVariable UUID customerId, @AuthenticationPrincipal UserDetailsImpl user) {
        return service.customerDue(customerId, user);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
    @Operation(summary = "Record money collected from a customer (idempotent on clientRef)")
    public CollectionDto collect(@RequestBody CollectRequest request, @AuthenticationPrincipal UserDetailsImpl user) {
        return service.collect(request, user);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
    @Operation(summary = "Collections recorded by reps, newest first")
    public CollectionList list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) UUID collectedBy,
                               @RequestParam(required = false) UUID customerId,
                               @RequestParam(defaultValue = "false") boolean unreviewed) {
        return service.list(from, to, collectedBy, customerId, unreviewed);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
    public CollectionDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
    @Operation(summary = "Mark a collection as checked (or unchecked)")
    public CollectionDto review(@PathVariable UUID id, @RequestBody ReviewRequest req,
                                @AuthenticationPrincipal UserDetailsImpl user) {
        return service.markReviewed(id, req.reviewed(), user.getId());
    }
}
