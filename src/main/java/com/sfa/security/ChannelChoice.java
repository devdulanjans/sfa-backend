package com.sfa.security;

import com.sfa.entity.MultiChannelScoped;

import java.util.Comparator;
import java.util.UUID;

/**
 * Which channel an order line for a product goes into, now that a product can be available in
 * several channels (V112). Shared by the customer-app catalog (which prices each product in that
 * channel) and checkout (which splits the cart per channel), so both always agree.
 */
public final class ChannelChoice {

    private ChannelChoice() {}

    /**
     * The session's active channel when the product is available there; otherwise the first
     * (stable order) of the product's channels this session can see; null if none.
     */
    public static UUID orderChannelFor(MultiChannelScoped product) {
        return orderChannelFor(product.getChannelIds());
    }

    public static UUID orderChannelFor(java.util.Collection<UUID> channelIds) {
        UUID active = TenantContext.getTenantId();
        if (active != null && channelIds.contains(active)) {
            return active;
        }
        return channelIds.stream()
                .filter(TenantContext::isVisible)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }
}
