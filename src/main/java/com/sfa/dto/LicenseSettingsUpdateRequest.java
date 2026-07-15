package com.sfa.dto;

public record LicenseSettingsUpdateRequest(
        boolean sfaEnabled,
        boolean posEnabled,
        String clientName,
        String note
) {}
