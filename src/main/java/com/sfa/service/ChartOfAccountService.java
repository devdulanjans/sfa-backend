package com.sfa.service;

import com.sfa.dto.ChartOfAccountDto;
import com.sfa.dto.CreateChartOfAccountRequest;
import com.sfa.entity.ChartOfAccount;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.ChartOfAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ChartOfAccountService {

    private final ChartOfAccountRepository accountRepo;
    private final JournalEntryService journalEntryService;

    @Transactional(readOnly = true)
    public List<ChartOfAccountDto> list() {
        return accountRepo.findAllByOrderByAccountCodeAsc().stream()
                .map(a -> ChartOfAccountDto.from(a, journalEntryService.getAccountBalance(a.getId())))
                .toList();
    }

    public ChartOfAccountDto create(CreateChartOfAccountRequest req) {
        if (req.accountCode() == null || req.accountCode().isBlank()) {
            throw new BusinessException("Account code is required");
        }
        if (accountRepo.existsByAccountCode(req.accountCode())) {
            throw new BusinessException("Account code already exists: " + req.accountCode());
        }
        ChartOfAccount parent = null;
        if (req.parentAccountId() != null) {
            parent = accountRepo.findById(req.parentAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chart of account", req.parentAccountId()));
        }
        ChartOfAccount account = ChartOfAccount.builder()
                .accountCode(req.accountCode())
                .accountName(req.accountName())
                .accountType(parseType(req.accountType()))
                .parentAccount(parent)
                .build();
        return ChartOfAccountDto.from(accountRepo.save(account));
    }

    public ChartOfAccountDto update(UUID id, String accountName, boolean active) {
        ChartOfAccount account = accountRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chart of account", id));
        if (account.isSystemAccount() && !active) {
            throw new BusinessException("System accounts cannot be deactivated");
        }
        account.setAccountName(accountName);
        account.setActive(active);
        return ChartOfAccountDto.from(accountRepo.save(account));
    }

    public void delete(UUID id) {
        ChartOfAccount account = accountRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chart of account", id));
        if (account.isSystemAccount()) {
            throw new BusinessException("System accounts cannot be deleted");
        }
        accountRepo.delete(account);
    }

    private ChartOfAccount.AccountType parseType(String type) {
        try {
            return ChartOfAccount.AccountType.valueOf(type);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("Invalid account type: " + type);
        }
    }
}
