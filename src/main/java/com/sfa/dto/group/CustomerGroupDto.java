package com.sfa.dto.group;

import com.sfa.entity.CustomerGroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerGroupDto(
        UUID id,
        String name,
        String description,
        int memberCount,
        List<MemberSummary> members,
        int assignedProductCount,
        List<ProductSummary> assignedProducts,
        Instant createdAt,
        Instant updatedAt
) {
    public record MemberSummary(UUID id, String name, String customerCode) {}
    public record ProductSummary(UUID id, String name, String productCode) {}

    /** Used for list views — counts only, no per-row member/product fetch. */
    public static CustomerGroupDto summary(CustomerGroup g, long memberCount, long assignedProductCount) {
        return new CustomerGroupDto(g.getId(), g.getName(), g.getDescription(),
                (int) memberCount, null, (int) assignedProductCount, null,
                g.getCreatedAt(), g.getUpdatedAt());
    }

    /** Used for the single-group detail view — includes the full member and assigned-product lists. */
    public static CustomerGroupDto withMembers(CustomerGroup g) {
        List<MemberSummary> members = g.getMembers().stream()
                .map(c -> new MemberSummary(c.getId(), c.getName(), c.getCustomerCode()))
                .toList();
        List<ProductSummary> products = g.getAssignedProducts().stream()
                .map(p -> new ProductSummary(p.getId(), p.getName(), p.getProductCode()))
                .toList();
        return new CustomerGroupDto(g.getId(), g.getName(), g.getDescription(),
                members.size(), members, products.size(), products,
                g.getCreatedAt(), g.getUpdatedAt());
    }
}
