package com.sfa.dto;

import com.sfa.entity.LicenseSettings;

import java.time.Instant;
import java.util.UUID;

public record LicenseSettingsDto(
        UUID id,
        boolean sfaEnabled,
        boolean posEnabled,
        String clientName,
        String note,
        Instant updatedAt
) {
    public static LicenseSettingsDto from(LicenseSettings s) {
        return new LicenseSettingsDto(
                s.getId(),
                s.isSfaEnabled(),
                s.isPosEnabled(),
                s.getClientName(),
                s.getNote(),
                s.getUpdatedAt()
        );
    }
}
