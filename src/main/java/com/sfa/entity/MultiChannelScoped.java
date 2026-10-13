package com.sfa.entity;

import java.util.Set;
import java.util.UUID;

/**
 * A row that can be available in several channels at once (customers, products, promotions,
 * batch prices — see V112). {@link #getTenant()} is its "home" channel; {@link #getChannelIds()}
 * lists every channel it's available in (always including home once saved), and is what the
 * tenant filter and {@link com.sfa.security.TenantGuard} check.
 */
public interface MultiChannelScoped extends TenantScoped {

    Set<UUID> getChannelIds();

    /** Keeps home ∈ channels — called on create so a new row is at least in its home channel. */
    default void ensureHomeChannel() {
        if (getTenant() != null && getChannelIds().isEmpty()) {
            getChannelIds().add(getTenant().getId());
        }
    }
}
