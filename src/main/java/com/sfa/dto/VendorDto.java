package com.sfa.dto;

import com.sfa.entity.Vendor;

import java.util.UUID;

public record VendorDto(
        UUID id,
        String vendorCode,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String taxNumber,
        int paymentTermsDays,
        boolean active
) {
    public static VendorDto from(Vendor v) {
        return new VendorDto(
                v.getId(), v.getVendorCode(), v.getName(), v.getContactPerson(), v.getPhone(), v.getEmail(),
                v.getAddress(), v.getTaxNumber(), v.getPaymentTermsDays(), v.isActive());
    }
}
