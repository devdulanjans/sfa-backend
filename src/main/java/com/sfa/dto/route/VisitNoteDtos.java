package com.sfa.dto.route;

import com.sfa.entity.User;
import com.sfa.entity.VisitNote;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for /api/visit-notes and the per-stop status in MyRouteDto. */
public final class VisitNoteDtos {

    private VisitNoteDtos() {}

    /**
     * A no-order visit. {@code visitedAt} lets a note recorded offline keep the time it was
     * actually taken (it decides which day the note counts for); {@code distributorId} is the
     * rep's active distributor, if they work under one.
     */
    public record RecordVisitNoteRequest(
            @NotNull UUID customerId,
            @NotNull UUID reasonId,
            @Size(max = 1000) String note,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant visitedAt,
            UUID distributorId
    ) {}

    public record VisitNoteDto(
            UUID id,
            LocalDate visitDate,
            Instant visitedAt,
            UUID customerId,
            String customerName,
            String customerCode,
            UUID salesRepId,
            String salesRepName,
            UUID routeId,
            String routeName,
            UUID distributorId,
            String distributorName,
            UUID tenantId,
            String tenantName,
            UUID reasonId,
            String reasonLabel,
            String note,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant updatedAt
    ) {
        public static VisitNoteDto from(VisitNote v) {
            return new VisitNoteDto(v.getId(), v.getVisitDate(), v.getVisitedAt(),
                    v.getCustomer().getId(), v.getCustomer().getName(), v.getCustomer().getCustomerCode(),
                    v.getSalesRep().getId(), name(v.getSalesRep()),
                    v.getRoute() != null ? v.getRoute().getId() : null,
                    v.getRoute() != null ? v.getRoute().getName() : null,
                    v.getDistributor() != null ? v.getDistributor().getId() : null,
                    v.getDistributor() != null ? v.getDistributor().getName() : null,
                    v.getTenant() != null ? v.getTenant().getId() : null,
                    v.getTenant() != null ? v.getTenant().getName() : null,
                    v.getReason() != null ? v.getReason().getId() : null,
                    v.getReasonLabel(), v.getNote(), v.getLatitude(), v.getLongitude(),
                    v.getUpdatedAt() != null ? v.getUpdatedAt() : v.getCreatedAt());
        }

        private static String name(User u) {
            return u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername();
        }
    }

    public record ReasonCountDto(String reason, long count) {}

    /**
     * How far a route stop got today: ordered (with the order numbers) and/or a no-order visit
     * note. Neither → not visited yet.
     */
    public record StopStatusDto(
            UUID customerId,
            boolean ordered,
            List<String> orderNumbers,
            VisitNoteDto visitNote
    ) {}
}
