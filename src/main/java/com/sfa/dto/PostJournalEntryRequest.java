package com.sfa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PostJournalEntryRequest(
        LocalDate entryDate,
        String description,
        List<Line> lines
) {
    public record Line(UUID accountId, BigDecimal debit, BigDecimal credit, String description) {}
}
