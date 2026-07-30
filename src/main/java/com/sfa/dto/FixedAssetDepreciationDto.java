package com.sfa.dto;

import com.sfa.entity.FixedAssetDepreciation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record FixedAssetDepreciationDto(
        UUID id,
        UUID fixedAssetId,
        LocalDate periodDate,
        BigDecimal amount,
        UUID journalEntryId
) {
    public static FixedAssetDepreciationDto from(FixedAssetDepreciation d) {
        return new FixedAssetDepreciationDto(
                d.getId(), d.getFixedAsset().getId(), d.getPeriodDate(), d.getAmount(), d.getJournalEntry().getId());
    }
}
