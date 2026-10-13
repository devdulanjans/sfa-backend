package com.sfa.controller;

import com.sfa.dto.route.SalesRouteDtos.*;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.SalesRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Sales routes: admins create routes (ordered customer stops) and assign them to sales reps per
 * day; a rep reads their own route for the day via {@code /my}.
 */
@RestController
@RequestMapping("/api/sales-routes")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
@Tag(name = "Sales Routes", description = "Routes, their customers, and per-day assignment to sales reps")
public class SalesRouteController {

    private final SalesRouteService service;

    // ── Rep ─────────────────────────────────────────────────────────────────────

    @GetMapping("/my")
    @PreAuthorize("hasRole('SALES_REP')")
    @Operation(summary = "The calling sales rep's route for a day (default today)")
    public MyRouteDto myRoute(@AuthenticationPrincipal UserDetailsImpl user,
                              @RequestParam(required = false)
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.myRoute(user.getId(), date != null ? date : LocalDate.now());
    }

    // ── Admin: routes ──────────────────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<RouteSummaryDto> list(@RequestParam(defaultValue = "") String search,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.search(search, status, page, size);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "All active routes — for pickers")
    public List<RouteSummaryDto> active() {
        return service.activeRoutes();
    }

    @GetMapping("/customer-locations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Customers with their map position — for the route editor / assign map")
    public List<RouteCustomerDto> customerLocations(@RequestParam(required = false) UUID tenantId) {
        return service.customerLocations(tenantId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public RouteDetailDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<RouteDetailDto> create(@Valid @RequestBody SaveRouteRequest req,
                                                 @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, user.getId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public RouteDetailDto update(@PathVariable UUID id, @Valid @RequestBody SaveRouteRequest req,
                                 @AuthenticationPrincipal UserDetailsImpl user) {
        return service.update(id, req, user.getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl user) {
        service.delete(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Admin: route suggestions (V113) ────────────────────────────────────────

    @GetMapping("/{id}/suggestions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Customers reps suggested for this route (captured as leads while working it)")
    public List<RouteSuggestionDto> suggestions(@PathVariable UUID id) {
        return service.suggestions(id);
    }

    @PostMapping("/{id}/suggestions/{customerId}/accept")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Accept a suggestion — the customer becomes the route's next stop")
    public RouteDetailDto acceptSuggestion(@PathVariable UUID id, @PathVariable UUID customerId,
                                           @AuthenticationPrincipal UserDetailsImpl user) {
        return service.acceptSuggestion(id, customerId, user.getId());
    }

    @DeleteMapping("/{id}/suggestions/{customerId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Dismiss a suggestion without adding the customer")
    public ResponseEntity<Void> dismissSuggestion(@PathVariable UUID id, @PathVariable UUID customerId,
                                                  @AuthenticationPrincipal UserDetailsImpl user) {
        service.dismissSuggestion(id, customerId, user.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Admin: assignments ─────────────────────────────────────────────────────

    @GetMapping("/assignments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public List<AssignmentDto> assignments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID salesRepId,
            @RequestParam(required = false) UUID routeId) {
        return service.assignments(from, to, salesRepId, routeId);
    }

    @PostMapping("/assignments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Assign a route to a sales rep on one or more days (replaces that day's route)")
    public List<AssignmentDto> assign(@Valid @RequestBody AssignRouteRequest req,
                                      @AuthenticationPrincipal UserDetailsImpl user) {
        return service.assign(req, user.getId());
    }

    @DeleteMapping("/assignments/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<Void> unassign(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl user) {
        service.unassign(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
