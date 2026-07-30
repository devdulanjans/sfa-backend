package com.sfa.service;

import com.sfa.dto.CreateFixedAssetRequest;
import com.sfa.dto.FixedAssetDepreciationDto;
import com.sfa.dto.FixedAssetDto;
import com.sfa.entity.BankAccount;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.FixedAsset;
import com.sfa.entity.FixedAssetDepreciation;
import com.sfa.entity.JournalEntry;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.BankAccountRepository;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.FixedAssetDepreciationRepository;
import com.sfa.repository.FixedAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fixed assets sit under one shared "Fixed Assets" / "Accumulated Depreciation" control
 * account pair (like Accounts Receivable/Payable) — per-asset cost and accumulated
 * depreciation are tracked here as the subsidiary ledger, not one GL account per asset.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class FixedAssetService {

    private static final String FIXED_ASSETS_CODE          = "1500";
    private static final String ACCUMULATED_DEPRECIATION_CODE = "1590";
    private static final String DEPRECIATION_EXPENSE_CODE  = "5800";

    private final FixedAssetRepository fixedAssetRepo;
    private final FixedAssetDepreciationRepository depreciationRepo;
    private final ChartOfAccountRepository accountRepo;
    private final BankAccountRepository bankAccountRepo;
    private final JournalEntryService journalEntryService;

    public FixedAssetDto create(CreateFixedAssetRequest req, UUID createdBy) {
        if (req.assetCode() == null || req.assetCode().isBlank()) {
            throw new BusinessException("Asset code is required");
        }
        if (fixedAssetRepo.existsByAssetCode(req.assetCode())) {
            throw new BusinessException("Asset code already exists: " + req.assetCode());
        }
        if (req.purchaseCost() == null || req.purchaseCost().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Purchase cost must be greater than zero");
        }
        if (req.usefulLifeYears() == null || req.usefulLifeYears() <= 0) {
            throw new BusinessException("Useful life (years) must be greater than zero");
        }
        BigDecimal salvageValue = req.salvageValue() != null ? req.salvageValue() : BigDecimal.ZERO;
        if (salvageValue.compareTo(req.purchaseCost()) >= 0) {
            throw new BusinessException("Salvage value must be less than purchase cost");
        }

        BankAccount bankAccount = bankAccountRepo.findById(req.bankAccountId())
                .orElseThrow(() -> new BusinessException("Bank account not found: " + req.bankAccountId()));
        ChartOfAccount fixedAssetsAccount = getSystemAccount(FIXED_ASSETS_CODE);

        FixedAsset asset = fixedAssetRepo.save(FixedAsset.builder()
                .assetCode(req.assetCode())
                .name(req.name())
                .category(req.category())
                .purchaseDate(req.purchaseDate() != null ? req.purchaseDate() : LocalDate.now())
                .purchaseCost(req.purchaseCost())
                .salvageValue(salvageValue)
                .usefulLifeYears(req.usefulLifeYears())
                .bankAccount(bankAccount)
                .createdBy(createdBy)
                .build());

        JournalEntry entry = journalEntryService.postEntry(
                asset.getPurchaseDate(),
                "Fixed asset purchase — " + asset.getName(),
                List.of(
                        new JournalEntryService.LinePosting(fixedAssetsAccount.getId(), req.purchaseCost(), null, asset.getAssetCode()),
                        new JournalEntryService.LinePosting(bankAccount.getGlAccount().getId(), null, req.purchaseCost(), asset.getAssetCode())
                ),
                JournalEntry.SourceType.FIXED_ASSET_PURCHASE,
                asset.getId(),
                createdBy);

        asset.setJournalEntry(entry);
        return FixedAssetDto.from(fixedAssetRepo.save(asset));
    }

    @Transactional(readOnly = true)
    public List<FixedAssetDto> list() {
        return fixedAssetRepo.findAllByOrderByPurchaseDateDesc().stream()
                .map(FixedAssetDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FixedAssetDepreciationDto> listDepreciation(UUID assetId) {
        return depreciationRepo.findByFixedAssetIdOrderByPeriodDateDesc(assetId).stream()
                .map(FixedAssetDepreciationDto::from)
                .toList();
    }

    /** Idempotent: re-running for a period that's already posted for an asset skips it. */
    public List<FixedAssetDepreciationDto> runDepreciation(LocalDate periodDate, UUID createdBy) {
        LocalDate period = periodDate.withDayOfMonth(1);
        ChartOfAccount depreciationExpense = getSystemAccount(DEPRECIATION_EXPENSE_CODE);
        ChartOfAccount accumulatedDepreciation = getSystemAccount(ACCUMULATED_DEPRECIATION_CODE);

        return fixedAssetRepo.findByActiveTrueOrderByPurchaseDateAsc().stream()
                .filter(asset -> !depreciationRepo.existsByFixedAssetIdAndPeriodDate(asset.getId(), period))
                .filter(asset -> !asset.getPurchaseDate().isAfter(period))
                .map(asset -> postDepreciationForAsset(asset, period, depreciationExpense, accumulatedDepreciation, createdBy))
                .filter(dto -> dto != null)
                .toList();
    }

    private FixedAssetDepreciationDto postDepreciationForAsset(FixedAsset asset, LocalDate period,
            ChartOfAccount depreciationExpense, ChartOfAccount accumulatedDepreciation, UUID createdBy) {
        BigDecimal depreciableBase = asset.getPurchaseCost().subtract(asset.getSalvageValue());
        BigDecimal monthly = depreciableBase.divide(
                BigDecimal.valueOf(asset.getUsefulLifeYears()).multiply(BigDecimal.valueOf(12)),
                2, RoundingMode.HALF_UP);

        BigDecimal remaining = depreciableBase.subtract(asset.getAccumulatedDepreciation());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // fully depreciated already
        }
        BigDecimal amount = monthly.min(remaining);

        JournalEntry entry = journalEntryService.postEntry(
                period,
                "Depreciation — " + asset.getName() + " (" + period + ")",
                List.of(
                        new JournalEntryService.LinePosting(depreciationExpense.getId(), amount, null, asset.getAssetCode()),
                        new JournalEntryService.LinePosting(accumulatedDepreciation.getId(), null, amount, asset.getAssetCode())
                ),
                JournalEntry.SourceType.DEPRECIATION,
                asset.getId(),
                createdBy);

        asset.setAccumulatedDepreciation(asset.getAccumulatedDepreciation().add(amount));
        fixedAssetRepo.save(asset);

        FixedAssetDepreciation record = depreciationRepo.save(FixedAssetDepreciation.builder()
                .fixedAsset(asset)
                .periodDate(period)
                .amount(amount)
                .journalEntry(entry)
                .build());

        return FixedAssetDepreciationDto.from(record);
    }

    private ChartOfAccount getSystemAccount(String code) {
        return accountRepo.findByAccountCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Chart of account " + code));
    }
}
