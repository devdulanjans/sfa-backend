package com.sfa.dto;

import com.sfa.entity.JournalEntry;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record JournalEntryDto(
        UUID id,
        String entryNumber,
        LocalDate entryDate,
        String description,
        String sourceType,
        UUID sourceId,
        String status,
        List<JournalEntryLineDto> lines,
        Instant createdAt
) {
    public static JournalEntryDto from(JournalEntry je, List<JournalEntryLineDto> lines) {
        return new JournalEntryDto(
                je.getId(), je.getEntryNumber(), je.getEntryDate(), je.getDescription(),
                je.getSourceType().name(), je.getSourceId(), je.getStatus().name(), lines, je.getCreatedAt());
    }
}
