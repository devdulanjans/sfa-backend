package com.sfa.dto.customer;

import com.sfa.entity.Customer;
import org.hibernate.Hibernate;

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
        List<CustomerAddressDto> addresses
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
     */
    public static CustomerDto from(Customer c, List<UUID> assignedProductIds) {
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
                addrs
        );
    }
}
