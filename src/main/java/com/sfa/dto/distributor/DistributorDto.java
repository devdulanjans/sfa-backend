package com.sfa.dto.distributor;

import com.sfa.entity.Distributor;

import java.time.Instant;
import java.util.UUID;

public record DistributorDto(
        UUID    id,
        String  code,
        String  name,
        String  address,
        String  phone,
        String  email,
        String  status,
        Instant createdAt
) {
    public static DistributorDto from(Distributor d) {
        return new DistributorDto(
                d.getId(),
                d.getCode(),
                d.getName(),
                d.getAddress(),
                d.getPhone(),
                d.getEmail(),
                d.getStatus() != null ? d.getStatus().name() : null,
                d.getCreatedAt()
        );
    }
}
