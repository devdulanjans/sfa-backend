package com.sfa.service;

import com.sfa.dto.JournalEntryDto;
import com.sfa.dto.JournalEntryLineDto;
import com.sfa.dto.PostJournalEntryRequest;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.JournalEntry;
import com.sfa.entity.JournalEntryLine;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.JournalEntryLineRepository;
import com.sfa.repository.JournalEntryRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The single write path into the ledger. Every automated posting (invoice issued,
 * invoice payment, vendor bill, vendor bill payment) and every manual entry goes
 * through postEntry(), so "every posting balances" only has to be proven once here.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class JournalEntryService {

    private final JournalEntryRepository journalEntryRepo;
    private final JournalEntryLineRepository journalEntryLineRepo;
    private final ChartOfAccountRepository accountRepo;
    private final EntityManager em;

    public record LinePosting(UUID accountId, BigDecimal debit, BigDecimal credit, String description) {}

    public JournalEntry postEntry(LocalDate entryDate, String description, List<LinePosting> lines,
                                   JournalEntry.SourceType sourceType, UUID sourceId, UUID createdBy) {
        if (lines == null || lines.size() < 2) {
            throw new BusinessException("A journal entry requires at least two lines");
        }

        JournalEntry entry = JournalEntry.builder()
                .entryNumber(generateEntryNumber())
                .entryDate(entryDate != null ? entryDate : LocalDate.now())
                .description(description)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .createdBy(createdBy)
                .build();
        journalEntryRepo.save(entry);

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<JournalEntryLine> entryLines = new ArrayList<>();
        int order = 0;

        for (LinePosting line : lines) {
            BigDecimal debit = line.debit() != null ? line.debit() : BigDecimal.ZERO;
            BigDecimal credit = line.credit() != null ? line.credit() : BigDecimal.ZERO;
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new BusinessException("Journal entry line amounts cannot be negative");
            }
            if (debit.signum() == 0 && credit.signum() == 0) {
                throw new BusinessException("Journal entry line must have a debit or credit amount");
            }
            ChartOfAccount account = accountRepo.findById(line.accountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chart of account", line.accountId()));
            if (!account.isActive()) {
                throw new BusinessException("Account '" + account.getAccountName() + "' is not active");
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            entryLines.add(JournalEntryLine.builder()
                    .journalEntry(entry)
                    .account(account)
                    .debitAmount(debit)
                    .creditAmount(credit)
                    .description(line.description())
                    .lineOrder(order++)
                    .build());
        }

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException("Journal entry does not balance: total debits (%s) != total credits (%s)"
                    .formatted(totalDebit, totalCredit));
        }

        journalEntryLineRepo.saveAll(entryLines);
        return entry;
    }

    public JournalEntry postManualEntry(PostJournalEntryRequest request, UUID createdBy) {
        List<LinePosting> lines = request.lines().stream()
                .map(l -> new LinePosting(l.accountId(), l.debit(), l.credit(), l.description()))
                .toList();
        return postEntry(request.entryDate(), request.description(), lines,
                JournalEntry.SourceType.MANUAL, null, createdBy);
    }

    /** Voids by posting a reversing entry (swapped debit/credit) rather than deleting — preserves the audit trail. */
    public JournalEntry voidEntry(UUID entryId, UUID voidedBy) {
        JournalEntry original = journalEntryRepo.findById(entryId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry", entryId));
        if (original.getStatus() == JournalEntry.JournalStatus.VOID) {
            throw new BusinessException("Journal entry is already void");
        }

        List<JournalEntryLine> originalLines = journalEntryLineRepo.findByJournalEntryIdOrderByLineOrder(entryId);
        List<LinePosting> reversedLines = originalLines.stream()
                .map(l -> new LinePosting(l.getAccount().getId(), l.getCreditAmount(), l.getDebitAmount(),
                        "Reversal of " + original.getEntryNumber()))
                .toList();
        postEntry(LocalDate.now(), "Reversal of " + original.getEntryNumber(), reversedLines,
                original.getSourceType(), original.getSourceId(), voidedBy);

        original.setStatus(JournalEntry.JournalStatus.VOID);
        return journalEntryRepo.save(original);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAccountBalance(UUID accountId) {
        return getAccountBalance(accountId, null);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAccountBalance(UUID accountId, LocalDate asOf) {
        ChartOfAccount account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Chart of account", accountId));
        BigDecimal netDebit = journalEntryLineRepo.netDebitBalance(accountId, JournalEntry.JournalStatus.POSTED, asOf);
        return isDebitNormal(account.getAccountType()) ? netDebit : netDebit.negate();
    }

    /** Sum of postings within [dateFrom, dateTo] only — for period reports (Profit & Loss), unlike
     *  {@link #getAccountBalance(UUID, LocalDate)} which is cumulative-to-date (for Balance Sheet). */
    @Transactional(readOnly = true)
    public BigDecimal getAccountBalanceInRange(UUID accountId, LocalDate dateFrom, LocalDate dateTo) {
        ChartOfAccount account = accountRepo.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Chart of account", accountId));
        BigDecimal netDebit = journalEntryLineRepo.netDebitInRange(accountId, JournalEntry.JournalStatus.POSTED, dateFrom, dateTo);
        return isDebitNormal(account.getAccountType()) ? netDebit : netDebit.negate();
    }

    @Transactional(readOnly = true)
    public List<JournalEntryLineDto> getAccountLedger(UUID accountId, LocalDate dateFrom, LocalDate dateTo) {
        return journalEntryLineRepo
                .findLedgerForAccount(accountId, JournalEntry.JournalStatus.POSTED, dateFrom, dateTo)
                .stream()
                .map(JournalEntryLineDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<JournalEntryDto> list(LocalDate dateFrom, LocalDate dateTo, JournalEntry.SourceType sourceType, Pageable pageable) {
        return journalEntryRepo.findFiltered(dateFrom, dateTo, sourceType, pageable)
                .map(je -> JournalEntryDto.from(je, getLinesFor(je.getId())));
    }

    @Transactional(readOnly = true)
    public JournalEntryDto get(UUID id) {
        JournalEntry je = journalEntryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry", id));
        return JournalEntryDto.from(je, getLinesFor(id));
    }

    private List<JournalEntryLineDto> getLinesFor(UUID entryId) {
        return journalEntryLineRepo.findByJournalEntryIdOrderByLineOrder(entryId).stream()
                .map(JournalEntryLineDto::from)
                .toList();
    }

    private boolean isDebitNormal(ChartOfAccount.AccountType type) {
        return type == ChartOfAccount.AccountType.ASSET || type == ChartOfAccount.AccountType.EXPENSE;
    }

    private String generateEntryNumber() {
        long seq = ((Number) em.createNativeQuery("SELECT NEXTVAL('journal_entry_number_seq')").getSingleResult()).longValue();
        return "JE-%06d".formatted(seq);
    }
}
