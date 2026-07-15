package com.sfa.dto.customer;

import com.sfa.entity.CustomerAddress;

import java.util.UUID;

public record CustomerAddressDto(UUID id, String label, String addressLine, boolean isPrimary) {

    public static CustomerAddressDto from(CustomerAddress a) {
        return new CustomerAddressDto(a.getId(), a.getLabel(), a.getAddressLine(), a.isPrimary());
    }
}
