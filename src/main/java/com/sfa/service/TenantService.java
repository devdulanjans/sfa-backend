package com.sfa.service;

import com.sfa.dto.tenant.CreateTenantRequest;
import com.sfa.dto.tenant.TenantDto;
import com.sfa.entity.Tenant;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository   userRepository;

    public Page<TenantDto> list(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return tenantRepository.search(search.trim(), pageable).map(TenantDto::from);
        }
        return tenantRepository.findAll(pageable).map(TenantDto::from);
    }

    public TenantDto getById(UUID id) {
        return TenantDto.from(findOrThrow(id));
    }

    public List<TenantDto> getByUser(UUID userId) {
        return tenantRepository.findByUserId(userId)
                .stream().map(TenantDto::from).toList();
    }

    @Transactional
    public TenantDto create(CreateTenantRequest req) {
        if (tenantRepository.existsByCode(req.code())) {
            throw new BusinessException("Channel code already exists: " + req.code());
        }
        Tenant t = Tenant.builder()
                .code(req.code().toUpperCase())
                .name(req.name())
                .build();
        return TenantDto.from(tenantRepository.save(t));
    }

    @Transactional
    public TenantDto update(UUID id, CreateTenantRequest req) {
        Tenant t = findOrThrow(id);
        if (!t.getCode().equals(req.code()) && tenantRepository.existsByCode(req.code())) {
            throw new BusinessException("Channel code already exists: " + req.code());
        }
        t.setCode(req.code().toUpperCase());
        t.setName(req.name());
        return TenantDto.from(tenantRepository.save(t));
    }

    @Transactional
    public TenantDto toggleStatus(UUID id) {
        Tenant t = findOrThrow(id);
        t.setStatus(t.getStatus() == Tenant.TenantStatus.ACTIVE
                ? Tenant.TenantStatus.INACTIVE
                : Tenant.TenantStatus.ACTIVE);
        return TenantDto.from(tenantRepository.save(t));
    }

    @Transactional
    public void assignUser(UUID tenantId, UUID userId, boolean asDefault) {
        Tenant t = findOrThrow(tenantId);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (u.getTenants().contains(t)) {
            throw new BusinessException("User already assigned to this channel");
        }
        u.getTenants().add(t);
        if (asDefault || u.getDefaultTenant() == null) {
            u.setDefaultTenant(t);
        }
        userRepository.save(u);
    }

    @Transactional
    public void unassignUser(UUID tenantId, UUID userId) {
        Tenant t = findOrThrow(tenantId);
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        u.getTenants().remove(t);
        if (t.equals(u.getDefaultTenant())) {
            u.setDefaultTenant(u.getTenants().stream().findFirst().orElse(null));
        }
        userRepository.save(u);
    }

    private Tenant findOrThrow(UUID id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant", id));
    }
}
