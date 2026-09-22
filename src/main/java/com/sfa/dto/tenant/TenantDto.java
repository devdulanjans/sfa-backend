package com.sfa.dto.tenant;

import com.sfa.entity.Tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantDto(
        UUID    id,
        String  code,
        String  name,
        String  status,
        Instant createdAt
) {
    public static TenantDto from(Tenant t) {
        return new TenantDto(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getStatus() != null ? t.getStatus().name() : null,
                t.getCreatedAt()
        );
    }
}
