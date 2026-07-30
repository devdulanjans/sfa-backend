package com.sfa.service;

import com.sfa.dto.BankAccountDto;
import com.sfa.dto.CreateBankAccountRequest;
import com.sfa.dto.JournalEntryLineDto;
import com.sfa.entity.BankAccount;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.JournalEntry;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.BankAccountRepository;
import com.sfa.repository.ChartOfAccountRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class BankAccountService {

    private final BankAccountRepository bankAccountRepo;
    private final ChartOfAccountRepository accountRepo;
    private final JournalEntryService journalEntryService;
    private final EntityManager em;

    public BankAccountDto create(CreateBankAccountRequest req, UUID createdBy) {
        if (req.accountName() == null || req.accountName().isBlank()) {
            throw new BusinessException("Bank account name is required");
        }
        BigDecimal opening = req.openingBalance() != null ? req.openingBalance() : BigDecimal.ZERO;
        if (opening.signum() < 0) {
            throw new BusinessException("Opening balance cannot be negative");
        }

        ChartOfAccount glAccount = accountRepo.save(ChartOfAccount.builder()
                .accountCode(generateGlAccountCode())
                .accountName(req.accountName())
                .accountType(ChartOfAccount.AccountType.ASSET)
                .build());

        BankAccount bankAccount = bankAccountRepo.save(BankAccount.builder()
                .accountName(req.accountName())
                .accountNumber(req.accountNumber())
                .bankName(req.bankName())
                .currency(req.currency() != null ? req.currency() : "LKR")
                .openingBalance(opening)
                .glAccount(glAccount)
                .build());

        if (opening.signum() > 0) {
            ChartOfAccount openingBalanceEquity = accountRepo.findByAccountCode("3900")
                    .orElseThrow(() -> new BusinessException("Opening Balance Equity system account is missing"));
            journalEntryService.postEntry(
                    LocalDate.now(),
                    "Opening balance for " + bankAccount.getAccountName(),
                    List.of(
                            new JournalEntryService.LinePosting(glAccount.getId(), opening, null, "Opening balance"),
                            new JournalEntryService.LinePosting(openingBalanceEquity.getId(), null, opening, "Opening balance")
                    ),
                    JournalEntry.SourceType.MANUAL,
                    bankAccount.getId(),
                    createdBy);
        }

        return toDto(bankAccount);
    }

    @Transactional(readOnly = true)
    public List<BankAccountDto> list() {
        return bankAccountRepo.findAllByOrderByAccountNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(UUID id) {
        BankAccount bankAccount = getOrThrow(id);
        return journalEntryService.getAccountBalance(bankAccount.getGlAccount().getId());
    }

    @Transactional(readOnly = true)
    public List<JournalEntryLineDto> getTransactions(UUID id, LocalDate dateFrom, LocalDate dateTo) {
        BankAccount bankAccount = getOrThrow(id);
        return journalEntryService.getAccountLedger(bankAccount.getGlAccount().getId(), dateFrom, dateTo);
    }

    private BankAccount getOrThrow(UUID id) {
        return bankAccountRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bank account", id));
    }

    private BankAccountDto toDto(BankAccount bankAccount) {
        return BankAccountDto.from(bankAccount, journalEntryService.getAccountBalance(bankAccount.getGlAccount().getId()));
    }

    private String generateGlAccountCode() {
        long seq = ((Number) em.createNativeQuery("SELECT NEXTVAL('bank_gl_account_code_seq')").getSingleResult()).longValue();
        return "11%02d".formatted(seq);
    }
}
