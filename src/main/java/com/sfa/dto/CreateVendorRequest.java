package com.sfa.dto;

public record CreateVendorRequest(
        String vendorCode,
        String name,
        String contactPerson,
        String phone,
        String email,
        String address,
        String taxNumber,
        Integer paymentTermsDays
) {}
