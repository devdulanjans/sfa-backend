package com.sfa.dto.publicsite;

import com.sfa.entity.SubscriptionPlan;

import java.math.BigDecimal;
import java.util.List;

/** A plan as the public website shows it. {@code id} is the plan's stable code (e.g. "standard"). */
public record PublicPlanDto(
        String id,
        String name,
        String description,
        BigDecimal pricePerUser,
        String currency,
        Integer maxOwners,
        /** Users besides the owner(s); null = unlimited. */
        Integer maxUsers,
        List<String> features,
        boolean highlighted
) {
    public static PublicPlanDto from(SubscriptionPlan p) {
        return new PublicPlanDto(p.getCode(), p.getName(), p.getDescription(), p.getPricePerUser(),
                p.getCurrency(), p.getMaxOwners(), p.getMaxUsers(),
                p.getFeatures() != null ? List.copyOf(p.getFeatures()) : List.of(), p.isHighlighted());
    }
}
