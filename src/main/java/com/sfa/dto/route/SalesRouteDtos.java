package com.sfa.dto.route;

import com.sfa.entity.Customer;
import com.sfa.entity.SalesRoute;
import com.sfa.entity.SalesRouteAssignment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request/response shapes for /api/sales-routes. */
public final class SalesRouteDtos {

    private SalesRouteDtos() {}

    /** One stop on a route (or a customer pin on the map). */
    public record RouteCustomerDto(
            UUID id,
            String customerCode,
            String name,
            String location,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            Integer stopNumber      // 1-based visit order; null outside a route
    ) {
        public static RouteCustomerDto from(Customer c, Integer stopNumber) {
            return new RouteCustomerDto(c.getId(), c.getCustomerCode(), c.getName(), c.getLocation(),
                    c.getPhone(), c.getLatitude(), c.getLongitude(), stopNumber);
        }
    }

    public record RouteSummaryDto(
            UUID id,
            String name,
            String description,
            String status,
            int customerCount,
            UUID tenantId,
            String tenantName,
            Instant createdAt,
            /** Customers suggested for this route by reps (V113) and not yet added or dismissed. */
            int suggestionCount
    ) {
        public static RouteSummaryDto from(SalesRoute r, int customerCount) {
            return from(r, customerCount, 0);
        }

        public static RouteSummaryDto from(SalesRoute r, int customerCount, int suggestionCount) {
            return new RouteSummaryDto(r.getId(), r.getName(), r.getDescription(), r.getStatus().name(),
                    customerCount,
                    r.getTenant() != null ? r.getTenant().getId() : null,
                    r.getTenant() != null ? r.getTenant().getName() : null,
                    r.getCreatedAt(), suggestionCount);
        }
    }

    /** A customer a rep suggested for a route (captured as a lead while working it). */
    public record RouteSuggestionDto(
            RouteCustomerDto customer,
            String suggestedByName,
            Instant suggestedAt
    ) {
        public static RouteSuggestionDto from(Customer c) {
            return new RouteSuggestionDto(RouteCustomerDto.from(c, null),
                    c.getSuggestedBy() != null ? c.getSuggestedBy().getFullName() : null,
                    c.getSuggestedAt());
        }
    }

    public record RouteDetailDto(
            UUID id,
            String name,
            String description,
            String status,
            UUID tenantId,
            String tenantName,
            List<RouteCustomerDto> customers,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static RouteDetailDto from(SalesRoute r) {
            List<Customer> stops = r.getCustomers();
            List<RouteCustomerDto> customers = new java.util.ArrayList<>();
            for (int i = 0; i < stops.size(); i++) {
                customers.add(RouteCustomerDto.from(stops.get(i), i + 1));
            }
            return new RouteDetailDto(r.getId(), r.getName(), r.getDescription(), r.getStatus().name(),
                    r.getTenant() != null ? r.getTenant().getId() : null,
                    r.getTenant() != null ? r.getTenant().getName() : null,
                    customers, r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    /**
     * @param customerIds the stops in visit order
     * @param tenantId    which channel the route belongs to — required for SUPER_ADMIN, optional
     *                    for a multi-channel admin, ignored otherwise (see TenantAccessService)
     */
    public record SaveRouteRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            String status,
            List<UUID> customerIds,
            UUID tenantId
    ) {}

    public record AssignmentDto(
            UUID id,
            LocalDate date,
            UUID routeId,
            String routeName,
            String routeStatus,
            UUID salesRepId,
            String salesRepName,
            String notes,
            String assignedByName,
            Instant createdAt
    ) {
        public static AssignmentDto from(SalesRouteAssignment a) {
            return new AssignmentDto(a.getId(), a.getAssignmentDate(),
                    a.getRoute().getId(), a.getRoute().getName(), a.getRoute().getStatus().name(),
                    a.getSalesRep().getId(),
                    a.getSalesRep().getFullName() != null ? a.getSalesRep().getFullName() : a.getSalesRep().getUsername(),
                    a.getNotes(),
                    a.getAssignedBy() != null
                            ? (a.getAssignedBy().getFullName() != null ? a.getAssignedBy().getFullName() : a.getAssignedBy().getUsername())
                            : null,
                    a.getCreatedAt());
        }
    }

    /**
     * Assign a route to a rep for one or more days. An existing assignment for the rep on any
     * of those days is replaced (one route per rep per day).
     */
    public record AssignRouteRequest(
            @NotNull UUID routeId,
            @NotNull UUID salesRepId,
            @NotEmpty List<LocalDate> dates,
            @Size(max = 500) String notes
    ) {}

    /**
     * The rep's route for a day — {@code assignment}/{@code route} null when none is assigned,
     * and always null while the global {@code sales_routes_enabled} setting is off
     * ({@code enabled} false), in which case routes aren't applied to reps at all.
     */
    public record MyRouteDto(
            LocalDate date,
            AssignmentDto assignment,
            RouteDetailDto route,
            boolean enabled,
            /** One entry per stop that was ordered and/or got a no-order visit note that day. */
            List<VisitNoteDtos.StopStatusDto> stopStatuses
    ) {}
}
