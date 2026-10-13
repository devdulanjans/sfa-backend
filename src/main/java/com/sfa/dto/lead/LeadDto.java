package com.sfa.dto.lead;

import com.sfa.entity.Lead;

import java.time.Instant;
import java.util.UUID;

public record LeadDto(
        UUID id,
        String leadNumber,
        String name,
        String contactPerson,
        String phone,
        String email,
        String location,
        String placeOfSupplier,
        String addressLine,
        Double latitude,
        Double longitude,
        String notes,
        String status,
        UUID createdById,
        String createdByName,
        UUID convertedCustomerId,
        String convertedCustomerCode,
        String convertedCustomerName,
        String reviewedByName,
        Instant reviewedAt,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt,
        UUID tenantId,
        String tenantName,
        UUID suggestedRouteId,
        String suggestedRouteName
) {
    public static LeadDto from(Lead l) {
        var createdBy = l.getCreatedBy();
        var customer  = l.getConvertedCustomer();
        var reviewer  = l.getReviewedBy();
        return new LeadDto(
                l.getId(),
                l.getLeadNumber(),
                l.getName(),
                l.getContactPerson(),
                l.getPhone(),
                l.getEmail(),
                l.getLocation(),
                l.getPlaceOfSupplier(),
                l.getAddressLine(),
                l.getLatitude() != null ? l.getLatitude().doubleValue() : null,
                l.getLongitude() != null ? l.getLongitude().doubleValue() : null,
                l.getNotes(),
                l.getStatus().name(),
                createdBy != null ? createdBy.getId() : null,
                createdBy != null ? createdBy.getFullName() : null,
                customer != null ? customer.getId() : null,
                customer != null ? customer.getCustomerCode() : null,
                customer != null ? customer.getName() : null,
                reviewer != null ? reviewer.getFullName() : null,
                l.getReviewedAt(),
                l.getRejectionReason(),
                l.getCreatedAt(),
                l.getUpdatedAt(),
                l.getTenant() != null ? l.getTenant().getId() : null,
                l.getTenant() != null ? l.getTenant().getName() : null,
                l.getSuggestedRoute() != null ? l.getSuggestedRoute().getId() : null,
                l.getSuggestedRoute() != null ? l.getSuggestedRoute().getName() : null
        );
    }
}
