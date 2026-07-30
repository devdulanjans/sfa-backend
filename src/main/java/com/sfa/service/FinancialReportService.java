package com.sfa.service;

import com.sfa.dto.BalanceSheetDto;
import com.sfa.dto.FinanceProfitLossDto;
import com.sfa.dto.FinancialReportLineDto;
import com.sfa.entity.ChartOfAccount;
import com.sfa.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Both reports are pure aggregations over the chart of accounts / journal entry lines
 * already maintained by JournalEntryService — no separate reporting tables. Profit & Loss
 * sums postings within a date range; Balance Sheet sums cumulative balances as of a date,
 * plus a synthetic "Current Year Earnings" equity line (all-time net income to that date)
 * so Assets = Liabilities + Equity holds without a period-close process.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialReportService {

    private final ChartOfAccountRepository accountRepo;
    private final JournalEntryService journalEntryService;

    public FinanceProfitLossDto getProfitAndLoss(LocalDate dateFrom, LocalDate dateTo) {
        List<FinancialReportLineDto> revenueLines = linesForType(ChartOfAccount.AccountType.REVENUE, dateFrom, dateTo);
        List<FinancialReportLineDto> expenseLines = linesForType(ChartOfAccount.AccountType.EXPENSE, dateFrom, dateTo);

        BigDecimal totalRevenue = sum(revenueLines);
        BigDecimal totalExpenses = sum(expenseLines);

        return new FinanceProfitLossDto(
                dateFrom, dateTo, revenueLines, expenseLines,
                totalRevenue, totalExpenses, totalRevenue.subtract(totalExpenses));
    }

    public BalanceSheetDto getBalanceSheet(LocalDate asOfDate) {
        LocalDate asOf = asOfDate != null ? asOfDate : LocalDate.now();

        List<FinancialReportLineDto> assetLines = asOfLinesForType(ChartOfAccount.AccountType.ASSET, asOf);
        List<FinancialReportLineDto> liabilityLines = asOfLinesForType(ChartOfAccount.AccountType.LIABILITY, asOf);
        List<FinancialReportLineDto> equityLines = asOfLinesForType(ChartOfAccount.AccountType.EQUITY, asOf);

        BigDecimal revenueToDate = sum(asOfLinesForType(ChartOfAccount.AccountType.REVENUE, asOf));
        BigDecimal expensesToDate = sum(asOfLinesForType(ChartOfAccount.AccountType.EXPENSE, asOf));
        BigDecimal currentEarnings = revenueToDate.subtract(expensesToDate);

        List<FinancialReportLineDto> equityWithEarnings = new java.util.ArrayList<>(equityLines);
        equityWithEarnings.add(new FinancialReportLineDto(null, null, "Current Year Earnings", currentEarnings));

        BigDecimal totalAssets = sum(assetLines);
        BigDecimal totalLiabilities = sum(liabilityLines);
        BigDecimal totalEquity = sum(equityLines).add(currentEarnings);

        boolean balanced = totalAssets.setScale(2, java.math.RoundingMode.HALF_UP)
                .compareTo(totalLiabilities.add(totalEquity).setScale(2, java.math.RoundingMode.HALF_UP)) == 0;

        return new BalanceSheetDto(
                asOf, assetLines, liabilityLines, equityWithEarnings,
                totalAssets, totalLiabilities, totalEquity, balanced);
    }

    private List<FinancialReportLineDto> linesForType(ChartOfAccount.AccountType type, LocalDate dateFrom, LocalDate dateTo) {
        return accountRepo.findAllByOrderByAccountCodeAsc().stream()
                .filter(a -> a.getAccountType() == type)
                .map(a -> new FinancialReportLineDto(a.getId(), a.getAccountCode(), a.getAccountName(),
                        journalEntryService.getAccountBalanceInRange(a.getId(), dateFrom, dateTo)))
                .filter(line -> line.amount().compareTo(BigDecimal.ZERO) != 0)
                .toList();
    }

    private List<FinancialReportLineDto> asOfLinesForType(ChartOfAccount.AccountType type, LocalDate asOf) {
        return accountRepo.findAllByOrderByAccountCodeAsc().stream()
                .filter(a -> a.getAccountType() == type)
                .map(a -> new FinancialReportLineDto(a.getId(), a.getAccountCode(), a.getAccountName(),
                        journalEntryService.getAccountBalance(a.getId(), asOf)))
                .filter(line -> line.amount().compareTo(BigDecimal.ZERO) != 0)
                .toList();
    }

    private BigDecimal sum(List<FinancialReportLineDto> lines) {
        return lines.stream().map(FinancialReportLineDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
