package com.sfa.service;

import com.sfa.dto.distributor.CreateDistributorRequest;
import com.sfa.dto.distributor.DistributorDto;
import com.sfa.entity.Distributor;
import com.sfa.entity.Tenant;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.DistributorRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DistributorService {

    private final DistributorRepository distributorRepository;
    private final TenantRepository      tenantRepository;
    private final UserRepository        userRepository;

    public Page<DistributorDto> list(String search, Pageable pageable) {
        boolean hasSearch = search != null && !search.isBlank();
        if (TenantContext.isUnscoped()) {
            return hasSearch
                    ? distributorRepository.search(search.trim(), pageable).map(DistributorDto::from)
                    : distributorRepository.findAll(pageable).map(DistributorDto::from);
        }
        // A distributor has no single owning channel to filter on via Hibernate's @Filter (it's
        // many-to-many) — scoped explicitly to the session's active channel instead. The
        // TenantFilterInterceptor has already refused the request by this point if a scoped
        // session hasn't picked a channel, so getTenantId() is guaranteed non-null here.
        UUID tenantId = TenantContext.getTenantId();
        return hasSearch
                ? distributorRepository.searchByTenantId(tenantId, search.trim(), pageable).map(DistributorDto::from)
                : distributorRepository.findByTenantId(tenantId, pageable).map(DistributorDto::from);
    }

    public DistributorDto getById(UUID id) {
        return DistributorDto.from(findOrThrow(id));
    }

    public List<DistributorDto> getByUser(UUID userId) {
        return distributorRepository.findByUserId(userId)
                .stream().map(DistributorDto::from).toList();
    }

    @Transactional
    public DistributorDto create(CreateDistributorRequest req) {
        if (distributorRepository.existsByCode(req.code())) {
            throw new BusinessException("Distributor code already exists: " + req.code());
        }
        if (req.tenantIds() == null || req.tenantIds().isEmpty()) {
            throw new BusinessException("Select at least one channel for this distributor.");
        }
        Distributor d = Distributor.builder()
                .code(req.code().toUpperCase())
                .name(req.name())
                .address(req.address())
                .phone(req.phone())
                .email(req.email())
                .build();
        d.getTenants().addAll(resolveTenants(req.tenantIds()));
        return DistributorDto.from(distributorRepository.save(d));
    }

    @Transactional
    public DistributorDto update(UUID id, CreateDistributorRequest req) {
        Distributor d = findOrThrow(id);
        if (!d.getCode().equals(req.code()) && distributorRepository.existsByCode(req.code())) {
            throw new BusinessException("Distributor code already exists: " + req.code());
        }
        d.setCode(req.code().toUpperCase());
        d.setName(req.name());
        d.setAddress(req.address());
        d.setPhone(req.phone());
        d.setEmail(req.email());

        // Channel assignments are editable (unlike a single-tenant entity's fixed-at-creation
        // channel) — full replace, same pattern as UserService.applyTenants. A non-SUPER_ADMIN
        // can never reach this far with an out-of-scope channel in the request anyway, since
        // findOrThrow above already refused a distributor outside their own active channel.
        if (req.tenantIds() != null && !req.tenantIds().isEmpty()) {
            d.getTenants().clear();
            d.getTenants().addAll(resolveTenants(req.tenantIds()));
        }

        return DistributorDto.from(distributorRepository.save(d));
    }

    @Transactional
    public DistributorDto toggleStatus(UUID id) {
        Distributor d = findOrThrow(id);
        d.setStatus(d.getStatus() == Distributor.DistributorStatus.ACTIVE
                ? Distributor.DistributorStatus.INACTIVE
                : Distributor.DistributorStatus.ACTIVE);
        return DistributorDto.from(distributorRepository.save(d));
    }

    @Transactional
    public void assignUser(UUID distributorId, UUID userId) {
        Distributor d = findOrThrow(distributorId);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (u.getDistributors().contains(d)) {
            throw new BusinessException("User already assigned to this distributor");
        }
        u.getDistributors().add(d);
        userRepository.save(u);
    }

    @Transactional
    public void unassignUser(UUID distributorId, UUID userId) {
        Distributor d = findOrThrow(distributorId);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        u.getDistributors().remove(d);
        userRepository.save(u);
    }

    private Set<Tenant> resolveTenants(List<UUID> tenantIds) {
        Set<Tenant> tenants = Set.copyOf(tenantRepository.findAllById(tenantIds));
        // SUPER_ADMIN creates/edits distributors (see DistributorController) and is always
        // unscoped, so there's no "must be within my own channels" membership check to apply
        // here — unlike TenantAccessService.resolveExplicitTenant, which exists for actors who
        // can be scoped. A non-existent id silently drops (findAllById), which is acceptable:
        // the caller only ever supplies ids from the channel picker it was just shown.
        return tenants;
    }

    private Distributor findOrThrow(UUID id) {
        Distributor d = distributorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Distributor", id));
        if (!TenantContext.isUnscoped()) {
            UUID tenantId = TenantContext.getTenantId();
            boolean visible = d.getTenants().stream().anyMatch(t -> t.getId().equals(tenantId));
            if (!visible) {
                // Same shape as TenantGuard.requireVisible — a channel that isn't among this
                // distributor's own channels must never see or edit it, even by guessing its id.
                throw new ResourceNotFoundException("Distributor", id);
            }
        }
        return d;
    }
}
