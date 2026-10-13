package com.sfa.dto.lead;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateLeadRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String contactPerson,
        @Size(max = 20) String phone,
        @Size(max = 120) String email,
        @Size(max = 100) String location,
        @Size(max = 150) String placeOfSupplier,
        @Size(max = 500) String addressLine,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
        String notes,
        /** Only consulted for an actor who may pick a channel explicitly — see TenantAccessService. */
        UUID tenantId,
        /** When the rep actually captured the lead — a lead queued offline and synced later still
         *  counts for the route the rep was working that day. Optional (defaults to now). */
        java.time.Instant capturedAt
) {}
