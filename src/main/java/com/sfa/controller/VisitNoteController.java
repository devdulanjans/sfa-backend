package com.sfa.controller;

import com.sfa.dto.route.VisitNoteDtos.*;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.VisitNoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/visit-notes")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
@Tag(name = "Visit Notes", description = "Why a customer visit ended without an order")
public class VisitNoteController {

    private final VisitNoteService service;

    @PostMapping
    @PreAuthorize("hasRole('SALES_REP')")
    @Operation(summary = "Record (or update) today's no-order visit note for a customer")
    public VisitNoteDto record(@Valid @RequestBody RecordVisitNoteRequest req,
                               @AuthenticationPrincipal UserDetailsImpl user) {
        return service.record(req, user.getId());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('SALES_REP')")
    public List<VisitNoteDto> my(@AuthenticationPrincipal UserDetailsImpl user,
                                 @RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.myNotes(user.getId(), date != null ? date : LocalDate.now());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Visit notes report — filter by route, sales rep, channel, distributor, reason, customer")
    public Page<VisitNoteDto> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID routeId,
            @RequestParam(required = false) UUID salesRepId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID distributorId,
            @RequestParam(required = false) UUID reasonId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(from, to, routeId, salesRepId, tenantId, distributorId, reasonId, customerId, page, size);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Visit-note counts per reason for the same filters (reason filter not applied)")
    public List<ReasonCountDto> summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID routeId,
            @RequestParam(required = false) UUID salesRepId,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) UUID distributorId,
            @RequestParam(required = false) UUID customerId) {
        return service.summary(from, to, routeId, salesRepId, tenantId, distributorId, customerId);
    }
}
