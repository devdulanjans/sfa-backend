package com.sfa.service;

import com.sfa.dto.ExpenseDto;
import com.sfa.entity.Expense;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.ExpenseRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ExpenseService {

    private final ExpenseRepository expenseRepo;
    private final UserRepository    userRepo;

    public ExpenseDto create(Expense.Category category, BigDecimal amount, LocalDate expenseDate,
                              String description, UUID recordedById) {
        validate(amount, expenseDate);
        User recordedBy = userRepo.findById(recordedById)
                .orElseThrow(() -> new ResourceNotFoundException("User", recordedById));

        Expense expense = Expense.builder()
                .category(category)
                .amount(amount)
                .expenseDate(expenseDate)
                .description(description)
                .recordedByUser(recordedBy)
                .build();
        return ExpenseDto.from(expenseRepo.save(expense));
    }

    public ExpenseDto update(UUID id, Expense.Category category, BigDecimal amount, LocalDate expenseDate, String description) {
        validate(amount, expenseDate);
        Expense expense = expenseRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense", id));

        expense.setCategory(category);
        expense.setAmount(amount);
        expense.setExpenseDate(expenseDate);
        expense.setDescription(description);
        return ExpenseDto.from(expenseRepo.save(expense));
    }

    public void delete(UUID id) {
        if (!expenseRepo.existsById(id)) {
            throw new ResourceNotFoundException("Expense", id);
        }
        expenseRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseDto> list(String categoryStr, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Expense.Category category = parseCategory(categoryStr);
        return expenseRepo.findFiltered(category, dateFrom, dateTo, pageable).map(ExpenseDto::from);
    }

    private void validate(BigDecimal amount, LocalDate expenseDate) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Amount must be greater than zero");
        }
        if (expenseDate == null) {
            throw new BusinessException("Expense date is required");
        }
    }

    private Expense.Category parseCategory(String categoryStr) {
        if (categoryStr == null || categoryStr.isBlank()) return null;
        try {
            return Expense.Category.valueOf(categoryStr);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid expense category: " + categoryStr);
        }
    }
}
