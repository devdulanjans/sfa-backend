package com.sfa.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record CompanyProfileUpdateRequest(
        @NotBlank String companyName,
        String registeredAddress,
        String operatingAddress,
        String phone,
        String email,
        String website,
        String taxId,
        String vatRegistrationNumber,
        BigDecimal vatRatePct,
        String bankName,
        String bankAccountName,
        String bankAccountNumber,
        String bankBranch,
        String bankSwiftCode
) {}
