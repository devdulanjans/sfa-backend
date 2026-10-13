package com.sfa.controller;

import com.sfa.dto.tracking.TrackingDtos.*;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.TrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Field tracking (V114): the rep's phone uploads GPS points; admins view paths, visits and live positions. */
@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
@Tag(name = "Field Tracking", description = "Sales rep location tracking and geofence visits")
public class TrackingController {

    private final TrackingService service;

    @GetMapping("/config")
    @PreAuthorize("hasRole('SALES_REP')")
    @Operation(summary = "Tracking settings for the mobile app (enabled, interval, distance filter)")
    public TrackingConfig config() {
        return service.config();
    }

    @PostMapping("/points")
    @PreAuthorize("hasRole('SALES_REP')")
    @Operation(summary = "Upload a batch of GPS points (idempotent per rep + timestamp)")
    public UploadResult upload(@Valid @RequestBody UploadRequest req, @AuthenticationPrincipal UserDetailsImpl user) {
        return service.upload(user.getId(), req);
    }

    @GetMapping("/live")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Latest position of each sales rep seen in the last 24 hours")
    public List<LiveRepDto> live() {
        return service.live();
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Per-rep totals for a day (points, distance, first/last seen, visits)")
    public List<RepSummaryDto> summary(@RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.summary(date != null ? date : LocalDate.now());
    }

    @GetMapping("/reps/{repId}/day")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "A rep's day: GPS path, geofence visits and distance")
    public RepDayDto repDay(@PathVariable UUID repId,
                            @RequestParam(required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.repDay(repId, date != null ? date : LocalDate.now());
    }
}
