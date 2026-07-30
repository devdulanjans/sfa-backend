package com.sfa.dto;

public record LicenseSettingsUpdateRequest(
        boolean sfaEnabled,
        boolean posEnabled,
        boolean financeEnabled,
        String clientName,
        String note
) {}
