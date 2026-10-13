package com.sfa.dto.tracking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for /api/tracking (V114). */
public final class TrackingDtos {

    private TrackingDtos() {}

    /** One GPS fix recorded by the rep's phone. */
    public record PointRequest(
            @NotNull Instant recordedAt,
            @NotNull BigDecimal latitude,
            @NotNull BigDecimal longitude,
            Float accuracy,
            Float speed,
            Float heading,
            Integer battery,
            Boolean isMock
    ) {}

    public record UploadRequest(@NotEmpty @Size(max = 1000) @Valid List<PointRequest> points) {}

    /** accepted = newly stored; duplicates (already uploaded) are ignored. */
    public record UploadResult(int received, int accepted) {}

    /** What the mobile app needs to run the tracker. */
    public record TrackingConfig(boolean enabled, int intervalSeconds, int distanceMeters) {}

    public record PointDto(Instant t, BigDecimal lat, BigDecimal lng, Float accuracy, Float speed,
                           Integer battery, boolean mock) {}

    public record VisitDto(
            UUID id,
            UUID customerId,
            String customerName,
            String customerCode,
            BigDecimal customerLat,
            BigDecimal customerLng,
            Instant arrivedAt,
            Instant departedAt,
            long durationMinutes,
            String status,            // OPEN (still there) / CLOSED
            Float minDistanceM,
            List<String> orderNumbers, // orders this rep placed for the customer that day
            String visitNoteReason     // the rep's no-order visit note that day, if any
    ) {}

    /** A rep's day: path, geofence visits and totals. */
    public record RepDayDto(
            UUID salesRepId,
            String salesRepName,
            LocalDate date,
            List<PointDto> points,
            List<VisitDto> visits,
            double distanceKm,
            Instant firstSeen,
            Instant lastSeen,
            int pointCount
    ) {}

    /** Latest known position of a rep (live view). */
    public record LiveRepDto(
            UUID salesRepId,
            String salesRepName,
            String username,
            Instant lastSeen,
            BigDecimal lat,
            BigDecimal lng,
            Float accuracy,
            Float speed,
            Integer battery,
            String currentCustomerName   // inside an OPEN geofence visit right now
    ) {}

    /** Per-rep totals for a day (overview table). */
    public record RepSummaryDto(
            UUID salesRepId,
            String salesRepName,
            int pointCount,
            double distanceKm,
            Instant firstSeen,
            Instant lastSeen,
            int visitCount
    ) {}
}
