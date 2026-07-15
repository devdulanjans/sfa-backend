package com.sfa.dto;

import com.sfa.entity.Expense;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ExpenseDto(
        UUID id,
        String category,
        BigDecimal amount,
        LocalDate expenseDate,
        String description,
        UUID recordedById,
        String recordedByName,
        Instant createdAt
) {
    public static ExpenseDto from(Expense e) {
        return new ExpenseDto(
                e.getId(), e.getCategory().name(), e.getAmount(), e.getExpenseDate(), e.getDescription(),
                e.getRecordedByUser().getId(), e.getRecordedByUser().getFullName(), e.getCreatedAt());
    }
}
