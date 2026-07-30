package com.sfa.service;

import com.sfa.dto.CreateVendorBillRequest;
import com.sfa.dto.VendorBillDto;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.JournalEntry;
import com.sfa.entity.Vendor;
import com.sfa.entity.VendorBill;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.VendorBillRepository;
import com.sfa.repository.VendorRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class VendorBillService {

    private final VendorBillRepository vendorBillRepo;
    private final VendorRepository vendorRepo;
    private final ChartOfAccountRepository accountRepo;
    private final JournalEntryService journalEntryService;
    private final EntityManager em;

    private static final String ACCOUNTS_PAYABLE_CODE = "2100";

    public VendorBillDto create(CreateVendorBillRequest req, UUID createdBy) {
        if (req.total() == null || req.total().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Bill total must be greater than zero");
        }
        Vendor vendor = vendorRepo.findById(req.vendorId())
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", req.vendorId()));
        ChartOfAccount expenseAccount = accountRepo.findById(req.expenseAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Chart of account", req.expenseAccountId()));
        ChartOfAccount accountsPayable = accountRepo.findByAccountCode(ACCOUNTS_PAYABLE_CODE)
                .orElseThrow(() -> new BusinessException("Accounts Payable system account is missing"));

        VendorBill bill = VendorBill.builder()
                .billNumber(generateBillNumber())
                .vendor(vendor)
                .billDate(req.billDate() != null ? req.billDate() : LocalDate.now())
                .dueDate(req.dueDate() != null ? req.dueDate() : LocalDate.now().plusDays(vendor.getPaymentTermsDays()))
                .total(req.total())
                .expenseAccount(expenseAccount)
                .description(req.description())
                .createdBy(createdBy)
                .build();
        VendorBill saved = vendorBillRepo.save(bill);

        JournalEntry entry = journalEntryService.postEntry(
                saved.getBillDate(),
                "Vendor bill " + saved.getBillNumber() + " — " + vendor.getName(),
                List.of(
                        new JournalEntryService.LinePosting(expenseAccount.getId(), req.total(), null, saved.getBillNumber()),
                        new JournalEntryService.LinePosting(accountsPayable.getId(), null, req.total(), saved.getBillNumber())
                ),
                JournalEntry.SourceType.VENDOR_BILL,
                saved.getId(),
                createdBy);

        saved.setJournalEntry(entry);
        return VendorBillDto.from(vendorBillRepo.save(saved));
    }

    @Transactional(readOnly = true)
    public Page<VendorBillDto> list(UUID vendorId, String statusStr, Pageable pageable) {
        VendorBill.BillStatus status = parseStatus(statusStr);
        return vendorBillRepo.findFiltered(vendorId, status, pageable).map(VendorBillDto::from);
    }

    @Transactional(readOnly = true)
    public VendorBillDto get(UUID id) {
        return VendorBillDto.from(getOrThrow(id));
    }

    VendorBill getOrThrow(UUID id) {
        return vendorBillRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor bill", id));
    }

    private VendorBill.BillStatus parseStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) return null;
        try {
            return VendorBill.BillStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid bill status: " + statusStr);
        }
    }

    private String generateBillNumber() {
        long seq = ((Number) em.createNativeQuery("SELECT NEXTVAL('vendor_bill_number_seq')").getSingleResult()).longValue();
        return "BILL-%06d".formatted(seq);
    }
}
