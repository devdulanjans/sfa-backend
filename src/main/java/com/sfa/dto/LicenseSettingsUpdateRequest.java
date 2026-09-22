package com.sfa.dto;

public record LicenseSettingsUpdateRequest(
        boolean sfaEnabled,
        boolean posEnabled,
        boolean financeEnabled,
        boolean multiTenantEnabled,
        String clientName,
        String note
) {}
