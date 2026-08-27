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
        Instant deletedAt,
        UUID parentCustomerId,
        String parentCustomerName,
        int branchCount,
        UUID tenantId,
        String tenantCode,
        String tenantName
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
     *
     * {@code parentCustomerName} and {@code branchCount} default to null/0 here — accurate for
     * a brand-new customer (create/quickCreate), and harmless-but-stale on update (the mutation
     * response isn't what the Branches card reads; it re-fetches via the branches/branch-summary
     * endpoints). Callers needing accurate values for a batch of existing customers should use
     * {@link CustomerService#toDtos}, the only place that bulk-loads them.
     */
    public static CustomerDto from(Customer c, List<UUID> assignedProductIds, List<UUID> effectiveAssignedProductIds) {
        return from(c, assignedProductIds, effectiveAssignedProductIds, null, 0);
    }

    public static CustomerDto from(Customer c, List<UUID> assignedProductIds, List<UUID> effectiveAssignedProductIds,
                                    String parentCustomerName, int branchCount) {
        List<CustomerAddressDto> addrs = c.getAddresses().stream()
                .map(CustomerAddressDto::from)
                .toList();

        // Calling getId() on a lazy @ManyToOne proxy is safe and does not trigger initialization
        // (Hibernate proxies already know their own identifier) — unlike parentCustomerName,
        // which requires the association to actually be loaded (see toDtos()).
        UUID parentId = c.getParentCustomer() != null ? c.getParentCustomer().getId() : null;

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
                c.getDeletedAt(),
                parentId,
                parentCustomerName,
                branchCount,
                c.getTenant() != null ? c.getTenant().getId() : null,
                c.getTenant() != null ? c.getTenant().getCode() : null,
                c.getTenant() != null ? c.getTenant().getName() : null
        );
    }
}
