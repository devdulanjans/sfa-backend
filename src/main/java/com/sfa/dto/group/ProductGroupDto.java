package com.sfa.dto.group;

import com.sfa.entity.ProductGroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProductGroupDto(
        UUID id,
        String name,
        String description,
        int memberCount,
        List<MemberSummary> members,
        Instant createdAt,
        Instant updatedAt
) {
    public record MemberSummary(UUID id, String name, String productCode) {}

    /** Used for list views — member count only, no per-row member fetch. */
    public static ProductGroupDto summary(ProductGroup g, long memberCount) {
        return new ProductGroupDto(g.getId(), g.getName(), g.getDescription(),
                (int) memberCount, null, g.getCreatedAt(), g.getUpdatedAt());
    }

    /** Used for the single-group detail view — includes the full member list. */
    public static ProductGroupDto withMembers(ProductGroup g) {
        List<MemberSummary> members = g.getMembers().stream()
                .map(p -> new MemberSummary(p.getId(), p.getName(), p.getProductCode()))
                .toList();
        return new ProductGroupDto(g.getId(), g.getName(), g.getDescription(),
                members.size(), members, g.getCreatedAt(), g.getUpdatedAt());
    }
}
