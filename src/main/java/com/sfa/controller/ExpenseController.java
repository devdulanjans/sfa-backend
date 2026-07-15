package com.sfa.controller;

import com.sfa.dto.ExpenseDto;
import com.sfa.entity.Expense;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.POS)
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public Page<ExpenseDto> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return expenseService.list(category, dateFrom, dateTo,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "expenseDate")));
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return Arrays.stream(Expense.Category.values()).map(Enum::name).toList();
    }

    @PostMapping
    public ExpenseDto create(@RequestBody Map<String, Object> body,
                              @AuthenticationPrincipal UserDetailsImpl principal) {
        return expenseService.create(
                parseCategory(body),
                new BigDecimal(body.get("amount").toString()),
                LocalDate.parse((String) body.get("expenseDate")),
                (String) body.get("description"),
                principal.getId());
    }

    @PutMapping("/{id}")
    public ExpenseDto update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        return expenseService.update(
                id,
                parseCategory(body),
                new BigDecimal(body.get("amount").toString()),
                LocalDate.parse((String) body.get("expenseDate")),
                (String) body.get("description"));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        expenseService.delete(id);
    }

    private Expense.Category parseCategory(Map<String, Object> body) {
        return Expense.Category.valueOf((String) body.get("category"));
    }
}
