package com.sfa.dto.customer;

import com.sfa.entity.Customer;
import org.hibernate.Hibernate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerDto(
        UUID id,
        String customerCode,
        String name,
        String contactPerson,
        String phone,
        String email,
        String location,
        String placeOfSupplier,
        String taxNumber,
        String taxType,
        Double taxRate,
        String categoryName,
        String visibilityRule,
        String status,
        Double creditLimit,
        Integer creditDays,
        Double currentBalance,
        String source,
        List<UUID> assignedProductIds,
        List<UUID> effectiveAssignedProductIds,
        List<CustomerAddressDto> addresses,
        Instant deletedAt
) {
    public static CustomerDto from(Customer c) {
        List<UUID> productIds = Hibernate.isInitialized(c.getAssignedProducts())
                ? c.getAssignedProducts().stream().map(p -> p.getId()).toList()
                : List.of();
        return from(c, productIds);
    }

    /**
     * Use this overload whenever assigned-product IDs were already bulk-loaded
     * for a batch of customers (e.g. list/sync endpoints) — {@code assignedProducts}
     * is a lazy @ManyToMany, so touching it per-row here would either N+1 or
     * (per the no-arg overload's isInitialized guard) silently come back empty.
     *
     * {@code effectiveAssignedProductIds} is not computed here (no customer-group
     * context available at this call site) — it defaults to the same direct-only
     * list. Callers that need the group-inclusive value should use
     * {@link CustomerService#toDtos} instead, which is the only place that unions
     * in a customer's customer-group assignments.
     */
    public static CustomerDto from(Customer c, List<UUID> assignedProductIds) {
        return from(c, assignedProductIds, assignedProductIds);
    }

    /**
     * {@code effectiveAssignedProductIds} is the customer's own {@code assignedProductIds}
     * (direct-only — this is also what the admin "Select Products" picker reads and writes
     * back, so it must never silently include group-derived products) unioned with every
     * customer group the customer belongs to. Mobile's order-creation catalog filter reads
     * this field specifically so group-assigned products restrict the catalog exactly like
     * direct assignments already do.
     */
    public static CustomerDto from(Customer c, List<UUID> assignedProductIds, List<UUID> effectiveAssignedProductIds) {
        List<CustomerAddressDto> addrs = c.getAddresses().stream()
                .map(CustomerAddressDto::from)
                .toList();

        return new CustomerDto(
                c.getId(),
                c.getCustomerCode(),
                c.getName(),
                c.getContactPerson(),
                c.getPhone(),
                c.getEmail(),
                c.getLocation(),
                c.getPlaceOfSupplier(),
                c.getTaxNumber(),
                c.getTaxType() != null ? c.getTaxType().name() : null,
                c.getTaxRate() != null ? c.getTaxRate().doubleValue() : null,
                c.getCategory() != null ? c.getCategory().getName() : null,
                c.getVisibilityRule() != null ? c.getVisibilityRule().name() : null,
                c.getStatus() != null ? c.getStatus().name() : null,
                c.getCreditLimit() != null ? c.getCreditLimit().doubleValue() : null,
                c.getCreditDays(),
                c.getCurrentBalance() != null ? c.getCurrentBalance().doubleValue() : null,
                c.getSource() != null ? c.getSource().name() : null,
                assignedProductIds,
                effectiveAssignedProductIds,
                addrs,
                c.getDeletedAt()
        );
    }
}
