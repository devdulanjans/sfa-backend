package com.sfa.service;

import com.sfa.entity.Tenant;
import com.sfa.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Which channels a CUSTOMER-role portal login can order from: every channel granted to the
 * login itself, plus the linked customer record's own channel — always included, so moving a
 * customer to another channel (or a portal account created without a channel picker) can
 * never lock the customer out of their own app.
 */
@Service
@RequiredArgsConstructor
public class CustomerChannelService {

    private final TenantRepository tenantRepository;

    /** Customer record's own channel first (the natural default), then the rest by name. */
    @Transactional(readOnly = true)
    public List<Tenant> channelsFor(UUID userId, UUID linkedCustomerId) {
        Map<UUID, Tenant> channels = new LinkedHashMap<>();
        if (linkedCustomerId != null) {
            // Home channel first, then every other channel the customer record is available in.
            tenantRepository.findByCustomerId(linkedCustomerId).ifPresent(t -> channels.put(t.getId(), t));
            tenantRepository.findChannelsOfCustomer(linkedCustomerId).forEach(t -> channels.putIfAbsent(t.getId(), t));
        }
        tenantRepository.findByUserId(userId).forEach(t -> channels.putIfAbsent(t.getId(), t));
        return List.copyOf(channels.values());
    }

    @Transactional(readOnly = true)
    public Set<UUID> channelIdsFor(UUID userId, UUID linkedCustomerId) {
        return channelsFor(userId, linkedCustomerId).stream().map(Tenant::getId).collect(Collectors.toSet());
    }
}
