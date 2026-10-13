package com.sfa.dto.distributor;

import com.sfa.dto.tenant.TenantDto;
import com.sfa.entity.Distributor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DistributorDto(
        UUID    id,
        String  code,
        String  name,
        String  address,
        String  phone,
        String  email,
        String  status,
        Instant createdAt,
        List<TenantDto> tenants
) {
    public static DistributorDto from(Distributor d) {
        List<TenantDto> tenants = d.getTenants() != null
                ? d.getTenants().stream().map(TenantDto::from).toList()
                : List.of();
        return new DistributorDto(
                d.getId(),
                d.getCode(),
                d.getName(),
                d.getAddress(),
                d.getPhone(),
                d.getEmail(),
                d.getStatus() != null ? d.getStatus().name() : null,
                d.getCreatedAt(),
                tenants
        );
    }
}
