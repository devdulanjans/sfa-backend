package com.sfa.dto;

import com.sfa.entity.CompanyProfile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CompanyProfileDto(
        UUID id,
        String companyName,
        String logoUrl,
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
        String bankSwiftCode,
        Instant updatedAt
) {
    public static CompanyProfileDto from(CompanyProfile p) {
        return new CompanyProfileDto(
                p.getId(),
                p.getCompanyName(),
                p.getLogoObjectPath() != null ? "/api/company-profile/logo" : null,
                p.getRegisteredAddress(),
                p.getOperatingAddress(),
                p.getPhone(),
                p.getEmail(),
                p.getWebsite(),
                p.getTaxId(),
                p.getVatRegistrationNumber(),
                p.getVatRatePct(),
                p.getBankName(),
                p.getBankAccountName(),
                p.getBankAccountNumber(),
                p.getBankBranch(),
                p.getBankSwiftCode(),
                p.getUpdatedAt()
        );
    }
}
