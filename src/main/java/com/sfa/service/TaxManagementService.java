package com.sfa.service;

import com.sfa.dto.JournalEntryLineDto;
import com.sfa.dto.RecordTaxPaymentRequest;
import com.sfa.dto.TaxPayableSummaryDto;
import com.sfa.dto.TaxPaymentDto;
import com.sfa.entity.BankAccount;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.JournalEntry;
import com.sfa.entity.TaxPayment;
import com.sfa.exception.BusinessException;
import com.sfa.repository.BankAccountRepository;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.TaxPaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Manages the Tax Payable liability that already accrues automatically from every
 * invoice-issued posting (see InvoiceService.postInvoiceIssuedEntry) — this service
 * lets Finance see that balance and record what's actually been remitted to the
 * tax authority, via a real Dr Tax Payable / Cr Bank posting.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TaxManagementService {

    private static final String TAX_PAYABLE_CODE = "2200";

    private final ChartOfAccountRepository accountRepo;
    private final BankAccountRepository bankAccountRepo;
    private final TaxPaymentRepository taxPaymentRepo;
    private final JournalEntryService journalEntryService;

    @Transactional(readOnly = true)
    public TaxPayableSummaryDto getPayableSummary() {
        ChartOfAccount taxPayable = getTaxPayableAccount();
        return new TaxPayableSummaryDto(taxPayable.getId(), journalEntryService.getAccountBalance(taxPayable.getId()));
    }

    @Transactional(readOnly = true)
    public List<JournalEntryLineDto> getLedger(LocalDate dateFrom, LocalDate dateTo) {
        ChartOfAccount taxPayable = getTaxPayableAccount();
        return journalEntryService.getAccountLedger(taxPayable.getId(), dateFrom, dateTo);
    }

    @Transactional(readOnly = true)
    public List<TaxPaymentDto> listPayments() {
        return taxPaymentRepo.findAllByOrderByPaymentDateDesc().stream()
                .map(TaxPaymentDto::from)
                .toList();
    }

    public TaxPaymentDto recordPayment(RecordTaxPaymentRequest req, UUID createdBy) {
        BigDecimal amount = req.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Payment amount must be greater than zero");
        }
        ChartOfAccount taxPayable = getTaxPayableAccount();
        BigDecimal outstanding = journalEntryService.getAccountBalance(taxPayable.getId());
        if (amount.compareTo(outstanding) > 0) {
            throw new BusinessException("Payment amount exceeds outstanding Tax Payable balance of " + outstanding);
        }

        BankAccount bankAccount = bankAccountRepo.findById(req.bankAccountId())
                .orElseThrow(() -> new BusinessException("Bank account not found: " + req.bankAccountId()));
        LocalDate paymentDate = req.paymentDate() != null ? req.paymentDate() : LocalDate.now();

        JournalEntry entry = journalEntryService.postEntry(
                paymentDate,
                "Tax payment to authority",
                List.of(
                        new JournalEntryService.LinePosting(taxPayable.getId(), amount, null, "Tax payment"),
                        new JournalEntryService.LinePosting(bankAccount.getGlAccount().getId(), null, amount, "Tax payment")
                ),
                JournalEntry.SourceType.TAX_PAYMENT,
                null,
                createdBy);

        TaxPayment payment = taxPaymentRepo.save(TaxPayment.builder()
                .bankAccount(bankAccount)
                .amount(amount)
                .paymentDate(paymentDate)
                .referenceNumber(req.referenceNumber())
                .journalEntry(entry)
                .createdBy(createdBy)
                .build());

        return TaxPaymentDto.from(payment);
    }

    private ChartOfAccount getTaxPayableAccount() {
        return accountRepo.findByAccountCode(TAX_PAYABLE_CODE)
                .orElseThrow(() -> new BusinessException("Tax Payable system account is missing"));
    }
}
