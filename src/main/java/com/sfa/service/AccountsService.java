package com.sfa.service;

import com.sfa.dto.LedgerEntryDto;
import com.sfa.dto.PosReportRowDto;
import com.sfa.dto.ProfitLossDto;
import com.sfa.dto.ProfitLossDto.ExpenseCategoryStat;
import com.sfa.entity.Expense;
import com.sfa.repository.ExpenseRepository;
import com.sfa.repository.PosSaleRepository;
import com.sfa.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountsService {

    private final PosSaleRepository     posSaleRepo;
    private final PosService            posService;
    private final ExpenseRepository     expenseRepo;
    private final StockMovementRepository movementRepo;

    public List<LedgerEntryDto> getLedger(LocalDate dateFrom, LocalDate dateTo) {
        Instant from = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<PosReportRowDto> sales = posService.getReportRowsForExport(null, null, null, from, to);
        List<Expense> expenses = expenseRepo.findAllBetween(dateFrom, dateTo);

        List<LedgerEntryDto> unordered = new ArrayList<>();
        for (PosReportRowDto s : sales) {
            unordered.add(new LedgerEntryDto(s.createdAt(), "INCOME", s.saleNumber(), s.customerName(),
                    BigDecimal.ZERO, s.total(), null));
        }
        for (Expense e : expenses) {
            Instant at = e.getExpenseDate().atStartOfDay(ZoneOffset.UTC).toInstant();
            unordered.add(new LedgerEntryDto(at, "EXPENSE", e.getCategory().name(),
                    e.getDescription() != null ? e.getDescription() : "—", e.getAmount(), BigDecimal.ZERO, null));
        }
        unordered.sort(Comparator.comparing(LedgerEntryDto::date));

        List<LedgerEntryDto> result = new ArrayList<>();
        BigDecimal balance = BigDecimal.ZERO;
        for (LedgerEntryDto entry : unordered) {
            balance = balance.add(entry.credit()).subtract(entry.debit());
            result.add(new LedgerEntryDto(entry.date(), entry.type(), entry.reference(), entry.description(),
                    entry.debit(), entry.credit(), balance));
        }
        return result;
    }

    public ProfitLossDto getProfitLoss(LocalDate dateFrom, LocalDate dateTo) {
        Instant from = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to   = dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal totalIncome = posSaleRepo.sumRevenueBetween(from, to);
        long incomeTransactionCount = posSaleRepo.countCompletedBetween(from, to);
        BigDecimal totalCogs = movementRepo.sumCogsBetween(from, to);
        BigDecimal grossProfit = totalIncome.subtract(totalCogs);
        BigDecimal totalExpenses = expenseRepo.sumBetween(dateFrom, dateTo);

        List<ExpenseCategoryStat> expensesByCategory = expenseRepo.sumByCategoryBetween(dateFrom, dateTo).stream()
                .map(r -> new ExpenseCategoryStat(categoryName(r[0]), toBigDecimal(r[1])))
                .toList();

        return new ProfitLossDto(dateFrom, dateTo, totalIncome, incomeTransactionCount,
                totalCogs, grossProfit, totalExpenses, expensesByCategory, grossProfit.subtract(totalExpenses));
    }

    private static String categoryName(Object v) {
        if (v instanceof Expense.Category c) return c.name();
        return String.valueOf(v);
    }

    private static BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }
}
