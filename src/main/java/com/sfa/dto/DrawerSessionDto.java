package com.sfa.dto;

import com.sfa.entity.DrawerSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DrawerSessionDto(
        UUID       id,
        UUID       cashierId,
        String     cashierName,
        BigDecimal openingFloat,
        Instant    openedAt,
        Instant    closedAt,
        BigDecimal expectedCash,
        BigDecimal countedCash,
        BigDecimal variance,
        String     status,
        String     openingNotes,
        String     closingNotes
) {
    public static DrawerSessionDto from(DrawerSession s, BigDecimal liveExpectedCash) {
        return new DrawerSessionDto(
                s.getId(),
                s.getCashier().getId(),
                s.getCashier().getFullName(),
                s.getOpeningFloat(),
                s.getOpenedAt(),
                s.getClosedAt(),
                s.getStatus() == DrawerSession.Status.OPEN ? liveExpectedCash : s.getExpectedCash(),
                s.getCountedCash(),
                s.getVariance(),
                s.getStatus().name(),
                s.getOpeningNotes(),
                s.getClosingNotes()
        );
    }
}
