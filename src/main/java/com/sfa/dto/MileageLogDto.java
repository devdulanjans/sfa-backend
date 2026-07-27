package com.sfa.dto;

import com.sfa.entity.MileageLog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MileageLogDto(
        UUID id,
        UUID userId,
        String userFullName,
        String userUsername,
        LocalDate logDate,
        BigDecimal startMileage,
        Instant startedAt,
        BigDecimal endMileage,
        Instant endedAt,
        BigDecimal distance
) {
    public static MileageLogDto from(MileageLog m) {
        BigDecimal distance = m.getEndMileage() != null
                ? m.getEndMileage().subtract(m.getStartMileage())
                : null;
        return new MileageLogDto(
                m.getId(),
                m.getUser().getId(),
                m.getUser().getFullName(),
                m.getUser().getUsername(),
                m.getLogDate(),
                m.getStartMileage(),
                m.getStartedAt(),
                m.getEndMileage(),
                m.getEndedAt(),
                distance
        );
    }
}
